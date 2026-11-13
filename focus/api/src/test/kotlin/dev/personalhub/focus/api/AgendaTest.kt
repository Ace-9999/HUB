package dev.personalhub.focus.api

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class AgendaTest {
    private val today = LocalDate.of(2026, 11, 13)
    private val t0 = Instant.parse("2026-11-01T00:00:00Z")
    private fun task(id: String, day: LocalDate, r: Recurrence = Recurrence.NONE, undated: Boolean = false, p: Priority = Priority.NORMAL) =
        Task(id, id, day, p, null, r, false, t0, t0, undated = undated)

    @Test fun groupsByDueDateWithoutPagingDays() {
        val s = FocusSnapshot(tasks = listOf(task("late", today.minusDays(3)), task("now", today), task("tmr", today.plusDays(1)),
            task("week", today.plusDays(5)), task("nextMonth", today.plusDays(35)), task("someday", today, undated = true)))
        val a = agenda(s, today).associate { it.kind to it.items.map { i -> i.day.task.id } }
        assertEquals(listOf("late"), a[SectionKind.OVERDUE]); assertEquals(listOf("now"), a[SectionKind.TODAY])
        assertEquals(listOf("tmr"), a[SectionKind.TOMORROW]); assertEquals(listOf("week"), a[SectionKind.WEEK])
        assertEquals(listOf("nextMonth"), a[SectionKind.LATER]); assertEquals(listOf("someday"), a[SectionKind.NO_DATE])
        assertNull(agenda(s, today).single { it.kind == SectionKind.NO_DATE }.items.single().date)
    }
    @Test fun repeatingTaskShowsOnceAtNextPendingOccurrence() {
        val daily = task("d", today.minusDays(10), Recurrence.DAILY)
        val doneToday = FocusSnapshot(tasks = listOf(daily), occurrences = listOf(TaskOccurrence("d", today, true)))
        val items = agenda(doneToday, today).flatMap { it.items }
        assertEquals(1, items.size); assertEquals(today.plusDays(1), items.single().date)
        val weekly = task("w", today.minusDays(2), Recurrence.WEEKLY) // next on today+5
        assertEquals(today.plusDays(5), agenda(FocusSnapshot(tasks = listOf(weekly)), today).single().items.single().date)
    }
    @Test fun completedOneOffsMoveToCompletedSectionAndPriorityOrdersWithinDay() {
        val s = FocusSnapshot(tasks = listOf(task("a", today), task("b", today, p = Priority.HIGH), task("c", today.plusDays(40))),
            occurrences = listOf(TaskOccurrence("c", today.plusDays(40), true)))
        val sections = agenda(s, today)
        assertEquals(listOf("b", "a"), sections.first { it.kind == SectionKind.TODAY }.items.map { it.day.task.id })
        assertEquals(listOf("c"), sections.last().items.map { it.day.task.id }); assertEquals(SectionKind.COMPLETED, sections.last().kind)
    }
    @Test fun undatedTasksCannotRepeatOrHaveTime() {
        assertThrows(IllegalArgumentException::class.java) { TaskDraft(title = "x", firstDay = today, recurrence = Recurrence.DAILY, undated = true) }
        assertThrows(IllegalArgumentException::class.java) { TaskDraft(title = "x", firstDay = today, dueTime = LocalTime.NOON, undated = true) }
    }
}
