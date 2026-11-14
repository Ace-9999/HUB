package dev.personalhub.college.api

import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import kotlin.math.ceil
import kotlin.math.floor

// Stable academic domain models. No Room, WebView, portal HTML or presentation styling.
data class Semester(val id: String, val name: String)

data class Course(val code: String, val title: String, val type: String, val credits: Double?, val slot: String?,
    val venue: String?, val faculty: String?, val classId: String?) {
    val key: String get() = courseKey(code, type)
}

data class ClassMeeting(val courseCode: String, val component: String, val day: DayOfWeek, val start: LocalTime, val end: LocalTime,
    val slot: String, val venue: String?) {
    init { require(end > start) { "Meeting must end after it starts." } }
}

data class AttendanceRecord(val courseCode: String, val courseTitle: String?, val type: String, val slot: String?,
    val attended: Int, val total: Int, val portalPercent: Int?) {
    init { require(attended in 0..total) { "Attended classes cannot exceed total." } }
    val key: String get() = courseKey(courseCode, type)
    /** Raw ratio from counts; the portal's own percentage (which may round up) is shown separately. */
    val percent: Double? get() = if (total == 0) null else attended * 100.0 / total
}

data class MarkComponent(val courseCode: String, val courseType: String?, val title: String, val maxMark: Double?, val weightage: Double?,
    val scored: Double?, val weighted: Double?, val status: String?)

data class GradeRecord(val courseCode: String, val title: String, val type: String?, val credits: Double?, val grade: String, val examMonth: String?)

/** One exam sitting from Examination → Exam Schedule. Unknown fields stay null. */
data class ExamSlot(val examType: String, val courseCode: String, val courseTitle: String?, val courseType: String?, val date: java.time.LocalDate?,
    val session: String?, val reportingTime: String?, val examTime: String?, val venue: String?, val seatLocation: String?, val seatNumber: String?)

enum class CalendarKind { INSTRUCTIONAL, HOLIDAY, EXAM, OTHER }
/** One labelled day from Academics → Academic Calendar (portal source; never inferred). */
data class CalendarDay(val date: java.time.LocalDate, val label: String, val kind: CalendarKind)

fun calendarKind(label: String): CalendarKind {
    val l = label.lowercase()
    return when {
        Regex("""holiday|vacation|break|leave""").containsMatchIn(l) -> CalendarKind.HOLIDAY
        Regex("""\b(cat|fat|exam|assessment|test)""").containsMatchIn(l) -> CalendarKind.EXAM
        l.contains("instructional") -> CalendarKind.INSTRUCTIONAL
        else -> CalendarKind.OTHER
    }
}

/** Grade range for one letter in one course, as published (e.g. "A" -> "65 - 72"). */
data class GradeRange(val grade: String, val range: String)
/** One graded course from Examination -> Grade View, with its assessment breakdown and ranges. */
data class GradedCourse(val code: String, val title: String, val type: String?, val credits: Double?, val gradingType: String?,
    val grandTotal: Double?, val grade: String?, val components: List<MarkComponent> = emptyList(), val ranges: List<GradeRange> = emptyList())
/** One semester's Grade View. [portalGpa] is the GPA exactly as VTOP prints it; the app never computes a semester GPA. */
data class SemesterResult(val semester: Semester, val courses: List<GradedCourse>, val portalGpa: Double? = null) {
    val credits: Double get() = courses.sumOf { it.credits ?: 0.0 }
}

data class CgpaSummary(val creditsRegistered: Double?, val creditsEarned: Double?, val cgpa: Double?)

enum class Dataset(val label: String) { TIMETABLE("Timetable"), ATTENDANCE("Attendance"), MARKS("Marks"), GRADES("Grade history"), EXAMS("Exam schedule"), CALENDAR("Academic calendar"), RESULTS("Semester results") }
enum class ImportState { OK, EMPTY, FAILED, SKIPPED }
data class DatasetStatus(val dataset: Dataset, val state: ImportState, val rows: Int, val detail: String)

