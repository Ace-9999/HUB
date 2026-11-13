package dev.personalhub.app

import dev.personalhub.focus.api.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class RemindersTest {
    private val day = LocalDate.of(2026, 11, 13) // Friday
    private val created = Instant.parse("2026-11-01T00:00:00Z")
    private fun task(r: Recurrence, carry: Boolean = false, due: LocalTime? = LocalTime.of(9, 0)) = Task("t", "Synthetic", day, Priority.NORMAL, due, r, carry, created, created)

    @Test fun oneOffFiresOnceAtDueTimeAndNotAfterCompletion() {
        val t = task(Recurrence.NONE)
        assertEquals(day.atTime(9, 0), Reminders.nextFire(t, FocusSnapshot(listOf(t)), day.atTime(8, 0)))
        assertNull(Reminders.nextFire(t, FocusSnapshot(listOf(t)), day.atTime(10, 0)))
        assertNull(Reminders.nextFire(t, FocusSnapshot(listOf(t), listOf(TaskOccurrence("t", day, true))), day.atTime(8, 0)))
    }
    @Test fun carryForwardKeepsRemindingDailyUntilDone() {
        val t = task(Recurrence.NONE, carry = true)
        assertEquals(day.plusDays(1).atTime(9, 0), Reminders.nextFire(t, FocusSnapshot(listOf(t)), day.atTime(10, 0)))
        assertNull(Reminders.nextFire(t, FocusSnapshot(listOf(t), listOf(TaskOccurrence("t", day, true))), day.atTime(10, 0)))
    }
    @Test fun recurringSkipsCompletedDaysAndWeeklyKeepsWeekday() {
        val d = task(Recurrence.DAILY)
        assertEquals(day.plusDays(1).atTime(9, 0), Reminders.nextFire(d, FocusSnapshot(listOf(d), listOf(TaskOccurrence("t", day, true))), day.atTime(8, 0)))
        val w = task(Recurrence.WEEKLY)
        assertEquals(day.plusDays(7).atTime(9, 0), Reminders.nextFire(w, FocusSnapshot(listOf(w)), day.atTime(10, 0)))
        assertNull(Reminders.nextFire(task(Recurrence.DAILY, due = null), FocusSnapshot(), day.atTime(8, 0)))
    }
}
