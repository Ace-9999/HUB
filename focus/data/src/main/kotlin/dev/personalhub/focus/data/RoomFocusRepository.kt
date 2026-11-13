package dev.personalhub.focus.data

import android.content.Context
import androidx.room.withTransaction
import dev.personalhub.core.*
import dev.personalhub.focus.api.*
import kotlinx.coroutines.flow.map
import java.io.Closeable
import java.time.*

// All commands commit rows and the dirty revision atomically; snapshots read one transaction.
class RoomFocusRepository private constructor(private val db: FocusDatabase, private val time: AppTime, private val ids: IdSource) : FocusRepository, Closeable {
    private val dao get() = db.records()
    override val snapshots = db.revision().observe().map {
        db.withTransaction {
            FocusSnapshot(
                dao.tasks().map { Task(it.id, it.title, LocalDate.parse(it.firstDay), Priority.valueOf(it.priority), it.dueTime?.let(LocalTime::parse), Recurrence.valueOf(it.recurrence), it.carryForward, Instant.ofEpochMilli(it.createdAt), Instant.ofEpochMilli(it.updatedAt)) },
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
        dao.task(TaskRow(id, draft.title.trim(), draft.firstDay.toString(), draft.priority.name, draft.dueTime?.toString(), draft.recurrence.name, draft.carryForward, old?.createdAt ?: now, now))
        id
    }
    override suspend fun completeTask(id: String, day: LocalDate, completed: Boolean) = change {
        val row = requireNotNull(dao.task(id)) { "Task no longer exists." }
        require(!row.deleted)
        require(!day.isAfter(time.today())) { "Future occurrences cannot be completed." }
        val start = LocalDate.parse(row.firstDay)
        val recurrence = Recurrence.valueOf(row.recurrence)
        require(day >= start && (recurrence != Recurrence.WEEKLY || day.dayOfWeek == start.dayOfWeek)) { "Task does not occur on this day." }
        val key = if (recurrence == Recurrence.NONE) start else day
        dao.occurrence(OccurrenceRow(id, key.toString(), completed, time.clock.millis()))
    }
    override suspend fun trashTask(id: String, deleted: Boolean) = change {
        val row = requireNotNull(dao.task(id)) { "Task no longer exists." }; dao.task(row.copy(deleted = deleted, updatedAt = time.clock.millis()))
    }
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
    override fun close() { db.close() }
    companion object {
        fun open(context: Context, time: AppTime = AppTime(), ids: IdSource = UuidSource, name: String = "focus.db") = RoomFocusRepository(FocusDatabase.open(context, name), time, ids)
    }
}
