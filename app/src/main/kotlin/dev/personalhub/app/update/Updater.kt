package dev.personalhub.app.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.*
import dev.personalhub.app.BuildConfig
import dev.personalhub.app.Channels
import dev.personalhub.app.PersonalHubApplication
import dev.personalhub.app.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit

/** A published GitHub release that carries an APK. */
data class Release(val tag: String, val version: String, val apkUrl: String, val apkBytes: Long, val notes: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val at: Instant) : UpdateState
    data class Downloading(val version: String, val percent: Int) : UpdateState
    /** Handed to the system installer; on Android 14+ it installs once HUB is no longer in the foreground. */
    data class Scheduled(val version: String, val whenIdle: Boolean) : UpdateState
    data class NeedsApproval(val version: String) : UpdateState
    data class Failed(val reason: String) : UpdateState
}

/**
 * Self-update from the project's public GitHub releases (v1.1 UPD-01..05) through Android's own PackageInstaller —
 * the same path app stores use, so app data, granted permissions, accessibility/overlay access and Google sign-in survive.
 *
 * Safety: only an APK for this exact package, with a higher versionCode and the SAME signing certificate as the installed app,
 * is ever installed. Nothing is uploaded; the only request is an anonymous read of the public releases list and the APK.
 * After the first approved update HUB is the installer of record, so later updates need no taps (Android 12+), and on
 * Android 14+ they wait until HUB isn't in use.
 */
class Updater(private val app: PersonalHubApplication) {
    companion object {
        const val REPO = "Ace-9999/HUB"
        private const val API = "https://api.github.com/repos/$REPO/releases/latest"
        const val ACTION_STATUS = "dev.personalhub.UPDATE_STATUS"
        private const val MAX_APK = 200L * 1024 * 1024

        /** Numeric dotted-version comparison ("1.10" > "1.9"; "v" prefix and suffixes ignored). */
        fun newer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V").split('.', '-', '+').map { p -> p.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            val a = parts(candidate); val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) { val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }; if (x != y) return x > y }
            return false
        }

