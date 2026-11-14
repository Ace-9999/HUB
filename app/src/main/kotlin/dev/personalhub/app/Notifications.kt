package dev.personalhub.app

import android.content.Context
import androidx.work.*
import dev.personalhub.college.api.CollegeImport
import dev.personalhub.college.api.CollegeSnapshot
import dev.personalhub.college.api.newMarks
import dev.personalhub.feature.college.nextClassAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** One entry in the in-app bell inbox. [target] names where tapping it goes (e.g. "marks"). */
data class InboxItem(val id: String, val at: Instant, val title: String, val body: String, val target: String?, val read: Boolean)

/**
 * The bell inbox (v1.1 NOT-02): a small local JSON file, newest first, capped at 100 items. Entries are written whether or not
 * phone notifications are on, so nothing is missed. Never synced or uploaded.
 */
class Inbox(private val context: Context) {
    private val file get() = File(context.filesDir, "inbox.json")
    private val lock = Mutex()
    private val items = MutableStateFlow(load())
    val state: StateFlow<List<InboxItem>> = items

    private fun load(): List<InboxItem> = runCatching {
        val a = JSONArray(file.readText())
        (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
            InboxItem(o.getString("id"), Instant.parse(o.getString("at")), o.getString("title"), o.getString("body"), o.optString("target").ifEmpty { null }, o.optBoolean("read"))
        }
    }.getOrDefault(emptyList())

    private suspend fun save(list: List<InboxItem>) = withContext(Dispatchers.IO) {
        val a = JSONArray(); list.forEach { i -> a.put(JSONObject().put("id", i.id).put("at", i.at.toString()).put("title", i.title).put("body", i.body).put("target", i.target ?: "").put("read", i.read)) }
        val tmp = File(context.filesDir, "inbox.tmp"); tmp.writeText(a.toString()); tmp.renameTo(file)
    }

    suspend fun add(title: String, body: String, target: String?) = lock.withLock {
        val next = (listOf(InboxItem(java.util.UUID.randomUUID().toString(), Instant.now(), title, body, target, false)) + items.value).take(100)
        items.value = next; save(next)
    }
    suspend fun markAllRead() = lock.withLock { if (items.value.any { !it.read }) { val next = items.value.map { it.copy(read = true) }; items.value = next; save(next) } }
    suspend fun clear() = lock.withLock { items.value = emptyList(); save(emptyList()) }
}

/** Notification channels and the per-kind switches (Settings → Notifications). */
object Channels {
    const val CLASSES = "classes"; const val MARKS = "marks"; const val REST = "rest"; const val REMINDERS = "reminders"; const val UPDATES = "updates"
    fun create(context: Context) {
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        listOf(CLASSES to ("Next class" to android.app.NotificationManager.IMPORTANCE_HIGH), MARKS to ("New marks" to android.app.NotificationManager.IMPORTANCE_DEFAULT),
            REST to ("Rest timer" to android.app.NotificationManager.IMPORTANCE_HIGH), REMINDERS to ("Reminders" to android.app.NotificationManager.IMPORTANCE_DEFAULT),
            UPDATES to ("App updates" to android.app.NotificationManager.IMPORTANCE_LOW)).forEach { (id, v) ->
            nm.createNotificationChannel(android.app.NotificationChannel(id, v.first, v.second))
        }
    }
}

object CollegeNotices {
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")

    /** Records newly published marks in the inbox and, if enabled, posts one phone notification. */
    suspend fun afterSync(app: PersonalHubApplication, before: CollegeSnapshot, after: CollegeImport) {
        val fresh = newMarks(before, after)
        if (fresh.isEmpty()) return
        val titles = (after.courses.associate { it.code to it.title })
        val lines = fresh.map { m -> "${titles[m.courseCode] ?: m.courseCode} · ${m.title}: ${trim(m.scored!!)}${m.maxMark?.let { "/${trim(it)}" } ?: ""}" }
        val title = if (fresh.size == 1) "New marks" else "${fresh.size} new marks"
        app.inbox.add(title, lines.joinToString("\n"), "marks")
        if (app.settings.notifyMarks.first()) Reminders.notify(app, 7201, title, lines.joinToString("; "), Channels.MARKS, "marks")
    }
    private fun trim(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else "%.1f".format(v)

    /** Schedules the single next class alert (replacing any previous one), or cancels when off/no timetable. */
    suspend fun scheduleClassAlert(app: PersonalHubApplication) {
        val wm = WorkManager.getInstance(app)
        if (!app.settings.notifyClasses.first()) { wm.cancelUniqueWork("class-alert"); return }
        val s = app.college.snapshots.first()
        val now = LocalDateTime.now(app.time.zone)
        val alert = nextClassAlert(s, now) ?: run { wm.cancelUniqueWork("class-alert"); return }
        wm.enqueueUniqueWork("class-alert", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ClassAlertWorker>()
            .setInitialDelay(Duration.between(now, alert.at).toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("at" to alert.next.at.toString())).build())
    }

    fun classText(alert: dev.personalhub.feature.college.NextClass): Pair<String, String> {
        val m = alert.meeting
        return "Next: ${alert.course?.title ?: m.courseCode}" to listOfNotNull("${m.start.format(timeFmt)}–${m.end.format(timeFmt)}", m.venue, m.courseCode).joinToString(" · ")
    }
}

/** Fires the scheduled class alert if it's still accurate, then schedules the next one. */
class ClassAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as PersonalHubApplication
        if (app.settings.notifyClasses.first()) {
            val expected = inputData.getString("at")?.let(LocalDateTime::parse)
            // Re-derive from current data: if the timetable or calendar changed, only alert when the class is still upcoming.
            val now = LocalDateTime.now(app.time.zone)
            val s = app.college.snapshots.first()
            val upcoming = nextClassAlert(s, now.minusMinutes(15))?.next
            if (upcoming != null && expected != null && upcoming.at == expected && upcoming.at.isAfter(now.minusMinutes(1))) {
                val (title, body) = CollegeNotices.classText(upcoming)
                Reminders.notify(app, 7301, title, body, Channels.CLASSES, "timetable")
            }
        }
        CollegeNotices.scheduleClassAlert(app)
        return Result.success()
    }
}
