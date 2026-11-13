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