        fun parseRelease(json: String): Release? {
            val o = JSONObject(json)
            if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
            val tag = o.getString("tag_name")
            val assets = o.optJSONArray("assets") ?: return null
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.getString("name").endsWith(".apk", ignoreCase = true) } ?: return null
            val url = apk.getString("browser_download_url")
            if (!url.startsWith("https://github.com/$REPO/releases/download/")) return null
            return Release(tag, tag.removePrefix("v").removePrefix("V"), url, apk.optLong("size"), o.optString("body"))
        }
    }

    private val lock = Mutex()
    private val mutable = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = mutable
    private val dir get() = File(app.filesDir, "updates").apply { mkdirs() }

    fun canInstall() = app.packageManager.canRequestPackageInstalls()

    fun schedule() {
        WorkManager.getInstance(app).enqueueUniquePeriodicWork("update-check", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresStorageNotLow(true).build()).build())
    }

    /** Checks, downloads, verifies and hands a newer release to the installer. [now] = install immediately (user tapped). */
    suspend fun run(now: Boolean) = lock.withLock {
        mutable.value = UpdateState.Checking
        runCatching {
            val release = withContext(Dispatchers.IO) { fetch(API, "application/vnd.github+json") }.let(::parseRelease)
            if (release == null || !newer(release.version, BuildConfig.VERSION_NAME)) { mutable.value = UpdateState.UpToDate(Instant.now()); cleanup(null); return@runCatching }
            val apk = download(release)
            verify(apk)
            install(apk, release.version, waitForIdle = !now)
        }.onFailure { mutable.value = UpdateState.Failed(it.message ?: "Update check failed.") }
    }

    private fun fetch(url: String, accept: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("Accept", accept); setRequestProperty("User-Agent", "HUB-Android-Updater")
        }
        try {
            if (c.responseCode == 404) throw IllegalStateException("No public release found.")
            if (c.responseCode !in 200..299) throw IllegalStateException("GitHub returned ${c.responseCode}.")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    private suspend fun download(r: Release): File = withContext(Dispatchers.IO) {
        val target = File(dir, "hub-${r.version}.apk")
        if (target.exists() && target.length() == r.apkBytes) return@withContext target
        cleanup(null)
        val tmp = File(dir, "download.tmp")
        val c = (URL(r.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 60_000; instanceFollowRedirects = true; setRequestProperty("User-Agent", "HUB-Android-Updater")
        }
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("Download failed (${c.responseCode}).")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: r.apkBytes
            if (total > MAX_APK) throw IllegalStateException("Update file is unexpectedly large.")
            c.inputStream.use { input -> tmp.outputStream().use { out ->
                val buf = ByteArray(64 * 1024); var done = 0L; var last = -1
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    done += n; if (done > MAX_APK) throw IllegalStateException("Update file is unexpectedly large.")
                    out.write(buf, 0, n)
                    val pct = if (total > 0) (done * 100 / total).toInt() else 0
                    if (pct != last) { last = pct; mutable.value = UpdateState.Downloading(r.version, pct) }
                } } }
        } finally { c.disconnect() }
        check(tmp.renameTo(target)) { "Couldn't save the update." }
        target
    }

    private fun certs(signing: android.content.pm.SigningInfo?): Set<String> {
        val signers = signing?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory } ?: return emptySet()
        return signers.map { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    /** Refuses anything that isn't a newer build of this app signed with the same key. */
    private fun verify(apk: File) {
        val pm = app.packageManager
        val archive = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: throw IllegalStateException("The downloaded file isn't a valid app.")
        val installed = pm.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        if (archive.packageName != app.packageName) { apk.delete(); throw IllegalStateException("The update is for a different app.") }
        if (archive.longVersionCode <= installed.longVersionCode) { apk.delete(); throw IllegalStateException("The update isn't newer than this version.") }
        val mine = certs(installed.signingInfo); val theirs = certs(archive.signingInfo)
        if (mine.isEmpty() || theirs.intersect(mine).isEmpty()) { apk.delete(); throw IllegalStateException("The update is signed with a different key, so it was not installed.") }
    }

    private suspend fun install(apk: File, version: String, waitForIdle: Boolean) = withContext(Dispatchers.IO) {
        val installer = app.packageManager.packageInstaller
        // Abandon any earlier unfinished session of ours so only one update is pending.
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            if (Build.VERSION.SDK_INT >= 33) setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("hub.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; session.fsync(out) }
            val status = PendingIntent.getBroadcast(app, id, Intent(ACTION_STATUS).setPackage(app.packageName).putExtra("version", version),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            if (waitForIdle && Build.VERSION.SDK_INT >= 34) {
                val constraints = PackageInstaller.InstallConstraints.Builder().setAppNotForegroundRequired().build()
                installer.commitSessionAfterInstallConstraintsAreMet(id, status.intentSender, constraints, TimeUnit.DAYS.toMillis(7))
                mutable.value = UpdateState.Scheduled(version, whenIdle = true)
            } else {
                session.commit(status.intentSender)
                mutable.value = UpdateState.Scheduled(version, whenIdle = false)
            }
        }
    }

    internal fun onStatus(status: Int, version: String?, message: String?, confirm: Intent?) {
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                mutable.value = UpdateState.NeedsApproval(version ?: "")
                confirm?.let { c ->
                    val open = PendingIntent.getActivity(app, 7402, c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    Reminders.notifyIntent(app, 7401, "HUB ${version ?: ""} is ready", "Tap to approve the update. Your data and permissions are kept.", Channels.UPDATES, open)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // the process is replaced; UpdatedReceiver reports it
            else -> mutable.value = UpdateState.Failed(message ?: "The system installer rejected the update.")
        }
    }

    /** Deletes downloaded APKs except [keep]. */
    fun cleanup(keep: File?) { dir.listFiles()?.filter { it != keep }?.forEach { it.delete() } }
}

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as PersonalHubApplication
        if (app.settings.autoUpdate.first()) app.updates.run(now = false)
        return Result.success()
    }
}

/** Receives PackageInstaller session results. Not exported: only our own PendingIntent can reach it. */
class UpdateStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as PersonalHubApplication
        @Suppress("DEPRECATION")
        val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT)
        app.updates.onStatus(intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE), intent.getStringExtra("version"),
            intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE), confirm)
    }
}

/** Runs once in the new version after any update: removes the downloaded APK, re-arms schedules, says what changed. */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as PersonalHubApplication
        app.updates.cleanup(null)
        app.updates.schedule()
        Reminders.notify(app, 7403, "HUB updated to ${BuildConfig.VERSION_NAME}", "Everything you had is still here.", Channels.UPDATES)
    }
}
