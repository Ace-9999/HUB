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
@Entity(tableName = "exams", primaryKeys = ["semesterId", "position"])
internal data class ExamRow(val semesterId: String, val position: Int, val examType: String, val courseCode: String, val courseTitle: String?, val courseType: String?,
    val date: String?, val session: String?, val reportingTime: String?, val examTime: String?, val venue: String?, val seatLocation: String?, val seatNumber: String?)
@Entity(tableName = "calendar_days", primaryKeys = ["semesterId", "position"])
internal data class CalendarRow(val semesterId: String, val position: Int, val date: String, val label: String, val kind: String)
@Entity(tableName = "result_courses", primaryKeys = ["semesterId", "position"])
internal data class ResultCourseRow(val semesterId: String, val position: Int, val semesterName: String, val semesterOrder: Int, val code: String, val title: String,
    val type: String?, val credits: Double?, val gradingType: String?, val grandTotal: Double?, val grade: String?, val semesterGpa: Double? = null)
@Entity(tableName = "result_components", primaryKeys = ["semesterId", "coursePosition", "position"])
internal data class ResultComponentRow(val semesterId: String, val coursePosition: Int, val position: Int, val title: String, val maxMark: Double?,
    val weightage: Double?, val scored: Double?, val weighted: Double?, val status: String?)
@Entity(tableName = "result_ranges", primaryKeys = ["semesterId", "coursePosition", "grade"])
internal data class ResultRangeRow(val semesterId: String, val coursePosition: Int, val grade: String, val range: String, val position: Int)
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
    @Query("SELECT * FROM exams WHERE semesterId = :s ORDER BY position") suspend fun exams(s: String): List<ExamRow>
    @Query("DELETE FROM exams WHERE semesterId = :s") suspend fun clearExams(s: String)
    @Insert suspend fun exams(rows: List<ExamRow>)
    @Query("SELECT * FROM calendar_days WHERE semesterId = :s ORDER BY position") suspend fun calendar(s: String): List<CalendarRow>
    @Query("DELETE FROM calendar_days WHERE semesterId = :s") suspend fun clearCalendar(s: String)
    @Insert suspend fun calendar(rows: List<CalendarRow>)
    @Query("SELECT * FROM result_courses ORDER BY semesterOrder, position") suspend fun resultCourses(): List<ResultCourseRow>
    @Query("SELECT * FROM result_components ORDER BY semesterId, coursePosition, position") suspend fun resultComponents(): List<ResultComponentRow>
    @Query("SELECT * FROM result_ranges ORDER BY semesterId, coursePosition, position") suspend fun resultRanges(): List<ResultRangeRow>
    @Query("DELETE FROM result_courses WHERE semesterId = :s") suspend fun clearResultCourses(s: String)
    @Query("DELETE FROM result_components WHERE semesterId = :s") suspend fun clearResultComponents(s: String)
    @Query("DELETE FROM result_ranges WHERE semesterId = :s") suspend fun clearResultRanges(s: String)
    @Insert suspend fun resultCourses(rows: List<ResultCourseRow>)
    @Insert suspend fun resultComponents(rows: List<ResultComponentRow>)
    @Insert suspend fun resultRanges(rows: List<ResultRangeRow>)
    @Query("DELETE FROM result_courses") suspend fun clearAllResultCourses()
    @Query("DELETE FROM result_components") suspend fun clearAllResultComponents()
    @Query("DELETE FROM result_ranges") suspend fun clearAllResultRanges()
    @Query("DELETE FROM exams") suspend fun clearAllExams()
    @Query("DELETE FROM calendar_days") suspend fun clearAllCalendar()
    @Query("DELETE FROM college_meta") suspend fun clearMeta()
    @Query("DELETE FROM dataset_status") suspend fun clearStatuses()
    @Query("DELETE FROM courses") suspend fun clearAllCourses()
    @Query("DELETE FROM meetings") suspend fun clearAllMeetings()
    @Query("DELETE FROM attendance") suspend fun clearAllAttendance()
    @Query("DELETE FROM marks") suspend fun clearAllMarks()
}

