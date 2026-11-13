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
    @Test fun nestedAttemptTablesDoNotDuplicateHistory() {
        val main = table(listOf("Course Code", "Course Title", "Credits", "Grade"), listOf("ZZZ0101L", "Intro", "3", "A"))
        val nested = table(listOf("Course Code", "Grade", "Exam Month"), listOf("ZZZ0101L", "A", "Nov"), context = listOf("1", "ZZZ0101L"))
        assertEquals(1, VtopParser.grades(listOf(main, nested)).first.rows.size)
    }
    @Test fun examScheduleUsesGroupRowsForExamType() {
        val head = listOf("S.No.", "Course Code", "Course Title", "Course Type", "Class ID", "Slot", "Exam Date", "Exam Session", "Reporting Time", "Exam Time", "Venue", "Seat Location", "Seat No.")
        val t = table(head, List(13) { "FAT" },
            listOf("1", "ZZZ1001L", "Synthetic Systems", "Embedded Theory", "C1", "A1+TA1", "24-Nov-2026", "FN", "09:15 AM", "09:30 AM - 12:30 PM", "XB1-101", "Hall", "12"),
            listOf("2", "ZZZ2002L", "Other", "Theory Only", "C2", "B1", "", "", "", "", "", "", ""))
        val rows = VtopParser.exams(listOf(t)).rows
        assertEquals(2, rows.size); assertEquals("FAT", rows[0].examType)
        assertEquals(java.time.LocalDate.of(2026, 11, 24), rows[0].date); assertEquals("12", rows[0].seatNumber)
        assertNull(rows[1].date); assertNull(rows[1].venue)
    }
    @Test fun calendarCellsBecomeLabelledDaysAndBlankDaysAreSkipped() {
        val t = table(listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"),
            listOf("", "", "1", "2 Instructional Day", "3 Holiday - Synthetic Festival", "4 CAT - II", "5"), context = listOf("01-NOV-2026"))
        val days = VtopParser.calendar(listOf(t)).rows
        assertEquals(3, days.size)
        assertEquals(CalendarKind.HOLIDAY, days.single { it.date.dayOfMonth == 3 }.kind)
        assertEquals(CalendarKind.EXAM, days.single { it.date.dayOfMonth == 4 }.kind)
        assertEquals(java.time.Month.NOVEMBER, days[0].date.month)
    }
    @Test fun portalDateFormatsParse() {
        assertEquals(java.time.LocalDate.of(2026, 7, 1), VtopParser.date("01-JUL-2026"))
        assertEquals(java.time.LocalDate.of(2026, 11, 4), VtopParser.date("4-Nov-2026")); assertNull(VtopParser.date("soon"))
    }
    @Test fun gradeViewRowsAndDetailRangesParse() {
        val view = table(listOf("Sl.No.", "Course Code", "Course Title", "Course Type", "Credits", "Grading Type", "Grand Total", "Grade", "View Mark"),
            listOf("1", "ZZZ0101L", "Intro", "Theory Only", "3", "RG", "71.5", "A", "View"))
        val c = VtopParser.gradeView(listOf(view)).single()
        assertEquals("A", c.grade); assertEquals(71.5, c.grandTotal!!, 0.0); assertEquals(3.0, c.credits!!, 0.0)
        val comps = table(listOf("Sl.No.", "Mark Title", "Max. Mark", "Weightage %", "Status", "Scored Mark", "Weightage Mark"),
            listOf("1", "CAT1", "50", "15", "Present", "40", "12"), listOf("2", "FAT", "100", "40", "Present", "70", "28"))
        val rangesRow = table(listOf("Grade", "S", "A", "B", "C", "D", "E", "F"), listOf("Range", ">= 80", "72 - 79", "65 - 71", "58 - 64", "50 - 57", "45 - 49", "< 45"))
        val (parts, ranges) = VtopParser.gradeDetail("ZZZ0101L", listOf(comps, rangesRow))
        assertEquals(listOf("CAT1", "FAT"), parts.map { it.title }); assertEquals("ZZZ0101L", parts[0].courseCode)
        assertEquals("72 - 79", ranges.single { it.grade == "A" }.range); assertEquals(7, ranges.size)
        val vertical = table(listOf("Grade", "Range"), listOf("S", "86 - 100"), listOf("A", "78 - 85"), listOf("B", "70 - 77"))
        assertEquals("78 - 85", VtopParser.gradeDetail("X", listOf(vertical)).second.single { it.grade == "A" }.range)
    }
    @Test fun lightSyncMarksHeavyDatasetsSkippedNotFailed() {
        val payload = JSONObject().put("phase", "done").put("semester", "S1").put("skipped", JSONArray().put("calendar").put("grades").put("results"))
            .put("datasets", JSONObject().put("timetable", JSONObject().put("status", 200).put("tables", JSONArray())))
        val out = VtopImport.build(payload.toString(), Instant.EPOCH) as SyncOutcome.Success
        listOf(Dataset.CALENDAR, Dataset.GRADES, Dataset.RESULTS).forEach { d -> assertEquals(ImportState.SKIPPED, out.import.statuses.single { it.dataset == d }.state) }
        assertFalse(out.import.ok(Dataset.GRADES)) // repository keeps the cached rows
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
