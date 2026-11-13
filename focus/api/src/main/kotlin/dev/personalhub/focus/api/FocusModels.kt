package dev.personalhub.focus.api

import dev.personalhub.core.*
import java.time.*

// Stable domain models: no Room, Android, composables or presentation styling.
enum class Recurrence { NONE, DAILY, WEEKLY }
enum class Priority { LOW, NORMAL, HIGH }
data class Task(val id: String, val title: String, val firstDay: LocalDate, val priority: Priority,
    val dueTime: LocalTime?, val recurrence: Recurrence, val carryForward: Boolean, val createdAt: Instant, val updatedAt: Instant, val google: Boolean = false,
    /** No due date (Google-Tasks style); [firstDay] is then only the creation day. */
    val undated: Boolean = false) {
    fun occurs(day: LocalDate): Boolean = day >= firstDay && when (recurrence) {
        Recurrence.NONE -> day == firstDay || carryForward || undated
        Recurrence.DAILY -> true
        Recurrence.WEEKLY -> day.dayOfWeek == firstDay.dayOfWeek
    }
    fun occurrenceKey(day: LocalDate): LocalDate = if (recurrence == Recurrence.NONE) firstDay else day
}
data class TaskDraft(val id: String? = null, val title: String, val firstDay: LocalDate, val priority: Priority = Priority.NORMAL,
    val dueTime: LocalTime? = null, val recurrence: Recurrence = Recurrence.NONE, val carryForward: Boolean = false, val undated: Boolean = false) {
    init {
        require(title.isNotBlank() && title.length <= 160) { "Enter a task title of 1–160 characters." }
        require(!undated || recurrence == Recurrence.NONE) { "Repeating tasks need a start date." }
        require(!undated || dueTime == null) { "Add a date before setting a time." }
    }
}
data class TaskOccurrence(val taskId: String, val day: LocalDate, val completed: Boolean)
data class TaskDay(val task: Task, val occurrenceDay: LocalDate, val completed: Boolean)
enum class HabitUnit(val label: String) { COUNT("count"), MINUTES("minutes"), YES_NO("yes / no") }
enum class GoalDirection { INCREASE, REDUCE, ABSTAIN }
data class Habit(val id: String, val label: String, val unit: HabitUnit, val direction: GoalDirection,
    val target: Double, val sensitive: Boolean, val createdAt: Instant, val updatedAt: Instant)
data class HabitDraft(val id: String? = null, val label: String, val unit: HabitUnit = HabitUnit.COUNT,
    val direction: GoalDirection = GoalDirection.INCREASE, val target: Double = 1.0, val sensitive: Boolean = false) {
    init {
        require(label.isNotBlank() && label.length <= 160) { "Enter a habit label of 1–160 characters." }
        validateHabitValue(unit, target)
        require(direction != GoalDirection.ABSTAIN || target == 0.0) { "Abstain goals use a zero target." }
    }
}
fun validateHabitValue(unit: HabitUnit, value: Double) {
    require(value.isFinite() && value >= 0) { "Use a nonnegative finite value." }
    require(unit != HabitUnit.COUNT || value % 1.0 == 0.0) { "Counts must be whole numbers." }
    require(unit != HabitUnit.YES_NO || value == 0.0 || value == 1.0) { "Yes/no values must be 0 or 1." }
}
data class HabitEntry(val habitId: String, val day: LocalDate, val value: Double, val explicitZero: Boolean, val note: String?, val updatedAt: Instant)
fun goalMet(habit: Habit, entry: HabitEntry?): Boolean? = entry?.let {
    when (habit.direction) { GoalDirection.INCREASE -> it.value >= habit.target; GoalDirection.REDUCE -> it.value <= habit.target; GoalDirection.ABSTAIN -> it.value == 0.0 }
}
enum class Mood(val code: String, val emoji: String, val label: String) {
    CALM_CONTENT("calm_content", "😌", "Calm / content"), GOOD_ENERGISED("good_energised", "⚡", "Good / energised"),
    FLAT_NUMB("flat_numb", "😐", "Flat / numb"), FOGGY_UNREAL("foggy_unreal", "🌫️", "Foggy / unreal"),
    ANXIOUS_RESTLESS("anxious_restless", "😟", "Anxious / restless"), LOW_SAD("low_sad", "😔", "Low / sad"),
    IRRITABLE("irritable", "😠", "Irritable"), LONELY_LEFT_OUT("lonely_left_out", "🫥", "Lonely / left out"), BORED("bored", "🥱", "Bored");
    companion object { fun fromCode(code: String): Mood = entries.singleOrNull { it.code == code } ?: throw IllegalArgumentException("Unsupported mood code.") }
}
data class MoodEntry(val id: String, val mood: Mood, val occurredAt: Instant, val originalZone: ZoneId,
    val createdAt: Instant, val updatedAt: Instant, val note: String?, val revision: Long)