@Database(entities = [MetaRow::class, SemesterRow::class, CourseRow::class, MeetingRow::class, AttendanceRow::class, MarkRow::class, GradeRow::class, SummaryRow::class, StatusRow::class, ExamRow::class, CalendarRow::class, ResultCourseRow::class, ResultComponentRow::class, ResultRangeRow::class], version = 4, exportSchema = true)
internal abstract class CollegeDatabase : RoomDatabase() {
    abstract fun dao(): CollegeDao
    companion object {
        /** Additive: exam schedule and academic calendar tables; existing rows untouched. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `exams` (`semesterId` TEXT NOT NULL, `position` INTEGER NOT NULL, `examType` TEXT NOT NULL, `courseCode` TEXT NOT NULL, `courseTitle` TEXT, `courseType` TEXT, `date` TEXT, `session` TEXT, `reportingTime` TEXT, `examTime` TEXT, `venue` TEXT, `seatLocation` TEXT, `seatNumber` TEXT, PRIMARY KEY(`semesterId`, `position`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `calendar_days` (`semesterId` TEXT NOT NULL, `position` INTEGER NOT NULL, `date` TEXT NOT NULL, `label` TEXT NOT NULL, `kind` TEXT NOT NULL, PRIMARY KEY(`semesterId`, `position`))")
            }
        }
        /** Additive: per-semester results (Grade View), assessment components and grade ranges. */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `result_courses` (`semesterId` TEXT NOT NULL, `position` INTEGER NOT NULL, `semesterName` TEXT NOT NULL, `semesterOrder` INTEGER NOT NULL, `code` TEXT NOT NULL, `title` TEXT NOT NULL, `type` TEXT, `credits` REAL, `gradingType` TEXT, `grandTotal` REAL, `grade` TEXT, PRIMARY KEY(`semesterId`, `position`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `result_components` (`semesterId` TEXT NOT NULL, `coursePosition` INTEGER NOT NULL, `position` INTEGER NOT NULL, `title` TEXT NOT NULL, `maxMark` REAL, `weightage` REAL, `scored` REAL, `weighted` REAL, `status` TEXT, PRIMARY KEY(`semesterId`, `coursePosition`, `position`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `result_ranges` (`semesterId` TEXT NOT NULL, `coursePosition` INTEGER NOT NULL, `grade` TEXT NOT NULL, `range` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`semesterId`, `coursePosition`, `grade`))")
            }
        }
        /** Additive: the semester GPA as printed by the portal (stored on each result row of that semester). */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("ALTER TABLE `result_courses` ADD COLUMN `semesterGpa` REAL") }
        }
    }
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
                dao.exams(s).map { ExamSlot(it.examType, it.courseCode, it.courseTitle, it.courseType, it.date?.let(java.time.LocalDate::parse), it.session, it.reportingTime, it.examTime, it.venue, it.seatLocation, it.seatNumber) },
                dao.calendar(s).mapNotNull { r -> runCatching { CalendarDay(java.time.LocalDate.parse(r.date), r.label, CalendarKind.valueOf(r.kind)) }.getOrNull() },
                results(),
            )
        }
    }

    private suspend fun results(): List<SemesterResult> {
        val comps = dao.resultComponents().groupBy { it.semesterId to it.coursePosition }
        val ranges = dao.resultRanges().groupBy { it.semesterId to it.coursePosition }
        return dao.resultCourses().groupBy { it.semesterId }.values.map { rows ->
            val first = rows.first()
            SemesterResult(Semester(first.semesterId, first.semesterName), rows.map { r ->
                GradedCourse(r.code, r.title, r.type, r.credits, r.gradingType, r.grandTotal, r.grade,
                    comps[r.semesterId to r.position].orEmpty().map { MarkComponent(r.code, r.type, it.title, it.maxMark, it.weightage, it.scored, it.weighted, it.status) },
                    ranges[r.semesterId to r.position].orEmpty().map { GradeRange(it.grade, it.range) })
            }, rows.firstNotNullOfOrNull { it.semesterGpa })
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
        if (import.ok(Dataset.EXAMS)) { dao.clearExams(s); dao.exams(import.exams.mapIndexed { i, e -> ExamRow(s, i, e.examType, e.courseCode, e.courseTitle, e.courseType, e.date?.toString(), e.session, e.reportingTime, e.examTime, e.venue, e.seatLocation, e.seatNumber) }) }
        if (import.ok(Dataset.CALENDAR)) { dao.clearCalendar(s); dao.calendar(import.calendar.mapIndexed { i, d -> CalendarRow(s, i, d.date.toString(), d.label, d.kind.name) }) }
        if (import.ok(Dataset.RESULTS)) import.results.forEachIndexed { order, res ->
            val sid = res.semester.id
            dao.clearResultCourses(sid); dao.clearResultComponents(sid); dao.clearResultRanges(sid)
            dao.resultCourses(res.courses.mapIndexed { i, c -> ResultCourseRow(sid, i, res.semester.name, order, c.code, c.title, c.type, c.credits, c.gradingType, c.grandTotal, c.grade, res.portalGpa) })
            dao.resultComponents(res.courses.flatMapIndexed { i, c -> c.components.mapIndexed { j, m -> ResultComponentRow(sid, i, j, m.title, m.maxMark, m.weightage, m.scored, m.weighted, m.status) } })
            dao.resultRanges(res.courses.flatMapIndexed { i, c -> c.ranges.distinctBy { it.grade }.mapIndexed { j, g -> ResultRangeRow(sid, i, g.grade, g.range, j) } })
        }
        import.statuses.filter { it.state != ImportState.SKIPPED || dao.status(it.dataset.name) == null }.forEach { st ->
            val previous = dao.status(st.dataset.name)
            val success = st.state == ImportState.OK || st.state == ImportState.EMPTY
            dao.status(StatusRow(st.dataset.name, st.state.name, st.rows, st.detail, now, if (success) now else previous?.lastSuccessAt))
        }
        dao.meta(MetaRow(1, s, import.semester.name, now, (dao.meta()?.revision ?: 0) + 1))
    }

    override suspend fun clear() = db.withTransaction {
        dao.clearMeta(); dao.clearSemesters(); dao.clearAllCourses(); dao.clearAllMeetings(); dao.clearAllAttendance(); dao.clearAllMarks()
        dao.clearGrades(); dao.clearSummary(); dao.clearStatuses(); dao.clearAllExams(); dao.clearAllCalendar()
        dao.clearAllResultCourses(); dao.clearAllResultComponents(); dao.clearAllResultRanges()
    }

    override fun close() = db.close()

    companion object {
        fun open(context: Context, name: String = "college.db") = RoomCollegeRepository(Room.databaseBuilder(context, CollegeDatabase::class.java, name).addMigrations(CollegeDatabase.MIGRATION_1_2, CollegeDatabase.MIGRATION_2_3, CollegeDatabase.MIGRATION_3_4).build())
    }
}
