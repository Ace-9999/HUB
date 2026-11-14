package dev.personalhub.feature.college

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.personalhub.college.api.*
import dev.personalhub.core.AppTime
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class CollegeUi(val loaded: Boolean = false, val snapshot: CollegeSnapshot = CollegeSnapshot(), val target: Int = 75,
    val scenario: Map<String, String> = emptyMap())

data class NextClass(val meeting: ClassMeeting, val at: LocalDateTime, val course: Course?)

enum class TomorrowKind { WORKING, HOLIDAY, EXAM, FREE }
sealed interface DayStatus {
    data class Holiday(val label: String?) : DayStatus
    /** Calendar marks an exam (CAT/FAT…) today: no regular classes. */
    data class ExamDay(val label: String?) : DayStatus
    data class NoClasses(val label: String?) : DayStatus
    data class Next(val next: NextClass, val tomorrow: Boolean) : DayStatus
    data class DoneToday(val tomorrow: TomorrowKind) : DayStatus
    data class TomorrowOff(val holiday: Boolean, val label: String?, val exam: Boolean = false) : DayStatus
}

/** Pure day classification; [eveningFrom] is when tomorrow's plan replaces today's (7 pm). */
fun dayStatus(s: CollegeSnapshot, now: LocalDateTime, eveningFrom: java.time.LocalTime = java.time.LocalTime.of(19, 0)): DayStatus {
    val today = now.toLocalDate(); val tomorrow = today.plusDays(1)
    fun holiday(d: java.time.LocalDate) = s.calendarOn(d).firstOrNull { it.kind == CalendarKind.HOLIDAY }
    fun exam(d: java.time.LocalDate) = s.calendarOn(d).firstOrNull { it.kind == CalendarKind.EXAM }
        ?: s.exams.firstOrNull { it.date == d }?.let { CalendarDay(d, it.examType.ifBlank { "Exam" }, CalendarKind.EXAM) }
    fun classes(d: java.time.LocalDate) = if (s.noClassesOn(d)) emptyList() else s.meetingsOn(d.dayOfWeek).sortedBy { it.start }
    val tomorrowKind = when { holiday(tomorrow) != null -> TomorrowKind.HOLIDAY; exam(tomorrow) != null -> TomorrowKind.EXAM
        classes(tomorrow).isEmpty() -> TomorrowKind.FREE; else -> TomorrowKind.WORKING }
    val evening = !now.toLocalTime().isBefore(eveningFrom)
    fun tomorrowStatus(): DayStatus = when (tomorrowKind) {
        TomorrowKind.WORKING -> classes(tomorrow).first().let { m -> DayStatus.Next(NextClass(m, tomorrow.atTime(m.start), s.course(m.courseCode, m.component)), true) }
        TomorrowKind.HOLIDAY -> DayStatus.TomorrowOff(true, holiday(tomorrow)?.label)
        TomorrowKind.EXAM -> DayStatus.TomorrowOff(false, exam(tomorrow)?.label, exam = true)
        TomorrowKind.FREE -> DayStatus.TomorrowOff(false, null)
    }
    holiday(today)?.let { return if (evening && tomorrowKind != TomorrowKind.FREE) tomorrowStatus() else DayStatus.Holiday(it.label) }
    exam(today)?.let { return if (evening && tomorrowKind != TomorrowKind.FREE) tomorrowStatus() else DayStatus.ExamDay(it.label) }
    val todays = classes(today)
    if (todays.isEmpty()) return if (evening && tomorrowKind != TomorrowKind.FREE) tomorrowStatus()
        else DayStatus.NoClasses(s.relevantCalendarOn(today).firstOrNull()?.label)
    todays.firstOrNull { it.end.isAfter(now.toLocalTime()) }?.let { m -> return DayStatus.Next(NextClass(m, today.atTime(m.start), s.course(m.courseCode, m.component)), false) }
    return if (evening) tomorrowStatus() else DayStatus.DoneToday(tomorrowKind)
}

class CollegeViewModel(private val repository: CollegeRepository, val time: AppTime) : ViewModel() {
    private val target = MutableStateFlow(75)
    private val scenario = MutableStateFlow<Map<String, String>>(emptyMap())
    val state: StateFlow<CollegeUi> = combine(repository.snapshots, target, scenario) { s, t, sc -> CollegeUi(true, s, t, sc) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollegeUi())

    fun setTarget(value: Int) { target.value = value.coerceIn(50, 95) }
    fun setGrade(courseKey: String, grade: String?) { scenario.update { if (grade == null) it - courseKey else it + (courseKey to grade) } }
    fun resetScenario() { scenario.value = emptyMap() }
    fun disconnect(clearSession: () -> Unit) = viewModelScope.launch { repository.clear(); clearSession() }