data class MoodDraft(val id: String? = null, val mood: Mood, val occurredAt: Instant, val originalZone: ZoneId, val note: String? = null) {
    init { require((note?.length ?: 0) <= 2000) { "Notes are limited to 2,000 characters." } }
}
data class FocusSnapshot(val tasks: List<Task> = emptyList(), val occurrences: List<TaskOccurrence> = emptyList(),
    val habits: List<Habit> = emptyList(), val habitEntries: List<HabitEntry> = emptyList(), val moods: List<MoodEntry> = emptyList()) {
    fun tasksOn(day: LocalDate): List<TaskDay> = tasks.filter { it.occurs(day) }.map { task ->
        val key = task.occurrenceKey(day)
        TaskDay(task, key, occurrences.any { it.taskId == task.id && it.day == key && it.completed })
    }.filterNot { it.task.recurrence == Recurrence.NONE && day > it.task.firstDay && it.completed }
        .sortedWith(compareByDescending<TaskDay> { it.task.priority.ordinal }.thenBy { it.task.dueTime ?: LocalTime.MAX }.thenBy { it.task.createdAt })
}
data class MoodDistribution(val counts: Map<Mood, Int>, val observationCount: Int, val recordedDays: Int) {
    val dominant: Set<Mood> get() = if (observationCount == 0) emptySet() else counts.filterValues { it == counts.values.max() }.keys
}
fun moodEvents(entries: List<MoodEntry>, range: DateRange): List<MoodEntry> = entries.filter { it.occurredAt.atZone(range.zone).toLocalDate() in range }.sortedWith(compareBy<MoodEntry> { it.occurredAt }.thenBy { it.id })
fun moodDistribution(entries: List<MoodEntry>, range: DateRange): MoodDistribution {
    val selected = moodEvents(entries, range)
    return MoodDistribution(Mood.entries.associateWith { mood -> selected.count { it.mood == mood } }, selected.size, selected.map { it.occurredAt.atZone(range.zone).toLocalDate() }.distinct().size)
}
fun habitSeries(habit: Habit, entries: List<HabitEntry>, range: DateRange): MetricSeries {
    val byDay = entries.filter { it.habitId == habit.id }.associateBy { it.day }
    val points = generateSequence(range.start) { it.plusDays(1) }.takeWhile { it <= range.endInclusive }.map { day ->
        val entry = byDay[day]
        MetricPoint(day, entry?.value, if (entry == null) Completeness.UNKNOWN else Completeness.COMPLETE)
    }.toList()
    return MetricSeries("habit:${habit.id}", habit.unit.label, points)
}

// ---------------------------------------------------------------- Google-Tasks-style agenda
enum class SectionKind(val title: String) { OVERDUE("Overdue"), TODAY("Today"), TOMORROW("Tomorrow"), WEEK("Next 7 days"), LATER("Later"), NO_DATE("No date"), COMPLETED("Completed") }
data class AgendaItem(val day: TaskDay, val date: LocalDate?)
data class AgendaSection(val kind: SectionKind, val items: List<AgendaItem>)

/**
 * One list of everything pending, grouped by due date relative to [today] (no day-by-day paging).
 * One-off tasks sit at their due date (overdue stays visible until done); undated tasks under "No date";
 * repeating tasks appear once, at their next not-completed occurrence within [horizonDays].
 */
fun agenda(s: FocusSnapshot, today: LocalDate, horizonDays: Long = 60, completedLimit: Int = 30): List<AgendaSection> {
    fun done(t: Task, key: LocalDate) = s.occurrences.any { it.taskId == t.id && it.day == key && it.completed }
    val open = mutableListOf<AgendaItem>(); val completed = mutableListOf<Pair<AgendaItem, Instant>>()
    s.tasks.forEach { t ->
        if (t.recurrence == Recurrence.NONE) {
            val item = AgendaItem(TaskDay(t, t.firstDay, done(t, t.firstDay)), if (t.undated) null else t.firstDay)
            if (item.day.completed) completed += item to t.updatedAt else open += item
        } else {
            val next = generateSequence(maxOf(today, t.firstDay)) { it.plusDays(1) }.take(horizonDays.toInt()).firstOrNull { t.occurs(it) && !done(t, it) }
            if (next != null) open += AgendaItem(TaskDay(t, next, false), next)
        }
    }
    val order = compareBy<AgendaItem>({ it.date ?: LocalDate.MAX }, { -it.day.task.priority.ordinal }, { it.day.task.dueTime ?: LocalTime.MAX }, { it.day.task.createdAt })
    fun kind(d: LocalDate?) = when {
        d == null -> SectionKind.NO_DATE
        d < today -> SectionKind.OVERDUE
        d == today -> SectionKind.TODAY
        d == today.plusDays(1) -> SectionKind.TOMORROW
        d <= today.plusDays(7) -> SectionKind.WEEK
        else -> SectionKind.LATER
    }
    val grouped = open.sortedWith(order).groupBy { kind(it.date) }
    val sections = SectionKind.entries.filter { it != SectionKind.COMPLETED }.mapNotNull { k -> grouped[k]?.let { AgendaSection(k, it) } }
    val doneList = completed.sortedByDescending { it.second }.take(completedLimit).map { it.first }
    return if (doneList.isEmpty()) sections else sections + AgendaSection(SectionKind.COMPLETED, doneList)
}