/** One validated acquisition. Datasets that failed keep their previously saved rows (see CollegeRepository.save). */
data class CollegeImport(
    val semester: Semester, val semesters: List<Semester>, val syncedAt: Instant,
    val courses: List<Course> = emptyList(), val meetings: List<ClassMeeting> = emptyList(),
    val attendance: List<AttendanceRecord> = emptyList(), val marks: List<MarkComponent> = emptyList(),
    val grades: List<GradeRecord> = emptyList(), val summary: CgpaSummary? = null,
    val statuses: List<DatasetStatus> = emptyList(),
    val exams: List<ExamSlot> = emptyList(), val calendar: List<CalendarDay> = emptyList(),
    val results: List<SemesterResult> = emptyList(),
) {
    fun ok(dataset: Dataset) = statuses.any { it.dataset == dataset && it.state in setOf(ImportState.OK, ImportState.EMPTY) }
}

data class CollegeSnapshot(
    val semester: Semester? = null, val semesters: List<Semester> = emptyList(), val syncedAt: Instant? = null,
    val courses: List<Course> = emptyList(), val meetings: List<ClassMeeting> = emptyList(),
    val attendance: List<AttendanceRecord> = emptyList(), val marks: List<MarkComponent> = emptyList(),
    val grades: List<GradeRecord> = emptyList(), val summary: CgpaSummary? = null, val statuses: List<DatasetStatus> = emptyList(),
    val exams: List<ExamSlot> = emptyList(), val calendar: List<CalendarDay> = emptyList(),
    val results: List<SemesterResult> = emptyList(),
) {
    fun calendarOn(day: java.time.LocalDate) = calendar.filter { it.date == day }
    fun examsOn(day: java.time.LocalDate) = exams.filter { it.date == day }
    val connected: Boolean get() = syncedAt != null
    fun meetingsOn(day: DayOfWeek) = meetings.filter { it.day == day }.sortedBy { it.start }
    fun course(code: String, component: String? = null): Course? =
        courses.firstOrNull { it.code == code && (component == null || componentOf(it.type) == componentOf(component)) } ?: courses.firstOrNull { it.code == code }
}

interface CollegeRepository {
    val snapshots: Flow<CollegeSnapshot>
    /** Atomically replaces each successfully imported dataset; failed datasets keep their cached rows. */
    suspend fun save(import: CollegeImport)
    suspend fun clear()
    /** Restores a full snapshot (clear + import with the original sync time and statuses). */
    suspend fun replaceAll(s: CollegeSnapshot) {
        clear()
        val sem = s.semester ?: return
        save(CollegeImport(sem, s.semesters, s.syncedAt ?: java.time.Instant.EPOCH, s.courses, s.meetings, s.attendance, s.marks, s.grades, s.summary,
            Dataset.entries.map { d -> s.statuses.firstOrNull { it.dataset == d }?.takeIf { it.state == ImportState.OK || it.state == ImportState.EMPTY }
                ?: DatasetStatus(d, ImportState.OK, 0, "restored from backup") }, s.exams, s.calendar, s.results))
    }
}

fun courseKey(code: String, type: String) = "$code|${componentOf(type)}"
/** Normalises portal type labels (TH, ETH, Embedded Theory, LO, ELA, Lab Only…) to theory/lab/project/other. */
fun componentOf(type: String): String {
    val t = type.uppercase().replace(" ", "")
    return when {
        t.contains("LAB") || t == "LO" || t == "ELA" || t == "LA" -> "LAB"
        t.contains("PROJECT") || t == "EPJ" || t == "PJT" || t == "PJ" -> "PROJECT"
        t.contains("THEORY") || t == "TH" || t == "ETH" || t == "SS" || t.contains("SOFTSKILL") -> "THEORY"
        else -> t.ifEmpty { "OTHER" }
    }
}

