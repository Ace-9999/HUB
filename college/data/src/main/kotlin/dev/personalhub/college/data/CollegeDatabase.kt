package dev.personalhub.college.data

import android.content.Context
import androidx.room.*
import androidx.room.withTransaction
import dev.personalhub.college.api.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

// College owns its database; semester-scoped rows carry their semester id. Public API never exposes rows.
@Entity(tableName = "college_meta") internal data class MetaRow(@PrimaryKey val id: Int = 1, val semesterId: String, val semesterName: String, val syncedAt: Long, val revision: Long)
@Entity(tableName = "semesters") internal data class SemesterRow(@PrimaryKey val id: String, val name: String, val position: Int)
@Entity(tableName = "courses", primaryKeys = ["semesterId", "position"])
internal data class CourseRow(val semesterId: String, val position: Int, val code: String, val title: String, val type: String, val credits: Double?,
    val slot: String?, val venue: String?, val faculty: String?, val classId: String?)
@Entity(tableName = "meetings", primaryKeys = ["semesterId", "position"])
internal data class MeetingRow(val semesterId: String, val position: Int, val courseCode: String, val component: String, val day: Int, val start: String, val end: String, val slot: String, val venue: String?)
@Entity(tableName = "attendance", primaryKeys = ["semesterId", "position"])
internal data class AttendanceRow(val semesterId: String, val position: Int, val courseCode: String, val courseTitle: String?, val type: String, val slot: String?, val attended: Int, val total: Int, val portalPercent: Int?)
@Entity(tableName = "marks", primaryKeys = ["semesterId", "position"])
internal data class MarkRow(val semesterId: String, val position: Int, val courseCode: String, val courseType: String?, val title: String, val maxMark: Double?, val weightage: Double?, val scored: Double?, val weighted: Double?, val status: String?)
@Entity(tableName = "grades") internal data class GradeRow(@PrimaryKey val position: Int, val courseCode: String, val title: String, val type: String?, val credits: Double?, val grade: String, val examMonth: String?)
@Entity(tableName = "cgpa") internal data class SummaryRow(@PrimaryKey val id: Int = 1, val creditsRegistered: Double?, val creditsEarned: Double?, val cgpa: Double?)
/** Last attempt outcome per dataset; [lastSuccessAt] is the age of the rows currently shown. */
@Entity(tableName = "dataset_status") internal data class StatusRow(@PrimaryKey val dataset: String, val state: String, val rows: Int, val detail: String, val attemptedAt: Long, val lastSuccessAt: Long?)

@Dao internal interface CollegeDao {
    @Query("SELECT * FROM college_meta WHERE id = 1") fun observeMeta(): Flow<MetaRow?>
    @Query("SELECT * FROM college_meta WHERE id = 1") suspend fun meta(): MetaRow?
    @Upsert suspend fun meta(row: MetaRow)
    @Query("SELECT * FROM semesters ORDER BY position") suspend fun semesters(): List<SemesterRow>
    @Query("DELETE FROM semesters") suspend fun clearSemesters()
    @Insert suspend fun semesters(rows: List<SemesterRow>)
    @Query("SELECT * FROM courses WHERE semesterId = :s ORDER BY position") suspend fun courses(s: String): List<CourseRow>
    @Query("DELETE FROM courses WHERE semesterId = :s") suspend fun clearCourses(s: String)
    @Insert suspend fun courses(rows: List<CourseRow>)
    @Query("SELECT * FROM meetings WHERE semesterId = :s ORDER BY position") suspend fun meetings(s: String): List<MeetingRow>
    @Query("DELETE FROM meetings WHERE semesterId = :s") suspend fun clearMeetings(s: String)
    @Insert suspend fun meetings(rows: List<MeetingRow>)
    @Query("SELECT * FROM attendance WHERE semesterId = :s ORDER BY position") suspend fun attendance(s: String): List<AttendanceRow>
    @Query("DELETE FROM attendance WHERE semesterId = :s") suspend fun clearAttendance(s: String)
    @Insert suspend fun attendance(rows: List<AttendanceRow>)
    @Query("SELECT * FROM marks WHERE semesterId = :s ORDER BY position") suspend fun marks(s: String): List<MarkRow>
    @Query("DELETE FROM marks WHERE semesterId = :s") suspend fun clearMarks(s: String)
    @Insert suspend fun marks(rows: List<MarkRow>)
    @Query("SELECT * FROM grades ORDER BY position") suspend fun grades(): List<GradeRow>
    @Query("DELETE FROM grades") suspend fun clearGrades()
    @Insert suspend fun grades(rows: List<GradeRow>)
    @Query("SELECT * FROM cgpa WHERE id = 1") suspend fun summary(): SummaryRow?
    @Upsert suspend fun summary(row: SummaryRow)
    @Query("DELETE FROM cgpa") suspend fun clearSummary()
    @Query("SELECT * FROM dataset_status") suspend fun statuses(): List<StatusRow>
    @Query("SELECT * FROM dataset_status WHERE dataset = :d") suspend fun status(d: String): StatusRow?
    @Upsert suspend fun status(row: StatusRow)
    @Query("DELETE FROM college_meta") suspend fun clearMeta()
    @Query("DELETE FROM dataset_status") suspend fun clearStatuses()
    @Query("DELETE FROM courses") suspend fun clearAllCourses()
    @Query("DELETE FROM meetings") suspend fun clearAllMeetings()
    @Query("DELETE FROM attendance") suspend fun clearAllAttendance()
    @Query("DELETE FROM marks") suspend fun clearAllMarks()
}