    /** Upcoming meetings in time order across the next week (skipping portal holidays), for the swipeable strip. */
    fun upcoming(s: CollegeSnapshot, limit: Int = 20): List<NextClass> {
        val now = LocalDateTime.now(time.clock.withZone(time.zone))
        val out = mutableListOf<NextClass>()
        for (offset in 0L..7L) {
            val date = now.toLocalDate().plusDays(offset)
            if (s.noClassesOn(date)) continue
            s.meetingsOn(date.dayOfWeek).filter { offset > 0 || it.end.isAfter(now.toLocalTime()) }
                .forEach { m -> out += NextClass(m, date.atTime(m.start), s.course(m.courseCode, m.component)) }
            if (out.size >= limit) break
        }
        return out.take(limit)
    }

    fun dayStatus(s: CollegeSnapshot): DayStatus = dayStatus(s, LocalDateTime.now(time.clock.withZone(time.zone)))

    fun nextClass(s: CollegeSnapshot): NextClass? {
        val now = LocalDateTime.now(time.clock.withZone(time.zone))
        for (offset in 0L..7L) {
            val date = now.toLocalDate().plusDays(offset)
            if (s.noClassesOn(date)) continue
            val hit = s.meetingsOn(date.dayOfWeek).firstOrNull { offset > 0 || it.end.isAfter(now.toLocalTime()) } ?: continue
            return NextClass(hit, date.atTime(hit.start), s.course(hit.courseCode, hit.component))
        }
        return null
    }
}

/** Courses eligible for grade scenarios: current semester, positive credits, one entry per course component. */
fun scenarioCourses(s: CollegeSnapshot): List<Course> = s.courses.filter { (it.credits ?: 0.0) > 0 }.distinctBy { it.key }

private val CLOCK = Regex("""(\d{1,2}):(\d{2})\s*([AaPp][Mm])""")
private fun clock(m: MatchResult): java.time.LocalTime {
    val h = m.groupValues[1].toInt() % 12 + if (m.groupValues[3].uppercase() == "PM") 12 else 0
    return java.time.LocalTime.of(h.coerceIn(0, 23), m.groupValues[2].toInt().coerceIn(0, 59))
}
/** Start/end of a paper from its exam time text ("09:30 AM - 12:30 PM"); unknown times span the whole day. */
fun examStart(e: ExamSlot): LocalDateTime? = e.date?.let { d -> CLOCK.find(e.examTime.orEmpty())?.let { d.atTime(clock(it)) } ?: d.atStartOfDay() }
fun examEnd(e: ExamSlot): LocalDateTime? = e.date?.let { d -> CLOCK.findAll(e.examTime.orEmpty()).lastOrNull()?.let { d.atTime(clock(it)) } ?: d.atTime(23, 59) }

/** The next paper that hasn't finished yet (within [days] days); a paper that ended earlier today is skipped. */
fun nextExam(s: CollegeSnapshot, now: LocalDateTime, days: Long = 14): ExamSlot? =
    s.exams.filter { e -> val end = examEnd(e); end != null && end.isAfter(now) && !e.date!!.isAfter(now.toLocalDate().plusDays(days)) }.minByOrNull { examStart(it)!! }

/** When to alert for an upcoming class and which class it is. */
data class ClassAlert(val at: LocalDateTime, val next: NextClass)

/**
 * Next class alert strictly after [now]: 10 minutes before the class starts, unless another class is still running at that
 * moment (back-to-back), in which case the alert fires as soon as that class ends. Portal holidays are skipped.
 */
fun nextClassAlert(s: CollegeSnapshot, now: LocalDateTime, lead: java.time.Duration = java.time.Duration.ofMinutes(10)): ClassAlert? {
    for (offset in 0L..8L) {
        val date = now.toLocalDate().plusDays(offset)
        if (s.noClassesOn(date)) continue
        val day = s.meetingsOn(date.dayOfWeek).sortedBy { it.start }
        for (m in day) {
            val start = date.atTime(m.start)
            var at = start.minus(lead)
            val running = day.filter { it !== m && it.start.isBefore(m.start) && date.atTime(it.end).isAfter(at) }.maxOfOrNull { it.end }
            if (running != null) at = minOf(date.atTime(running), start)
            if (at.isAfter(now)) return ClassAlert(at, NextClass(m, start, s.course(m.courseCode, m.component)))
        }
    }
    return null
}
