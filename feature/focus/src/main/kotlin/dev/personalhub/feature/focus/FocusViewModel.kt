package dev.personalhub.feature.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.personalhub.core.*
import dev.personalhub.focus.api.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*

/** Undoable change shown to the owner after a destructive or completing command. */
data class UndoNotice(val message: String, val id: Long, internal val revert: suspend () -> Unit)

data class FocusUi(
    val loaded: Boolean = false,
    val snapshot: FocusSnapshot = FocusSnapshot(),
    val today: LocalDate = LocalDate.now(),
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    fun tasksOn(day: LocalDate) = snapshot.tasksOn(day)
    fun entry(habitId: String, day: LocalDate) = snapshot.habitEntries.firstOrNull { it.habitId == habitId && it.day == day }
    val recentMoods: List<MoodEntry> get() = snapshot.moods.sortedWith(compareByDescending<MoodEntry> { it.occurredAt }.thenByDescending { it.createdAt })
}

enum class TrendRange(val label: String, val days: Long) { DAY("Day", 1), WEEK("7 days", 7), MONTH("30 days", 30), CALENDAR("This month", 0) }
fun TrendRange.range(today: LocalDate, zone: ZoneId): DateRange = when (this) {
    TrendRange.CALENDAR -> DateRange(today.withDayOfMonth(1), today, zone)
    else -> DateRange(today.minusDays(days - 1), today, zone)
}

// Owns presentation state only; every write goes through the public repository command contract.
class FocusViewModel(private val repository: FocusRepository, private val time: AppTime) : ViewModel() {
    private val error = MutableStateFlow<String?>(null)
    private val undoState = MutableStateFlow<UndoNotice?>(null)
    val message: StateFlow<String?> = error
    val undo: StateFlow<UndoNotice?> = undoState
    val state: StateFlow<FocusUi> = repository.snapshots.map { FocusUi(true, it, time.today(), time.zone) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUi(today = time.today(), zone = time.zone))
    private var counter = 0L

    private fun run(block: suspend () -> Unit) = viewModelScope.launch {
        try { block(); error.value = null } catch (e: IllegalArgumentException) { error.value = e.message ?: "That change could not be saved." }
    }
    private fun offerUndo(message: String, revert: suspend () -> Unit) { undoState.value = UndoNotice(message, ++counter, revert) }
    fun dismissMessage() { error.value = null }
    fun dismissUndo() { undoState.value = null }
    fun performUndo() { val notice = undoState.value ?: return; undoState.value = null; run { notice.revert() } }

    fun saveTask(draft: () -> TaskDraft) = run { repository.saveTask(draft()) }
    fun setTaskDone(item: TaskDay, done: Boolean) = run {
        repository.completeTask(item.task.id, item.occurrenceDay, done)
        if (done) offerUndo("Completed “${item.task.title}”") { repository.completeTask(item.task.id, item.occurrenceDay, false) }
    }
    fun deleteTask(task: Task) = run { repository.trashTask(task.id); offerUndo("Task deleted") { repository.trashTask(task.id, false) } }

    fun saveHabit(draft: () -> HabitDraft) = run { repository.saveHabit(draft()) }
    fun deleteHabit(habit: Habit) = run { repository.trashHabit(habit.id); offerUndo("Habit removed") { repository.trashHabit(habit.id, false) } }
    /** Sets the day's value; null clears the entry back to "not recorded" (unknown), never to zero. */
    fun logHabit(habit: Habit, day: LocalDate, value: Double?) = run {
        val previous = state.value.entry(habit.id, day)
        repository.putHabitEntry(habit.id, day, value, previous?.note)
        offerUndo(if (value == null) "Entry cleared" else "Logged ${habit.unit.format(value)}") { repository.putHabitEntry(habit.id, day, previous?.value, previous?.note) }
    }

    fun saveMood(id: String?, mood: Mood, occurredAt: Instant, note: String?) = run {
        val saved = repository.saveMood(MoodDraft(id, mood, occurredAt, time.zone, note?.trim()?.ifEmpty { null }))
        if (id == null) offerUndo("${mood.emoji} ${mood.label} logged") { repository.trashMood(saved) }
    }
    fun deleteMood(entry: MoodEntry) = run { repository.trashMood(entry.id); offerUndo("Mood entry deleted") { repository.trashMood(entry.id, false) } }
    fun now(): Instant = time.clock.instant()
}

fun HabitUnit.format(value: Double): String = when (this) {
    HabitUnit.YES_NO -> if (value >= 1.0) "yes" else "no"
    HabitUnit.MINUTES -> "${trim(value)} min"
    HabitUnit.COUNT -> trim(value)
}
private fun trim(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else "%.1f".format(v)
