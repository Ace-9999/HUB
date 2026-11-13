package dev.personalhub.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.*
import dev.personalhub.focus.api.FocusSnapshot
import dev.personalhub.focus.api.Task
import kotlinx.coroutines.flow.first
import java.time.*
import java.util.concurrent.TimeUnit

/*
 * Task due-time reminders and an optional neutral daily habit nudge (HAB-02, HAB-06). Delivery uses WorkManager,
 * so alerts can be a little late under battery optimisation; task due times themselves are unaffected.
 * Notifications never name habits or show mood/private values.
 */
object Reminders {
    private const val CHANNEL = "reminders"
    private const val TASK_TAG = "task-reminder"

    /** Next not-yet-completed occurrence of [task] at its due time, strictly after [now]. */
    fun nextFire(task: Task, snap: FocusSnapshot, now: LocalDateTime): LocalDateTime? {
        val due = task.dueTime ?: return null
        for (offset in 0L..8L) {
            val day = now.toLocalDate().plusDays(offset)
            if (!task.occurs(day)) continue
            val key = task.occurrenceKey(day)
            if (snap.occurrences.any { it.taskId == task.id && it.day == key && it.completed }) { if (key != day) return null else continue }
            val at = day.atTime(due)
            if (at.isAfter(now)) return at
        }
        return null
    }

    suspend fun reschedule(app: PersonalHubApplication) {
        val wm = WorkManager.getInstance(app)
        wm.cancelAllWorkByTag(TASK_TAG)
        if (!app.settings.taskReminders.first()) return
        val snap = app.focus.snapshots.first()
        val now = LocalDateTime.now(app.time.zone)
        snap.tasks.forEach { t ->
            val at = nextFire(t, snap, now) ?: return@forEach
            val delay = Duration.between(now, at).toMillis().coerceAtLeast(0)
            wm.enqueueUniqueWork("rem-${t.id}", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<TaskReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS).addTag(TASK_TAG)
                .setInputData(workDataOf("task" to t.id, "day" to at.toLocalDate().toString())).build())
        }
    }

    suspend fun scheduleNudge(app: PersonalHubApplication) {
        val wm = WorkManager.getInstance(app)
        if (!app.settings.habitNudge.first()) { wm.cancelUniqueWork("habit-nudge"); return }
        val time = LocalTime.parse(app.settings.habitNudgeTime.first())
        val now = LocalDateTime.now(app.time.zone)
        var next = now.toLocalDate().atTime(time); if (!next.isAfter(now)) next = next.plusDays(1)
        wm.enqueueUniquePeriodicWork("habit-nudge", ExistingPeriodicWorkPolicy.UPDATE, PeriodicWorkRequestBuilder<HabitNudgeWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES).build())
    }

    fun notify(context: Context, id: Int, title: String, body: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT))
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val n = (if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(context, CHANNEL) else @Suppress("DEPRECATION") android.app.Notification.Builder(context))
            .setSmallIcon(R.drawable.ic_launcher_monochrome).setContentTitle(title).setContentText(body).setContentIntent(open).setAutoCancel(true).build()
        nm.notify(id, n)
    }
}

class TaskReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as PersonalHubApplication
        val id = inputData.getString("task") ?: return Result.success()
        val day = inputData.getString("day")?.let(LocalDate::parse) ?: return Result.success()
        val snap = app.focus.snapshots.first()
        val task = snap.tasks.firstOrNull { it.id == id }
        val done = task != null && snap.occurrences.any { it.taskId == id && it.day == task.occurrenceKey(day) && it.completed }
        if (task != null && !done && app.settings.taskReminders.first()) Reminders.notify(app, id.hashCode(), task.title, "Due ${task.dueTime}")
        Reminders.reschedule(app)
        return Result.success()
    }
}

class HabitNudgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as PersonalHubApplication
        val snap = app.focus.snapshots.first()
        val today = app.time.today()
        val missing = snap.habits.count { h -> snap.habitEntries.none { it.habitId == h.id && it.day == today } }
        if (missing > 0) Reminders.notify(app, 7001, "Daily check-in", "$missing habit${if (missing > 1) "s" else ""} not logged today.")
        return Result.success()
    }
}
