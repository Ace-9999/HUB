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
        fun comp(code: String, vararg s: Double) = listOf("CAT1" to 50.0, "CAT2" to 50.0, "DA" to 30.0, "FAT" to 100.0).zip(s.toList()).map { (m, v) ->
            MarkComponent(code, null, m.first, m.second, if (m.first == "FAT") 40.0 else if (m.first == "DA") 30.0 else 15.0, v, v / m.second * (if (m.first == "FAT") 40.0 else if (m.first == "DA") 30.0 else 15.0), "Present") }
        val ranges = listOf("S" to ">= 80", "A" to "72 - 79", "B" to "65 - 71", "C" to "58 - 64", "D" to "50 - 57", "E" to "45 - 49", "F" to "< 45").map { GradeRange(it.first, it.second) }
        val results = listOf(SemesterResult(Semester("DEMO-W", "Winter Semester 2025-26 (sample)"), listOf(
            GradedCourse("DEM001L", "Foundations Sample", "Theory Only", 3.0, "RG", 76.4, "A", comp("DEM001L", 41.0, 38.0, 27.0, 72.0), ranges),
            GradedCourse("DEM003L", "Calculus Sample", "Theory Only", 4.0, "RG", 68.1, "B", comp("DEM003L", 33.0, 36.0, 25.0, 66.0), ranges))))
        val withResults = all + DatasetStatus(Dataset.RESULTS, ImportState.OK, 1, "1 semester (sample)")
        return CollegeImport(sem, listOf(sem), now, courses, meetings, attendance, marks, grades, CgpaSummary(48.0, 48.0, 8.64), withResults, exams, calendar, results)
    }

    /** One labelled sample week: a Push routine and four finished workouts (demo flavor only). */
    fun training(now: Instant): Pair<dev.personalhub.training.api.Routine, List<dev.personalhub.training.api.Workout>> {
        fun s(w: Double?, r: Int) = dev.personalhub.training.api.WorkoutSet(dev.personalhub.training.api.SetType.NORMAL, w, r, null, null, null, true)
        fun ex(id: String, vararg sets: dev.personalhub.training.api.WorkoutSet) = dev.personalhub.training.api.WorkoutExercise("seed:$id", sets.toList(), 90)
        val push = dev.personalhub.training.api.Routine("demo-push", "Push (sample)", listOf(
            dev.personalhub.training.api.RoutineItem("seed:bench_bb", 3, 6, 70.0, 120), dev.personalhub.training.api.RoutineItem("seed:incline_db", 3, 10, 22.5, 90),
            dev.personalhub.training.api.RoutineItem("seed:lateral_raise", 3, 15, 8.0, 60), dev.personalhub.training.api.RoutineItem("seed:tricep_pushdown", 3, 12, 25.0, 60)), null, false, now)
        fun w(id: String, name: String, daysAgo: Long, vararg e: dev.personalhub.training.api.WorkoutExercise): dev.personalhub.training.api.Workout {
            val start = now.minus(java.time.Duration.ofDays(daysAgo)).minusSeconds(3600)
            return dev.personalhub.training.api.Workout(id, name, null, start, start.plusSeconds(3300), e.toList())
        }
        return push to listOf(
            w("demo-w1", "Push (sample)", 6, ex("bench_bb", s(70.0, 6), s(70.0, 6), s(70.0, 5)), ex("incline_db", s(22.5, 10), s(22.5, 9)), ex("tricep_pushdown", s(25.0, 12), s(25.0, 12))),
            w("demo-w2", "Pull (sample)", 4, ex("lat_pulldown", s(55.0, 10), s(55.0, 9)), ex("bb_row", s(60.0, 8), s(60.0, 8)), ex("db_curl", s(12.0, 12), s(12.0, 10))),
            w("demo-w3", "Legs (sample)", 2, ex("squat_bb", s(90.0, 5), s(90.0, 5), s(90.0, 5)), ex("rdl", s(80.0, 8), s(80.0, 8)), ex("calf_raise", s(60.0, 15), s(60.0, 15))),
            w("demo-w4", "Push (sample)", 0, ex("bench_bb", s(72.5, 6), s(72.5, 5), s(72.5, 5)), ex("lateral_raise", s(8.0, 15), s(8.0, 14)), ex("pushup", s(null, 20))),
        )
    }
}