@Database(entities = [MetaRow::class, SemesterRow::class, CourseRow::class, MeetingRow::class, AttendanceRow::class, MarkRow::class, GradeRow::class, SummaryRow::class, StatusRow::class], version = 1, exportSchema = true)
internal abstract class CollegeDatabase : RoomDatabase() {
    abstract fun dao(): CollegeDao
}

class RoomCollegeRepository private constructor(private val db: CollegeDatabase) : CollegeRepository, java.io.Closeable {
    private val dao get() = db.dao()

    override val snapshots: Flow<CollegeSnapshot> = dao.observeMeta().map { meta ->
        if (meta == null) return@map CollegeSnapshot()
        db.withTransaction {
            val s = meta.semesterId
            CollegeSnapshot(
                Semester(s, meta.semesterName), dao.semesters().map { Semester(it.id, it.name) }, Instant.ofEpochMilli(meta.syncedAt),
                dao.courses(s).map { Course(it.code, it.title, it.type, it.credits, it.slot, it.venue, it.faculty, it.classId) },
                dao.meetings(s).map { ClassMeeting(it.courseCode, it.component, DayOfWeek.of(it.day), LocalTime.parse(it.start), LocalTime.parse(it.end), it.slot, it.venue) },
                dao.attendance(s).map { AttendanceRecord(it.courseCode, it.courseTitle, it.type, it.slot, it.attended, it.total, it.portalPercent) },
                dao.marks(s).map { MarkComponent(it.courseCode, it.courseType, it.title, it.maxMark, it.weightage, it.scored, it.weighted, it.status) },
                dao.grades().map { GradeRecord(it.courseCode, it.title, it.type, it.credits, it.grade, it.examMonth) },
                dao.summary()?.let { CgpaSummary(it.creditsRegistered, it.creditsEarned, it.cgpa) },
                dao.statuses().mapNotNull { r -> runCatching { DatasetStatus(Dataset.valueOf(r.dataset), ImportState.valueOf(r.state), r.rows, r.detail) }.getOrNull() },
            )
        }
    }

    /** Last successful time for a dataset, so stale rows can be labelled with their true age. */
    suspend fun lastSuccess(dataset: Dataset): Instant? = dao.status(dataset.name)?.lastSuccessAt?.let(Instant::ofEpochMilli)

    override suspend fun save(import: CollegeImport) = db.withTransaction {
        val s = import.semester.id
        val now = import.syncedAt.toEpochMilli()
        dao.clearSemesters(); dao.semesters(import.semesters.mapIndexed { i, x -> SemesterRow(x.id, x.name, i) })
        if (import.ok(Dataset.TIMETABLE)) {
            dao.clearCourses(s); dao.courses(import.courses.mapIndexed { i, c -> CourseRow(s, i, c.code, c.title, c.type, c.credits, c.slot, c.venue, c.faculty, c.classId) })
            dao.clearMeetings(s); dao.meetings(import.meetings.mapIndexed { i, m -> MeetingRow(s, i, m.courseCode, m.component, m.day.value, m.start.toString(), m.end.toString(), m.slot, m.venue) })
        }
        if (import.ok(Dataset.ATTENDANCE)) { dao.clearAttendance(s); dao.attendance(import.attendance.mapIndexed { i, a -> AttendanceRow(s, i, a.courseCode, a.courseTitle, a.type, a.slot, a.attended, a.total, a.portalPercent) }) }
        if (import.ok(Dataset.MARKS)) { dao.clearMarks(s); dao.marks(import.marks.mapIndexed { i, m -> MarkRow(s, i, m.courseCode, m.courseType, m.title, m.maxMark, m.weightage, m.scored, m.weighted, m.status) }) }
        if (import.ok(Dataset.GRADES)) {
            dao.clearGrades(); dao.grades(import.grades.mapIndexed { i, g -> GradeRow(i, g.courseCode, g.title, g.type, g.credits, g.grade, g.examMonth) })
            dao.clearSummary(); import.summary?.let { dao.summary(SummaryRow(1, it.creditsRegistered, it.creditsEarned, it.cgpa)) }
        }
        import.statuses.forEach { st ->
            val previous = dao.status(st.dataset.name)
            val success = st.state == ImportState.OK || st.state == ImportState.EMPTY
            dao.status(StatusRow(st.dataset.name, st.state.name, st.rows, st.detail, now, if (success) now else previous?.lastSuccessAt))
        }
        dao.meta(MetaRow(1, s, import.semester.name, now, (dao.meta()?.revision ?: 0) + 1))
    }

    override suspend fun clear() = db.withTransaction {
        dao.clearMeta(); dao.clearSemesters(); dao.clearAllCourses(); dao.clearAllMeetings(); dao.clearAllAttendance(); dao.clearAllMarks()
        dao.clearGrades(); dao.clearSummary(); dao.clearStatuses()
    }

    override fun close() = db.close()

    companion object {
        fun open(context: Context, name: String = "college.db") = RoomCollegeRepository(Room.databaseBuilder(context, CollegeDatabase::class.java, name).build())
    }
}
