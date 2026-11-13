package dev.personalhub.app.backup

import android.content.Context
import android.net.Uri
import androidx.work.*
import dev.personalhub.app.PersonalHubApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/*
 * Full JSON backups (I13). Internal snapshots: write temp -> re-read and decode (validate) -> atomic rename -> keep 7.
 * The last good snapshot is never deleted before a new verified one exists. Restore stages the file, writes a journal,
 * takes a pre-restore snapshot, replaces each domain, then clears the journal; an interrupted restore is re-applied
 * from the staged file at next start (replace-all is idempotent), so the end state is complete old or complete new.
 */
class BackupManager(private val app: PersonalHubApplication) {
    private val dir get() = File(app.filesDir, "backups").apply { mkdirs() }
    private val journal get() = File(dir, "restore.journal")
    private val staged get() = File(dir, "restore.staged.json")
    private val lock = Mutex()
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(java.time.ZoneOffset.UTC)

    data class Snapshot(val file: File, val createdAt: Instant, val bytes: Long)

    suspend fun current(): FullBackup = FullBackup(Instant.now(), app.time.zone, app.focus.exportAll(), app.training.exportAll(), app.usage.exportAll(),
        app.college.snapshots.first(), AppSettingsBackup(app.settings.theme.first(), app.settings.focusLock.first(), app.settings.taskReminders.first(),
            app.settings.habitNudge.first(), app.settings.habitNudgeTime.first()))

    fun snapshots(): List<Snapshot> = dir.listFiles { f -> f.name.startsWith("personalhub-") && f.name.endsWith(".json") }.orEmpty()
        .map { Snapshot(it, Instant.ofEpochMilli(it.lastModified()), it.length()) }.sortedByDescending { it.createdAt }

    /** Writes and verifies an internal snapshot; returns it. Keeps the newest [keep]. */
    suspend fun backupNow(keep: Int = 7): Snapshot = lock.withLock {
        val text = BackupCodec.encode(current()).toString()
        withContext(Dispatchers.IO) {
            val tmp = File(dir, "tmp-${System.nanoTime()}.json")
            tmp.writeText(text)
            BackupCodec.decode(tmp.readText()) // verify the completed file before it replaces anything
            val target = File(dir, "personalhub-${stamp.format(Instant.now())}.json")
            check(tmp.renameTo(target)) { "Could not finalise backup." }
            snapshots().drop(keep).forEach { it.file.delete() }
            Snapshot(target, Instant.ofEpochMilli(target.lastModified()), target.length())
        }
    }

    /** Owner-chosen destination (system file picker). The app never uploads or shares it. */
    suspend fun exportTo(uri: Uri) = lock.withLock {
        val text = BackupCodec.encode(current()).toString(2)
        withContext(Dispatchers.IO) { app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) } }
    }

    /** Reads and fully validates a file for the preview; nothing is changed. */
    suspend fun read(uri: Uri): FullBackup = withContext(Dispatchers.IO) {
        val bytes = app.contentResolver.openInputStream(uri)!!.use { input ->
            val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(64 * 1024); var total = 0L
            while (true) { val n = input.read(buf); if (n < 0) break; total += n; if (total > MAX_BACKUP_BYTES) throw BackupFormatException("Backup file is too large."); out.write(buf, 0, n) }
            out.toByteArray()
        }
        BackupCodec.decode(String(bytes))
    }
    suspend fun read(file: File): FullBackup = withContext(Dispatchers.IO) { BackupCodec.decode(file.readText()) }

    /** Replaces all domains with [b] after a pre-restore snapshot. */
    suspend fun restore(b: FullBackup) {
        backupNow(keep = 8) // pre-restore safety copy
        lock.withLock {
            withContext(Dispatchers.IO) { staged.writeText(BackupCodec.encode(b).toString()); journal.writeText(Instant.now().toString()) }
            apply(b)
            withContext(Dispatchers.IO) { journal.delete(); staged.delete() }
        }
    }

    /** Re-applies an interrupted restore at start-up. */
    suspend fun recover() {
        if (!journal.exists()) return
        lock.withLock {
            val b = runCatching { BackupCodec.decode(staged.readText()) }.getOrNull()
            if (b != null) apply(b)
            journal.delete(); staged.delete()
        }
    }

    private suspend fun apply(b: FullBackup) {
        app.focus.replaceAll(b.focus)
        app.training.replaceAll(b.training)
        app.usage.replaceAll(b.usage)
        app.college.replaceAll(b.college)
        b.settings.let { s -> app.settings.theme(s.theme); app.settings.focusLock(s.focusLock); app.settings.taskReminders(s.taskReminders); app.settings.habitNudge(s.habitNudge, s.habitNudgeTime) }
    }

    fun schedule() {
        WorkManager.getInstance(app).enqueueUniquePeriodicWork("daily-backup", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS).setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build()).build())
    }
}

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching { (applicationContext as PersonalHubApplication).backups.backupNow() }
        .fold({ Result.success() }, { if (runAttemptCount < 3) Result.retry() else Result.failure() })
}