// ---------------------------------------------------------------- attendance
data class AttendancePlan(val target: Int, val canSkip: Int, val mustAttend: Int)
/** Classes that can be missed while staying at/above target, or consecutive classes needed to reach it. */
fun attendancePlan(attended: Int, total: Int, targetPercent: Int = 75): AttendancePlan {
    require(targetPercent in 1..99)
    val t = targetPercent / 100.0
    if (total == 0) return AttendancePlan(targetPercent, 0, 0)
    val mustAttend = if (attended >= t * total) 0 else ceil((t * total - attended) / (1 - t) - 1e-9).toInt()
    val canSkip = if (attended < t * total) 0 else floor(attended / t - total + 1e-9).toInt()
    return AttendancePlan(targetPercent, canSkip, mustAttend)
}

// ---------------------------------------------------------------- grades
/** VIT 10-point letter scale. N/W/U-type outcomes are not grade points and are excluded. */
val gradePoints: Map<String, Int> = linkedMapOf("S" to 10, "A" to 9, "B" to 8, "C" to 7, "D" to 6, "E" to 5, "F" to 0)
val scenarioGrades: List<String> = listOf("F", "E", "D", "C", "B", "A", "S")

data class GpaResult(val gpa: Double?, val credits: Double)
/** Credit-weighted GPA over courses with a known letter and positive credits. */
fun gpa(items: List<Pair<Double, String>>): GpaResult {
    val valid = items.filter { it.first > 0 && it.second in gradePoints }
    val credits = valid.sumOf { it.first }
    return GpaResult(if (credits == 0.0) null else valid.sumOf { it.first * gradePoints.getValue(it.second) } / credits, credits)
}

/**
 * Scenario CGPA: combines the official CGPA over its credit base with hypothetical grades for current courses.
 * The base must be the official denominator; null when it is unknown, so no precise projection is shown.
 */
fun scenarioCgpa(officialCgpa: Double?, baseCredits: Double?, scenario: List<Pair<Double, String>>): Double? {
    val s = gpa(scenario)
    if (officialCgpa == null || baseCredits == null || baseCredits < 0) return null
    if (s.gpa == null) return officialCgpa
    return (officialCgpa * baseCredits + s.gpa * s.credits) / (baseCredits + s.credits)
}

/** Latest attempt per course code in portal order (later rows supersede earlier attempts). */
fun latestAttempts(grades: List<GradeRecord>): List<GradeRecord> = grades.groupBy { it.courseCode }.map { it.value.last() }

/**
 * Marks that appeared (or changed score) in [after] compared with what was saved before, for the same semester only.
 * The first sync of a semester returns nothing, so connecting never floods the inbox with old marks.
 */
/**
 * Calendar entries worth showing for [date]: instructional-day entries for other programmes are dropped when the calendar
 * has "General Semester" entries (only those matter to the owner); duplicates are removed. Holidays, exams and events stay.
 */
fun CollegeSnapshot.relevantCalendarOn(date: java.time.LocalDate): List<CalendarDay> {
    val general = calendar.any { it.kind == CalendarKind.INSTRUCTIONAL && it.label.contains("general", ignoreCase = true) }
    return calendarOn(date).filter { d -> d.kind != CalendarKind.INSTRUCTIONAL || !general || d.label.contains("general", ignoreCase = true) }.distinctBy { it.label.lowercase() }
}

/** Holidays and exam days (calendar CAT/FAT label, or any paper in the exam schedule) have no regular classes. */
fun CollegeSnapshot.noClassesOn(date: java.time.LocalDate): Boolean =
    calendarOn(date).any { it.kind == CalendarKind.HOLIDAY || it.kind == CalendarKind.EXAM } || exams.any { it.date == date }

fun newMarks(before: CollegeSnapshot, after: CollegeImport): List<MarkComponent> {
    if (before.syncedAt == null || before.semester?.id != after.semester.id || before.marks.isEmpty()) return emptyList()
    val old = before.marks.associateBy { Triple(it.courseCode, it.courseType, it.title) }
    return after.marks.filter { m -> m.scored != null && old[Triple(m.courseCode, m.courseType, m.title)]?.scored != m.scored }
}
