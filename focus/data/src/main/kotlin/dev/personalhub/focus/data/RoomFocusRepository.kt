package dev.personalhub.focus.data

import android.content.Context
import androidx.room.withTransaction
import dev.personalhub.core.*
import dev.personalhub.focus.api.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.io.Closeable
import java.time.*

// All commands commit rows and the dirty revision atomically; snapshots read one transaction.
class RoomFocusRepository private constructor(private val db: FocusDatabase, private val time: AppTime, private val ids: IdSource) : FocusRepository, TaskSyncStore, FocusBackupStore, Closeable {
    /** Called after every committed local task change so the host can schedule a debounced sync. */
    @Volatile var onTasksChanged: (() -> Unit)? = null
    private val dao get() = db.records()
    override val snapshots = db.revision().observe().map {
        db.withTransaction {
            FocusSnapshot(
                dao.tasks().map { Task(it.id, it.title, LocalDate.parse(it.firstDay), Priority.valueOf(it.priority), it.dueTime?.let(LocalTime::parse), Recurrence.valueOf(it.recurrence), it.carryForward, Instant.ofEpochMilli(it.createdAt), Instant.ofEpochMilli(it.updatedAt), it.googleId != null, it.undated) },
                dao.occurrences().map { TaskOccurrence(it.taskId, LocalDate.parse(it.day), it.completed) },
                dao.habits().map { Habit(it.id, it.label, HabitUnit.valueOf(it.unit), GoalDirection.valueOf(it.direction), it.target, it.sensitive, Instant.ofEpochMilli(it.createdAt), Instant.ofEpochMilli(it.updatedAt)) },
                dao.entries().map { HabitEntry(it.habitId, LocalDate.parse(it.day), it.value, it.explicitZero, it.note, Instant.ofEpochMilli(it.updatedAt)) },
                dao.moods().map { MoodEntry(it.id, Mood.fromCode(it.code), Instant.ofEpochMilli(it.occurredAt), ZoneId.of(it.originalZone), Instant.ofEpochMilli(it.createdAt), Instant.ofEpochMilli(it.updatedAt), it.note, it.revision) }
            )
        }
    }
    private suspend fun <T> change(block: suspend () -> T): T = db.withTransaction { val result = block(); db.revision().committedChange(); result }
    override suspend fun saveTask(draft: TaskDraft): String = change {
        val old = draft.id?.let { requireNotNull(dao.task(it)) { "Task no longer exists." } }
        val id = old?.id ?: ids.next(); val now = time.clock.millis()
        require(old == null || !old.deleted) { "Restore the task before editing." }
        dao.task(TaskRow(id, draft.title.trim(), draft.firstDay.toString(), draft.priority.name, draft.dueTime?.toString(), draft.recurrence.name, draft.carryForward, old?.createdAt ?: now, now,
            googleId = old?.googleId, googleList = old?.googleList, dirty = draft.recurrence == Recurrence.NONE, undated = draft.undated))
        id
    }.also { onTasksChanged?.invoke() }
    override suspend fun completeTask(id: String, day: LocalDate, completed: Boolean) = change {
        val row = requireNotNull(dao.task(id)) { "Task no longer exists." }
        require(!row.deleted)
        val start = LocalDate.parse(row.firstDay)
        val recurrence = Recurrence.valueOf(row.recurrence)
        // One-off tasks may be finished early (as in Google Tasks); repeating occurrences only on/after their day.
        require(recurrence == Recurrence.NONE || !day.isAfter(time.today())) { "Future occurrences cannot be completed." }
        require(day >= start && (recurrence != Recurrence.WEEKLY || day.dayOfWeek == start.dayOfWeek)) { "Task does not occur on this day." }
        val key = if (recurrence == Recurrence.NONE) start else day
        dao.occurrence(OccurrenceRow(id, key.toString(), completed, time.clock.millis()))
        if (recurrence == Recurrence.NONE) dao.task(row.copy(dirty = true, updatedAt = time.clock.millis()))
    }.also { onTasksChanged?.invoke() }
    override suspend fun trashTask(id: String, deleted: Boolean) = change {
        val row = requireNotNull(dao.task(id)) { "Task no longer exists." }
        dao.task(row.copy(deleted = deleted, updatedAt = time.clock.millis(), dirty = row.recurrence == Recurrence.NONE.name))
    }.also { onTasksChanged?.invoke() }
    override suspend fun saveHabit(draft: HabitDraft): String = change {
        val old = draft.id?.let { requireNotNull(dao.habit(it)) { "Habit no longer exists." } }
        val id = old?.id ?: ids.next(); val now = time.clock.millis()
        require(old == null || !old.deleted) { "Restore the habit before editing." }
        // Metric changes need explicit migration; never reinterpret historical values silently.
        require(old == null || old.unit == draft.unit.name) { "Create a new habit to change its unit." }
        dao.habit(HabitRow(id, draft.label.trim(), draft.unit.name, draft.direction.name, draft.target, draft.sensitive, old?.createdAt ?: now, now))
        id
    }
    override suspend fun trashHabit(id: String, deleted: Boolean) = change {
        val row = requireNotNull(dao.habit(id)) { "Habit no longer exists." }; dao.habit(row.copy(deleted = deleted, updatedAt = time.clock.millis()))
    }
    override suspend fun putHabitEntry(habitId: String, day: LocalDate, value: Double?, note: String?) = change {
        val habit = requireNotNull(dao.habit(habitId)) { "Habit no longer exists." }
        require(!habit.deleted); require(day <= time.today()) { "Future entries are not allowed." }
        require((note?.length ?: 0) <= 2000) { "Notes are limited to 2,000 characters." }
        if (value == null) dao.removeEntry(habitId, day.toString()) else {
            validateHabitValue(HabitUnit.valueOf(habit.unit), value)
            dao.entry(HabitEntryRow(habitId, day.toString(), value, value == 0.0, note?.trim()?.ifEmpty { null }, time.clock.millis()))
        }
    }
    override suspend fun saveMood(draft: MoodDraft): String = change {
        require(!draft.occurredAt.isAfter(time.clock.instant())) { "Mood occurrence time cannot be in the future." }
        val old = draft.id?.let { requireNotNull(dao.mood(it)) { "Mood entry no longer exists." } }
        require(old == null || !old.deleted) { "Restore the mood entry before editing." }
        val id = old?.id ?: ids.next(); val now = time.clock.millis()
        dao.mood(MoodRow(id, draft.mood.code, draft.occurredAt.toEpochMilli(), draft.originalZone.id, old?.createdAt ?: now, now, draft.note?.trim()?.ifEmpty { null }, (old?.revision ?: 0) + 1))
        id
    }
    override suspend fun trashMood(id: String, deleted: Boolean) = change {
        val row = requireNotNull(dao.mood(id)) { "Mood entry no longer exists." }; dao.mood(row.copy(deleted = deleted, revision = row.revision + 1, updatedAt = time.clock.millis()))
    }
    // ---------------------------------------------------------------- TaskSyncStore (Google Tasks adapter only)
    private suspend fun completedOn(row: TaskRow) = dao.occurrences().any { it.taskId == row.id && it.day == row.firstDay && it.completed }
    override suspend fun syncCandidates(): List<LocalSyncTask> = db.withTransaction {
        dao.oneOffTasks().map { r -> LocalSyncTask(r.id, r.googleId, r.googleList, r.title, if (r.undated) null else LocalDate.parse(r.firstDay), completedOn(r), r.deleted, Instant.ofEpochMilli(r.updatedAt), r.dirty) }
    }
    override suspend fun applyRemote(remote: RemoteTask) = change {
        val row = dao.taskByGoogle(remote.googleId)
        val local = row?.let { r -> LocalSyncTask(r.id, r.googleId, r.googleList, r.title, if (r.undated) null else LocalDate.parse(r.firstDay), completedOn(r), r.deleted, Instant.ofEpochMilli(r.updatedAt), r.dirty) }
        if (!remoteWins(local, remote)) return@change
        if (row == null) {
            if (remote.deleted) return@change
            val id = ids.next(); val now = time.clock.millis()
            val first = remote.due ?: time.today()
            dao.task(TaskRow(id, remote.title.trim().ifEmpty { "(untitled)" }.take(160), first.toString(), Priority.NORMAL.name, null, Recurrence.NONE.name, true, now, remote.updated.toEpochMilli(),
                googleId = remote.googleId, googleList = remote.list, dirty = false, undated = remote.due == null))
            if (remote.completed) dao.occurrence(OccurrenceRow(id, first.toString(), true, now))
        } else {
            val first = remote.due?.toString() ?: row.firstDay
            dao.task(row.copy(title = remote.title.trim().ifEmpty { row.title }.take(160), firstDay = first, deleted = remote.deleted, googleList = remote.list,
                updatedAt = remote.updated.toEpochMilli(), dirty = false, undated = remote.due == null))
            dao.occurrence(OccurrenceRow(row.id, first, remote.completed, time.clock.millis()))
        }
    }
    override suspend fun linkPushed(localId: String, googleId: String, list: String, remoteUpdated: Instant) = change {
        dao.task(localId)?.let { dao.task(it.copy(googleId = googleId, googleList = list, dirty = false)) }; Unit
    }
    override suspend fun unlink(localId: String) = change { dao.task(localId)?.let { dao.task(it.copy(googleId = null, googleList = null, dirty = false)) }; Unit }
    override suspend fun lastSync(): Instant? = dao.syncValue("googleLastSync")?.let(Instant::parse)
    override suspend fun setLastSync(at: Instant) = dao.syncValue(SyncStateRow("googleLastSync", at.toString()))

