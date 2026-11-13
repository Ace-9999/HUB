package dev.personalhub.vtop

import dev.personalhub.college.api.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

// Synthetic structural fixtures only: invented codes, rooms and names; no captured portal content.
class VtopParserTest {
    private fun table(vararg rows: List<String>, context: List<String> = emptyList()) = PortalTable("", rows.map { r -> r.map { PortalCell(it) } }, context)

    private val courseTable = table(
        listOf("Sl.No", "Class Group", "Course", "L T P J C", "Category", "Class Nbr", "Slot - Venue", "Faculty Details"),
        listOf("1", "GEN", "ZZZ1001L - Synthetic Systems ( Embedded Theory )", "3 0 0 0 3", "PC", "CH001", "A1+TA1 - XB1-101", "Faculty One - SCOPE"),
        listOf("2", "GEN", "ZZZ1001P - Synthetic Systems Lab ( Embedded Lab )", "0 0 2 0 1", "PC", "CH002", "L1+L2 - XB1-LAB2", "Faculty One - SCOPE"),
        listOf("", "", "Total Number Of Credits: 4", "", "", "", "", ""),
    )
    // Grid as produced by the span-expanding script: labels repeat for row-spanned cells.
    private val grid = table(
        listOf("THEORY", "Start", "08:00", "08:55", "Lunch", "02:00"),
        listOf("THEORY", "End", "08:50", "09:45", "Lunch", "02:50"),
        listOf("LAB", "Start", "08:00", "08:50", "Lunch", "02:00"),
        listOf("LAB", "End", "08:50", "09:40", "Lunch", "02:50"),
        listOf("MON", "THEORY", "A1-ZZZ1001L-ETH-XB1-101-ALL", "B1", "Lunch", "TA1-ZZZ1001L-ETH-XB1-101-ALL"),
        listOf("MON", "LAB", "L1-ZZZ1001P-ELA-XB1-LAB2-ALL", "L2-ZZZ1001P-ELA-XB1-LAB2-ALL", "Lunch", "L31"),
        listOf("TUE", "THEORY", "C1", "D1", "Lunch", "E1"),
        listOf("TUE", "LAB", "L7", "L8", "Lunch", "L37"),
    )

