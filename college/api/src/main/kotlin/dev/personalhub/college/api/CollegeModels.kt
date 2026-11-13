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

data class CgpaSummary(val creditsRegistered: Double?, val creditsEarned: Double?, val cgpa: Double?)

enum class Dataset(val label: String) { TIMETABLE("Timetable"), ATTENDANCE("Attendance"), MARKS("Marks"), GRADES("Grade history") }
enum class ImportState { OK, EMPTY, FAILED, SKIPPED }
data class DatasetStatus(val dataset: Dataset, val state: ImportState, val rows: Int, val detail: String)

/** One validated acquisition. Datasets that failed keep their previously saved rows (see CollegeRepository.save). */
data class CollegeImport(
    val semester: Semester, val semesters: List<Semester>, val syncedAt: Instant,
    val courses: List<Course> = emptyList(), val meetings: List<ClassMeeting> = emptyList(),
    val attendance: List<AttendanceRecord> = emptyList(), val marks: List<MarkComponent> = emptyList(),
    val grades: List<GradeRecord> = emptyList(), val summary: CgpaSummary? = null,
    val statuses: List<DatasetStatus> = emptyList(),
) {
    fun ok(dataset: Dataset) = statuses.any { it.dataset == dataset && it.state in setOf(ImportState.OK, ImportState.EMPTY) }
}

data class CollegeSnapshot(
    val semester: Semester? = null, val semesters: List<Semester> = emptyList(), val syncedAt: Instant? = null,
    val courses: List<Course> = emptyList(), val meetings: List<ClassMeeting> = emptyList(),
    val attendance: List<AttendanceRecord> = emptyList(), val marks: List<MarkComponent> = emptyList(),
    val grades: List<GradeRecord> = emptyList(), val summary: CgpaSummary? = null, val statuses: List<DatasetStatus> = emptyList(),
) {
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