    override suspend fun exportAll(): FocusBackup = db.withTransaction {
        val s = snapshots.first()
        FocusBackup(s.tasks, dao.tasks().filter { it.googleId != null }.associate { it.id to (it.googleId!! to it.googleList) }, s.occurrences, s.habits, s.habitEntries, s.moods)
    }
    override suspend fun replaceAll(b: FocusBackup) = change {
        dao.wipeOccurrences(); dao.wipeEntries(); dao.wipeTasks(); dao.wipeHabits(); dao.wipeMoods()
        b.tasks.forEach { t -> val link = b.googleLinks[t.id]
            dao.task(TaskRow(t.id, t.title, t.firstDay.toString(), t.priority.name, t.dueTime?.toString(), t.recurrence.name, t.carryForward,
                t.createdAt.toEpochMilli(), t.updatedAt.toEpochMilli(), false, link?.first, link?.second, false, t.undated)) }
        val taskIds = b.tasks.map { it.id }.toSet()
        b.occurrences.filter { it.taskId in taskIds }.forEach { dao.occurrence(OccurrenceRow(it.taskId, it.day.toString(), it.completed, time.clock.millis())) }
        b.habits.forEach { h -> dao.habit(HabitRow(h.id, h.label, h.unit.name, h.direction.name, h.target, h.sensitive, h.createdAt.toEpochMilli(), h.updatedAt.toEpochMilli())) }
        val habitIds = b.habits.map { it.id }.toSet()
        b.entries.filter { it.habitId in habitIds }.forEach { e -> dao.entry(HabitEntryRow(e.habitId, e.day.toString(), e.value, e.explicitZero, e.note, e.updatedAt.toEpochMilli())) }
        b.moods.forEach { m -> dao.mood(MoodRow(m.id, m.mood.code, m.occurredAt.toEpochMilli(), m.originalZone.id, m.createdAt.toEpochMilli(), m.updatedAt.toEpochMilli(), m.note, m.revision)) }
    }

    override fun close() { db.close() }
    companion object {
        fun open(context: Context, time: AppTime = AppTime(), ids: IdSource = UuidSource, name: String = "focus.db") = RoomFocusRepository(FocusDatabase.open(context, name), time, ids)
    }
}
