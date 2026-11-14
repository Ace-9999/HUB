package dev.personalhub.college.api

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class NewMarksTest {
    private val sem = Semester("S1", "Fall")
    private fun mark(title: String, scored: Double?) = MarkComponent("ZZZ1001L", "ETH", title, 50.0, 15.0, scored, scored?.let { it * 0.3 }, null)
    private val before = CollegeSnapshot(sem, listOf(sem), Instant.EPOCH, marks = listOf(mark("CAT1", 40.0), mark("CAT2", null)))
    private fun after(vararg m: MarkComponent, semester: Semester = sem) = CollegeImport(semester, listOf(semester), Instant.EPOCH, marks = m.toList())

    @Test fun onlyNewlyScoredOrChangedComponentsCount() {
        val out = newMarks(before, after(mark("CAT1", 40.0), mark("CAT2", 35.0), mark("DA1", 9.0), mark("Quiz", null)))
        assertEquals(listOf("CAT2", "DA1"), out.map { it.title })
        assertEquals(listOf("CAT1"), newMarks(before, after(mark("CAT1", 42.0))).map { it.title }) // re-evaluated score
    }
    @Test fun calendarKeepsOnlyGeneralSemesterInstructionalEntries() {
        val d = java.time.LocalDate.of(2026, 11, 16)
        val s = CollegeSnapshot(calendar = listOf(CalendarDay(d, "Instructional Day - General Semester", CalendarKind.INSTRUCTIONAL),
            CalendarDay(d, "Instructional Day - Other Programme", CalendarKind.INSTRUCTIONAL), CalendarDay(d, "Holiday - X", CalendarKind.HOLIDAY),
            CalendarDay(d, "Holiday - X", CalendarKind.HOLIDAY)))
        assertEquals(listOf("Instructional Day - General Semester", "Holiday - X"), s.relevantCalendarOn(d).map { it.label })
    }
    @Test fun firstSyncOrSemesterChangeNeverFloods() {
        assertTrue(newMarks(CollegeSnapshot(), after(mark("CAT1", 40.0))).isEmpty())
        assertTrue(newMarks(before, after(mark("CAT2", 35.0), semester = Semester("S2", "Winter"))).isEmpty())
    }
}
