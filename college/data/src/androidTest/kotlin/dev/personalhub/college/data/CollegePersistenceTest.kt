package dev.personalhub.college.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.personalhub.college.api.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

// Real SQLite: a failed dataset keeps its last good rows; semester switch scopes rows; reopen and clear.
class CollegePersistenceTest {
    @get:org.junit.Rule val helper = androidx.room.testing.MigrationTestHelper(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(), CollegeDatabase::class.java)

    @Test fun schemaOneRowsSurviveAdditiveMigrationAndNewDatasetsPersist() = runBlocking<Unit> {
        val name = "test-${UUID.randomUUID()}.db"
        try {
            helper.createDatabase(name, 1).apply {
                execSQL("INSERT INTO college_meta VALUES (1, 'S1', 'Synthetic', 1000, 1)")
                execSQL("INSERT INTO attendance VALUES ('S1', 0, 'ZZZ1001L', 'Synthetic', 'TH', 'A1', 20, 30, NULL)"); close()
            }
            helper.runMigrationsAndValidate(name, 2, true, CollegeDatabase.MIGRATION_1_2).close()
            val repo = RoomCollegeRepository.open(context, name)
            try {
                assertEquals(20, repo.snapshots.first().attendance.single().attended)
                repo.save(import(s1, 2000, null, listOf(ok(Dataset.EXAMS), ok(Dataset.CALENDAR))).copy(
                    exams = listOf(ExamSlot("FAT", "ZZZ1001L", null, null, java.time.LocalDate.of(2026, 11, 24), "FN", null, null, "XB1-101", null, "12")),
                    calendar = listOf(CalendarDay(java.time.LocalDate.of(2026, 11, 3), "Holiday - Synthetic", CalendarKind.HOLIDAY))))
                val snap = repo.snapshots.first()
                assertEquals("12", snap.examsOn(java.time.LocalDate.of(2026, 11, 24)).single().seatNumber)
                assertEquals(CalendarKind.HOLIDAY, snap.calendarOn(java.time.LocalDate.of(2026, 11, 3)).single().kind)
                assertEquals(20, snap.attendance.single().attended) // attendance not re-imported: cache kept
            } finally { repo.close() }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun schemaTwoMigratesAndSemesterResultsRoundTrip() = runBlocking<Unit> {
        val name = "test-${UUID.randomUUID()}.db"
        try {
            helper.createDatabase(name, 2).apply { execSQL("INSERT INTO college_meta VALUES (1, 'S1', 'Synthetic', 1000, 1)"); close() }
            helper.runMigrationsAndValidate(name, 3, true, CollegeDatabase.MIGRATION_2_3).close()
            val repo = RoomCollegeRepository.open(context, name)
            try {
                val course = GradedCourse("ZZZ0101L", "Intro", "Theory Only", 3.0, "RG", 71.5, "A",
                    listOf(MarkComponent("ZZZ0101L", null, "CAT1", 50.0, 15.0, 40.0, 12.0, null), MarkComponent("ZZZ0101L", null, "FAT", 100.0, 40.0, null, null, null)),
                    listOf(GradeRange("S", ">= 80"), GradeRange("A", "72 - 79")))
                repo.save(import(s1, 2000, null, listOf(ok(Dataset.RESULTS))).copy(results = listOf(SemesterResult(Semester("W25", "Synthetic Winter 2025-26"), listOf(course)))))
                val r = repo.snapshots.first().results.single()
                assertEquals("Synthetic Winter 2025-26", r.semester.name)
                val c = r.courses.single()
                assertEquals(listOf("CAT1", "FAT"), c.components.map { it.title }); assertNull(c.components[1].scored)
                assertEquals("72 - 79", c.ranges.single { it.grade == "A" }.range); assertEquals(9.0, r.gpa.gpa!!, 0.0)
            } finally { repo.close() }
        } finally { context.deleteDatabase(name) }
    }
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val s1 = Semester("S1", "Synthetic Fall"); private val s2 = Semester("S2", "Synthetic Winter")
    private fun import(sem: Semester, at: Long, attendance: Int?, statuses: List<DatasetStatus>) = CollegeImport(sem, listOf(s1, s2), Instant.ofEpochMilli(at),
        courses = listOf(Course("ZZZ1001L", "Synthetic", "Embedded Theory", 3.0, "A1", "XB1-101", null, null)),
        meetings = listOf(ClassMeeting("ZZZ1001L", "ETH", DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(8, 50), "A1", "XB1-101")),
        attendance = attendance?.let { listOf(AttendanceRecord("ZZZ1001L", "Synthetic", "Embedded Theory", "A1", it, 30, null)) }.orEmpty(),
        grades = listOf(GradeRecord("ZZZ0001L", "Old", null, 3.0, "A", null)), summary = CgpaSummary(40.0, 40.0, 8.5), statuses = statuses)
    private fun ok(d: Dataset) = DatasetStatus(d, ImportState.OK, 1, "ok")

    @Test fun failedDatasetKeepsCachedRowsAndSurvivesReopen() = runBlocking<Unit> {
        val name = "test-${UUID.randomUUID()}.db"
        try {
            var repo = RoomCollegeRepository.open(context, name)
            repo.save(import(s1, 1000, 20, Dataset.entries.map(::ok)))
            repo.save(import(s1, 2000, null, listOf(ok(Dataset.TIMETABLE), DatasetStatus(Dataset.ATTENDANCE, ImportState.FAILED, 0, "HTTP 500"))))
            assertEquals(Instant.ofEpochMilli(1000), repo.lastSuccess(Dataset.ATTENDANCE))
            repo.close(); repo = RoomCollegeRepository.open(context, name)
            try {
                val snap = repo.snapshots.first()
                assertEquals(20, snap.attendance.single().attended)
                assertEquals(ImportState.FAILED, snap.statuses.single { it.dataset == Dataset.ATTENDANCE }.state)
                assertEquals(1, snap.meetingsOn(DayOfWeek.MONDAY).size); assertEquals(8.5, snap.summary!!.cgpa!!, 0.0)
                assertEquals(2, snap.semesters.size)
            } finally { repo.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun semesterSwitchShowsOnlyThatSemesterAndClearRemovesEverything() = runBlocking<Unit> {
        val name = "test-${UUID.randomUUID()}.db"
        val repo = RoomCollegeRepository.open(context, name)
        try {
            repo.save(import(s1, 1000, 25, Dataset.entries.map(::ok)))
            repo.save(import(s2, 2000, null, listOf(DatasetStatus(Dataset.TIMETABLE, ImportState.FAILED, 0, "x"), DatasetStatus(Dataset.ATTENDANCE, ImportState.FAILED, 0, "x"))))
            val snap = repo.snapshots.first()
            assertEquals("S2", snap.semester!!.id); assertTrue(snap.attendance.isEmpty()); assertTrue(snap.courses.isEmpty())
            repo.clear()
            assertFalse(repo.snapshots.first().connected)
        } finally { repo.close(); context.deleteDatabase(name) }
    }
}