    @Test fun coursesAreMappedByHeaderLabelsAndKeepUnknownsNull() {
        val parsed = VtopParser.courses(listOf(table(listOf("unrelated", "x")), courseTable))
        assertEquals(2, parsed.rows.size)
        val c = parsed.rows.first()
        assertEquals("ZZZ1001L", c.code); assertEquals("Synthetic Systems", c.title); assertEquals("Embedded Theory", c.type)
        assertEquals(3.0, c.credits!!, 0.0); assertEquals("A1+TA1", c.slot); assertEquals("XB1-101", c.venue); assertEquals("Faculty One", c.faculty)
        assertEquals(ImportState.OK, parsed.status.state)
    }
    @Test fun reorderedColumnsStillParse() {
        val t = table(listOf("Slot - Venue", "Course", "L T P J C"), listOf("B1 - XB2-202", "ZZZ2002L - Other Course ( Theory Only )", "2 0 0 0 2"))
        val c = VtopParser.courses(listOf(t)).rows.single()
        assertEquals("ZZZ2002L", c.code); assertEquals("B1", c.slot); assertEquals(2.0, c.credits!!, 0.0)
    }
    @Test fun timetableGridProducesMeetingsWithAfternoonTimesAndMergedLabs() {
        val m = VtopParser.meetings(listOf(grid)).rows
        assertEquals(3, m.size)
        val first = m.first()
        assertEquals(DayOfWeek.MONDAY, first.day); assertEquals(LocalTime.of(8, 0), first.start); assertEquals("ZZZ1001L", first.courseCode)
        val lab = m.single { it.component == "ELA" }
        assertEquals(LocalTime.of(8, 0), lab.start); assertEquals(LocalTime.of(9, 40), lab.end); assertEquals("L1+L2", lab.slot); assertEquals("XB1-LAB2", lab.venue)
        val afternoon = m.last()
        assertEquals(LocalTime.of(14, 0), afternoon.start); assertEquals(LocalTime.of(14, 50), afternoon.end)
    }
    @Test fun freeGridIsEmptyNotFabricated() {
        val free = table(listOf("THEORY", "Start", "08:00"), listOf("THEORY", "End", "08:50"), listOf("MON", "THEORY", "A1"))
        assertTrue(VtopParser.meetings(listOf(free)).rows.isEmpty())
    }
    @Test fun attendanceUsesCountsAndRejectsImpossibleRows() {
        val t = table(
            listOf("Sl.No.", "Course Code", "Course Title", "Course Type", "Slot", "Faculty", "Attended Classes", "Total Classes", "Attendance Percentage"),
            listOf("1", "ZZZ1001L", "Synthetic Systems", "Embedded Theory", "A1+TA1", "F", "27", "30", "90"),
            listOf("2", "ZZZ1001P", "Synthetic Systems Lab", "Embedded Lab", "L1+L2", "F", "9", "8", "112"),
        )
        val rows = VtopParser.attendance(listOf(t)).rows
        assertEquals(1, rows.size)
        assertEquals(27, rows[0].attended); assertEquals(30, rows[0].total); assertEquals(90, rows[0].portalPercent)
        assertEquals("THEORY", componentOf(rows[0].type))
    }
    @Test fun attendanceWithCombinedCourseDetailColumn() {
        val t = table(listOf("Course Detail", "Attended Classes", "Total Classes"), listOf("ZZZ3003L - Combined Name - Theory Only", "5", "10"))
        val r = VtopParser.attendance(listOf(t)).rows.single()
        assertEquals("ZZZ3003L", r.courseCode); assertNull(r.portalPercent); assertEquals(50.0, r.percent!!, 0.0)
    }
    @Test fun marksTakeCourseFromContextRowAndKeepMissingScoresNull() {
        val t = table(
            listOf("Sl.No.", "Mark Title", "Max. Mark", "Weightage %", "Status", "Scored Mark", "Weightage Mark"),
            listOf("1", "CAT-1", "50", "15", "Present", "40", "12"),
            listOf("2", "Assessment 2", "10", "10", "", "", ""),
            context = listOf("1", "CH001", "ZZZ1001L", "Synthetic Systems", "Embedded Theory", "Faculty", "A1"),
        )
        val rows = VtopParser.marks(listOf(t)).rows
        assertEquals(2, rows.size)
        assertEquals("ZZZ1001L", rows[0].courseCode); assertEquals(12.0, rows[0].weighted!!, 0.0); assertEquals("Embedded Theory", rows[0].courseType)
        assertNull(rows[1].scored); assertNull(rows[1].weighted)
    }
    @Test fun gradeHistoryAndCgpaSummary() {
        val g = table(listOf("Sl.No.", "Course Code", "Course Title", "Course Type", "Credits", "Grade", "Exam Month"),
            listOf("1", "ZZZ0101L", "Intro", "Theory Only", "3", "A", "Nov-2025"), listOf("2", "ZZZ0102P", "Intro Lab", "Lab Only", "1", "S", "Nov-2025"))
        val s = table(listOf("Credits Registered", "Credits Earned", "CGPA", "S", "A"), listOf("40", "40", "8.92", "5", "6"))
        val (parsed, summary) = VtopParser.grades(listOf(g, s))
        assertEquals(2, parsed.rows.size); assertEquals("A", parsed.rows[0].grade); assertEquals(3.0, parsed.rows[0].credits!!, 0.0)
        assertEquals(8.92, summary!!.cgpa!!, 0.0); assertEquals(40.0, summary.creditsEarned!!, 0.0)
    }
    @Test fun semesterPlaceholdersAreDropped() {
        val s = VtopParser.semesters(listOf("" to "-- Choose Semester --", "S1" to "Fall  Semester 2026-27", "S1" to "dup"))
        assertEquals(listOf(Semester("S1", "Fall Semester 2026-27")), s)
    }
    @Test fun importKeepsPerDatasetFailuresAndRejectsExpiredSession() {
        val tt = JSONObject().put("status", 200).put("tables", JSONArray().put(JSONObject().put("id", "").put("rows",
            JSONArray(courseTable.rows.map { r -> JSONArray(r.map { it.text }) })).put("context", JSONArray())))
        val payload = JSONObject().put("phase", "done").put("semester", "S1").put("semesters", JSONArray().put(JSONArray().put("S1").put("Fall")))
            .put("datasets", JSONObject().put("timetable", tt).put("attendance", JSONObject().put("status", 500)).put("marks", JSONObject().put("error", "session_expired")))
        val out = VtopImport.build(payload.toString(), Instant.EPOCH) as SyncOutcome.Success
        assertEquals(2, out.import.courses.size)
        assertTrue(out.import.ok(Dataset.TIMETABLE)); assertFalse(out.import.ok(Dataset.ATTENDANCE)); assertFalse(out.import.ok(Dataset.MARKS))
        assertEquals("HTTP 500", out.import.statuses.single { it.dataset == Dataset.ATTENDANCE }.detail)
        val expired = VtopImport.build(JSONObject().put("phase", "error").put("reason", "session_expired").toString(), Instant.EPOCH)
        assertTrue(expired is SyncOutcome.Failure)
        assertTrue(VtopImport.build("<html>", Instant.EPOCH) is SyncOutcome.Failure)
    }
    @Test fun twelveHourTimesNormalise() {
        assertEquals(LocalTime.of(13, 0), VtopParser.time("01:00 PM")); assertEquals(LocalTime.of(0, 30), VtopParser.time("12:30 AM"))
        assertEquals(LocalTime.of(12, 50), VtopParser.time("12:50")); assertEquals(LocalTime.of(19, 25), VtopParser.time("07:25"))
    }
}
