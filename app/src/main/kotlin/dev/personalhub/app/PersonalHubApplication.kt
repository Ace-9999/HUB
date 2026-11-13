package dev.personalhub.app

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.personalhub.college.data.RoomCollegeRepository
import dev.personalhub.core.AppTime
import dev.personalhub.feature.college.SessionHooks
import dev.personalhub.focus.data.RoomFocusRepository
import dev.personalhub.vtop.VtopSyncView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.ZoneId

private val Context.preferences by preferencesDataStore("settings")

// Application composition owns repositories; feature modules consume public APIs only.
class PersonalHubApplication : Application(), dev.personalhub.monitoring.UsageHost {
    val settings by lazy { SettingsStore(this) }
    val time by lazy { AppTime(zone = ZoneId.systemDefault()) }
    val focus by lazy { RoomFocusRepository.open(this, time) }
    val college by lazy { RoomCollegeRepository.open(this) }
    val usage by lazy { dev.personalhub.usage.data.RoomUsageRepository.open(this) }
    val googleTasks by lazy { GoogleTasksHost(this) }
    val backups by lazy { dev.personalhub.app.backup.BackupManager(this) }
    val training by lazy { dev.personalhub.training.data.RoomTrainingRepository.open(this) }
    /** Rest-over alert at the countdown deadline (WorkManager; may be a few seconds late, disclosed in UI). */
    val restAlerts = object : dev.personalhub.feature.training.RestAlerts {
        override fun schedule(at: java.time.Instant) {
            val delay = java.time.Duration.between(java.time.Instant.now(), at).toMillis().coerceAtLeast(0)
            androidx.work.WorkManager.getInstance(this@PersonalHubApplication).enqueueUniqueWork("rest-alert", androidx.work.ExistingWorkPolicy.REPLACE,
                androidx.work.OneTimeWorkRequestBuilder<RestAlertWorker>().setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS).build())
        }
        override fun cancel() { androidx.work.WorkManager.getInstance(this@PersonalHubApplication).cancelUniqueWork("rest-alert") }
    }
    private val mainScope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    override val usageEngine by lazy { dev.personalhub.monitoring.UsageEngine(usage, time.zone, mainScope) }
    override fun showBlock(reason: dev.personalhub.usage.api.BlockReason, ctx: dev.personalhub.usage.api.UsageContext) {
        startActivity(BlockedActivity.intent(this, reason, ctx, usageEngine))
    }
    /** Outlives activities so an acknowledged sync save is not cancelled by closing the portal screen. */
    val scope = CoroutineScope(SupervisorJob())
    override fun onCreate() {
        super.onCreate()
        SessionHooks.clear = { VtopSyncView.clearSession() }
        // Debug builds record layout id names (no text) for on-device classifier calibration.
        if (BuildConfig.DEBUG) dev.personalhub.monitoring.Calibration.enabled = true
        // Local task edits schedule a debounced Google Tasks sync when connected.
        focus.onTasksChanged = { mainScope.launch { if (googleTasks.enabled()) googleTasks.syncSoon(this@PersonalHubApplication); Reminders.reschedule(this@PersonalHubApplication) } }
        mainScope.launch { Reminders.reschedule(this@PersonalHubApplication); Reminders.scheduleNudge(this@PersonalHubApplication) }
        mainScope.launch { backups.recover(); backups.schedule() }
        mainScope.launch { if (googleTasks.enabled()) { googleTasks.schedule(this@PersonalHubApplication); googleTasks.syncSoon(this@PersonalHubApplication, 1) } }
    }
}
class SettingsStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val googleKey = androidx.datastore.preferences.core.booleanPreferencesKey("googleTasks")
    val googleTasks = context.preferences.data.map { it[googleKey] ?: false }
    suspend fun googleTasks(on: Boolean) { context.preferences.edit { it[googleKey] = on } }
    private val lockKey = androidx.datastore.preferences.core.booleanPreferencesKey("focusLock")
    private val remindKey = androidx.datastore.preferences.core.booleanPreferencesKey("taskReminders")
    private val nudgeKey = androidx.datastore.preferences.core.booleanPreferencesKey("habitNudge")
    private val nudgeTimeKey = stringPreferencesKey("habitNudgeTime")
    val focusLock = context.preferences.data.map { it[lockKey] ?: false }
    val taskReminders = context.preferences.data.map { it[remindKey] ?: false }
    val habitNudge = context.preferences.data.map { it[nudgeKey] ?: false }
    val habitNudgeTime = context.preferences.data.map { it[nudgeTimeKey] ?: "21:00" }
    suspend fun focusLock(on: Boolean) { context.preferences.edit { it[lockKey] = on } }
    suspend fun taskReminders(on: Boolean) { context.preferences.edit { it[remindKey] = on } }
    suspend fun habitNudge(on: Boolean, time: String? = null) { context.preferences.edit { it[nudgeKey] = on; time?.let { t -> it[nudgeTimeKey] = t } } }
    val theme = context.preferences.data.map { it[themeKey] ?: DEFAULT_THEME }
    suspend fun theme(value: String) {
        require(value in setOf("system", "dark", "light"))
        context.preferences.edit { it[themeKey] = value }
    }
    companion object { const val DEFAULT_THEME = "light" }
}
