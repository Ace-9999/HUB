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
            if (s.calendarOn(date).any { it.kind == CalendarKind.HOLIDAY }) continue
            s.meetingsOn(date.dayOfWeek).filter { offset > 0 || it.end.isAfter(now.toLocalTime()) }
                .forEach { m -> out += NextClass(m, date.atTime(m.start), s.course(m.courseCode, m.component)) }
            if (out.size >= limit) break
        }
        return out.take(limit)
    }

    fun nextClass(s: CollegeSnapshot): NextClass? {
        val now = LocalDateTime.now(time.clock.withZone(time.zone))
        for (offset in 0L..7L) {
            val date = now.toLocalDate().plusDays(offset)
            val hit = s.meetingsOn(date.dayOfWeek).firstOrNull { offset > 0 || it.end.isAfter(now.toLocalTime()) } ?: continue
            return NextClass(hit, date.atTime(hit.start), s.course(hit.courseCode, hit.component))
        }
        return null
    }
}

/** Courses eligible for grade scenarios: current semester, positive credits, one entry per course component. */
fun scenarioCourses(s: CollegeSnapshot): List<Course> = s.courses.filter { (it.credits ?: 0.0) > 0 }.distinctBy { it.key }
