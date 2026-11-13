package dev.personalhub.focus.api

import dev.personalhub.core.*
import java.time.*

// Stable domain models: no Room, Android, composables or presentation styling.
enum class Recurrence { NONE, DAILY, WEEKLY }
enum class Priority { LOW, NORMAL, HIGH }
data class Task(val id: String, val title: String, val firstDay: LocalDate, val priority: Priority,
    val dueTime: LocalTime?, val recurrence: Recurrence, val carryForward: Boolean, val createdAt: Instant, val updatedAt: Instant, val google: Boolean = false) {
    fun occurs(day: LocalDate): Boolean = day >= firstDay && when (recurrence) {
        Recurrence.NONE -> day == firstDay || carryForward
        Recurrence.DAILY -> true
        Recurrence.WEEKLY -> day.dayOfWeek == firstDay.dayOfWeek
    }
    fun occurrenceKey(day: LocalDate): LocalDate = if (recurrence == Recurrence.NONE) firstDay else day
}
data class TaskDraft(val id: String? = null, val title: String, val firstDay: LocalDate, val priority: Priority = Priority.NORMAL,
    val dueTime: LocalTime? = null, val recurrence: Recurrence = Recurrence.NONE, val carryForward: Boolean = false) {
    init { require(title.isNotBlank() && title.length <= 160) { "Enter a task title of 1–160 characters." } }
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
