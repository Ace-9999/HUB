package dev.personalhub.app

import dev.personalhub.college.api.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

// Demo flavor only: invented courses for UI review. Clearly labelled; never offered in the connected app.
object DemoData {
    fun college(now: Instant): CollegeImport {
        val sem = Semester("DEMO", "Sample semester (demo data)")
        val courses = listOf(
            Course("DEM101L", "Signals Made Simple", "Embedded Theory", 3.0, "A1+TA1", "AB1-101", "Sample Faculty", "D1"),
            Course("DEM101P", "Signals Made Simple Lab", "Embedded Lab", 1.0, "L1+L2", "AB1-LAB", "Sample Faculty", "D2"),
            Course("DEM202L", "Practical Databases", "Theory Only", 3.0, "B1+TB1", "AB2-204", "Sample Faculty", "D3"),
            Course("DEM303L", "Networks Today", "Theory Only", 4.0, "C1+TC1", "AB1-305", "Sample Faculty", "D4"),
            Course("DEM404J", "Mini Project", "Project", 2.0, null, null, "Sample Faculty", "D5"),
        )
        fun m(code: String, type: String, d: DayOfWeek, h: Int, min: Int, len: Long, slot: String, venue: String) =
            ClassMeeting(code, type, d, LocalTime.of(h, min), LocalTime.of(h, min).plusMinutes(len), slot, venue)
        val meetings = listOf(
            m("DEM101L", "ETH", DayOfWeek.MONDAY, 8, 0, 50, "A1", "AB1-101"), m("DEM202L", "TH", DayOfWeek.MONDAY, 9, 50, 50, "B1", "AB2-204"),
            m("DEM101P", "ELA", DayOfWeek.MONDAY, 14, 0, 100, "L31+L32", "AB1-LAB"), m("DEM303L", "TH", DayOfWeek.TUESDAY, 8, 55, 50, "C1", "AB1-305"),
            m("DEM202L", "TH", DayOfWeek.TUESDAY, 11, 40, 50, "TB1", "AB2-204"), m("DEM101L", "ETH", DayOfWeek.WEDNESDAY, 10, 45, 50, "TA1", "AB1-101"),
            m("DEM303L", "TH", DayOfWeek.WEDNESDAY, 15, 0, 50, "TC1", "AB1-305"), m("DEM303L", "TH", DayOfWeek.THURSDAY, 8, 0, 50, "C1", "AB1-305"),
            m("DEM202L", "TH", DayOfWeek.FRIDAY, 9, 50, 50, "B1", "AB2-204"), m("DEM101P", "ELA", DayOfWeek.FRIDAY, 14, 0, 100, "L33+L34", "AB1-LAB"),
        )
        val attendance = listOf(
            AttendanceRecord("DEM101L", "Signals Made Simple", "Embedded Theory", "A1+TA1", 27, 30, 90),
            AttendanceRecord("DEM101P", "Signals Made Simple Lab", "Embedded Lab", "L1+L2", 9, 12, 75),
            AttendanceRecord("DEM202L", "Practical Databases", "Theory Only", "B1+TB1", 20, 28, 72),
            AttendanceRecord("DEM303L", "Networks Today", "Theory Only", "C1+TC1", 31, 40, 78),
        )
        val marks = listOf(
            MarkComponent("DEM101L", "Embedded Theory", "CAT-1", 50.0, 15.0, 41.0, 12.3, "Present"),
            MarkComponent("DEM101L", "Embedded Theory", "Quiz 1", 10.0, 10.0, 8.0, 8.0, "Present"),
            MarkComponent("DEM101L", "Embedded Theory", "CAT-2", 50.0, 15.0, null, null, null),
            MarkComponent("DEM202L", "Theory Only", "CAT-1", 50.0, 15.0, 36.0, 10.8, "Present"),
        )
        val grades = listOf(GradeRecord("DEM001L", "Foundations Sample", "Theory Only", 3.0, "A", "Nov-2025"),
            GradeRecord("DEM002P", "Foundations Lab Sample", "Lab Only", 1.0, "S", "Nov-2025"), GradeRecord("DEM003L", "Calculus Sample", "Theory Only", 4.0, "B", "May-2026"))
        val statuses = listOf(DatasetStatus(Dataset.TIMETABLE, ImportState.OK, 5, "5 courses · 10 weekly classes (sample)"),
            DatasetStatus(Dataset.ATTENDANCE, ImportState.OK, 4, "4 course components (sample)"), DatasetStatus(Dataset.MARKS, ImportState.OK, 4, "4 assessment rows (sample)"),
            DatasetStatus(Dataset.GRADES, ImportState.OK, 3, "3 graded attempts (sample)"))
        val today = java.time.LocalDate.now()
        val exams = listOf(ExamSlot("FAT", "DEM101L", "Signals Made Simple", "Embedded Theory", today.plusDays(5), "FN", "09:15 AM", "09:30 AM - 12:30 PM", "AB1-101", "Hall A", "12"),
            ExamSlot("FAT", "DEM202L", "Practical Databases", "Theory Only", today.plusDays(8), "AN", "01:45 PM", "02:00 PM - 05:00 PM", "AB2-204", "Hall B", "31"))
        val calendar = listOf(CalendarDay(today.plusDays(2), "Holiday - Sample Festival", CalendarKind.HOLIDAY),
            CalendarDay(today.plusDays(1), "Instructional Day", CalendarKind.INSTRUCTIONAL), CalendarDay(today.plusDays(3), "Instructional Day", CalendarKind.INSTRUCTIONAL))
        val all = statuses + listOf(DatasetStatus(Dataset.EXAMS, ImportState.OK, 2, "2 exams (sample)"), DatasetStatus(Dataset.CALENDAR, ImportState.OK, 3, "3 labelled days (sample)"))
        return CollegeImport(sem, listOf(sem), now, courses, meetings, attendance, marks, grades, CgpaSummary(48.0, 48.0, 8.64), all, exams, calendar)
    }
}
