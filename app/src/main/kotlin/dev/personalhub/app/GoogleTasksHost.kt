package dev.personalhub.app

import android.content.Context
import androidx.work.*
import dev.personalhub.focus.api.GoogleSyncState
import dev.personalhub.googletasks.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Owns Google Tasks scheduling and status. One sync at a time; failures never touch local tasks. */
class GoogleTasksHost(private val app: PersonalHubApplication) {
    private val mutable = MutableStateFlow<GoogleSyncState>(GoogleSyncState.Off)
    val state: StateFlow<GoogleSyncState> = mutable.asStateFlow()
    private val lock = Mutex()

    suspend fun enabled() = app.settings.googleTasks.first()

    /** Runs one pass. [token] comes from an interactive consent; otherwise authorization is attempted silently. */
    suspend fun sync(token: String? = null): GoogleSyncState = lock.withLock {
        if (!enabled()) return GoogleSyncState.Off.also { mutable.value = it }
        mutable.value = GoogleSyncState.Syncing
        val result = runCatching {
            val access = token ?: when (val a = GoogleAuth.authorize(app)) {
                is Auth.Token -> a.value
                is Auth.NeedsConsent -> return@runCatching GoogleSyncState.NeedsConsent
            }
            val (pulled, pushed) = GoogleTasksSync(app.focus, GoogleTasksClient(access)).run(Instant.now())
            GoogleSyncState.Synced(Instant.now(), pulled, pushed)
        }.getOrElse { e ->
            GoogleSyncState.Failed(when ((e as? ApiException)?.code) {
                401, 403 -> "Google refused access (check the Tasks API is enabled and your account is a test user)."
                null -> if (e is java.io.IOException) "No connection. Will retry." else "Sign-in unavailable: ${e.javaClass.simpleName}"
                else -> "Google Tasks error ${(e as ApiException).code}. Will retry."
            })
        }
        mutable.value = result
        result
    }

    fun schedule(context: Context) {
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("gtasks-periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<GoogleTasksWorker>(15, TimeUnit.MINUTES).setConstraints(net).build())
    }
    /** Debounced sync after local edits or app open. */
    fun syncSoon(context: Context, delaySeconds: Long = 5) {
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(context).enqueueUniqueWork("gtasks-now", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<GoogleTasksWorker>().setInitialDelay(delaySeconds, TimeUnit.SECONDS).setConstraints(net).build())
    }
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("gtasks-periodic"); WorkManager.getInstance(context).cancelUniqueWork("gtasks-now")
        mutable.value = GoogleSyncState.Off
    }
}

class GoogleTasksWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val host = (applicationContext as PersonalHubApplication).googleTasks
        return when (host.sync()) { is GoogleSyncState.Failed -> Result.retry(); else -> Result.success() }
    }
}
