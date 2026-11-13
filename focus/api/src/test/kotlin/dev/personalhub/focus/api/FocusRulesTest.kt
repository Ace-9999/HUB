package dev.personalhub.focus.api

import dev.personalhub.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class FocusRulesTest {
    private val day = LocalDate.parse("2026-11-12")
    private val now = Instant.parse("2026-11-12T12:00:00Z")
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun task(repeat: Recurrence = Recurrence.DAILY, carry: Boolean = false) = Task("task", "Study", day, Priority.NORMAL, null, repeat, carry, now, now)
    @Test fun recurringCompletionIsSpecificToEachDay() {
        val snapshot = FocusSnapshot(tasks = listOf(task()), occurrences = listOf(TaskOccurrence("task", day, true)))
        assertTrue(snapshot.tasksOn(day).single().completed); assertFalse(snapshot.tasksOn(day.plusDays(1)).single().completed)
    }
    @Test fun carryForwardUsesOneOriginalOccurrenceAndStopsWhenCompleted() {
        val snapshot = FocusSnapshot(tasks = listOf(task(Recurrence.NONE, true)))
        assertEquals(day, snapshot.tasksOn(day.plusDays(1)).single().occurrenceDay)
        assertTrue(snapshot.copy(occurrences = listOf(TaskOccurrence("task", day, true))).tasksOn(day.plusDays(1)).isEmpty())
    }
    @Test fun weeklyTaskDoesNotAppearEveryDayOrBeforeStart() {
        val t = task(Recurrence.WEEKLY)
        assertFalse(t.occurs(day.minusDays(7))); assertFalse(t.occurs(day.plusDays(1))); assertTrue(t.occurs(day.plusDays(7)))
    }
    @Test fun explicitZeroIsAConfirmedReductionSuccessButMissingRemainsUnknown() {
        val habit = Habit("h", "Private", HabitUnit.COUNT, GoalDirection.REDUCE, 0.0, true, now, now)
        assertNull(goalMet(habit, null)); assertEquals(true, goalMet(habit, HabitEntry("h", day, 0.0, true, null, now)))
        val series = habitSeries(habit, listOf(HabitEntry("h", day, 0.0, true, null, now)), DateRange(day, day.plusDays(1), zone))
        assertEquals(0.0, series.points.first().value!!, 0.0); assertNull(series.points.last().value)
        assertEquals(Completeness.UNKNOWN, series.points.last().completeness)
    }
    @Test fun tenSameTimeMoodsRemainTenCategoricalObservationsAndTiesAreMixed() {
        val entries = (1..10).map { MoodEntry("m$it", if (it <= 5) Mood.CALM_CONTENT else Mood.BORED, now, zone, now, now, null, 1) }
        val range = DateRange(day, day, zone)
        assertEquals(10, moodEvents(entries, range).size)
        val distribution = moodDistribution(entries, range)
        assertEquals(10, distribution.observationCount); assertEquals(1, distribution.recordedDays)
        assertEquals(setOf(Mood.CALM_CONTENT, Mood.BORED), distribution.dominant); assertEquals(9, distribution.counts.size)
    }
    @Test fun moodRangeUsesReportingZoneAndPreservesOriginalZone() {
        val event = MoodEntry("m", Mood.BORED, Instant.parse("2026-11-11T20:00:00Z"), ZoneId.of("UTC"), now, now, null, 1)
        assertEquals(1, moodEvents(listOf(event), DateRange(day, day, zone)).size); assertEquals(ZoneId.of("UTC"), event.originalZone)
    }
    @Test(expected = IllegalArgumentException::class) fun unknownMoodCodeIsRejected() { Mood.fromCode("neutral") }
    @Test(expected = IllegalArgumentException::class) fun negativeHabitValuesAreRejected() { validateHabitValue(HabitUnit.COUNT, -1.0) }
    @Test(expected = IllegalArgumentException::class) fun yesNoCannotContainArbitraryCounts() { validateHabitValue(HabitUnit.YES_NO, 2.0) }
}
