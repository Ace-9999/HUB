package dev.personalhub.feature.college

import dev.personalhub.college.api.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

// Synthetic timetable: Monday 08:00-08:50 and 14:00-14:50, Tuesday 09:00; nothing Wednesday.
class DayStatusTest {
    private val mon = LocalDate.of(2026, 11, 16) // a Monday
    private fun m(day: DayOfWeek, h: Int) = ClassMeeting("ZZZ1001L", "ETH", day, LocalTime.of(h, 0), LocalTime.of(h, 50), "A1", "XB1")
    private val base = CollegeSnapshot(Semester("S", "Fall"), listOf(Semester("S", "Fall")), Instant.EPOCH,
        meetings = listOf(m(DayOfWeek.MONDAY, 8), m(DayOfWeek.MONDAY, 14), m(DayOfWeek.TUESDAY, 9)))
    private fun at(d: LocalDate, h: Int, min: Int = 0) = d.atTime(h, min)

    @Test fun workingDayShowsNextClassThenDoneUntilSevenThenTomorrow() {
        val a = dayStatus(base, at(mon, 7)) as DayStatus.Next
        assertEquals(8, a.next.meeting.start.hour); assertFalse(a.tomorrow)
        assertEquals(14, (dayStatus(base, at(mon, 8, 55)) as DayStatus.Next).next.meeting.start.hour)
        assertEquals(DayStatus.DoneToday(TomorrowKind.WORKING), dayStatus(base, at(mon, 16)))
        val evening = dayStatus(base, at(mon, 19)) as DayStatus.Next
        assertTrue(evening.tomorrow); assertEquals(DayOfWeek.TUESDAY, evening.next.at.dayOfWeek)
    }
    @Test fun holidayFromPortalCalendarHidesClasses() {
        val s = base.copy(calendar = listOf(CalendarDay(mon, "Holiday - Synthetic", CalendarKind.HOLIDAY)))
        assertEquals(DayStatus.Holiday("Holiday - Synthetic"), dayStatus(s, at(mon, 9)))
        assertTrue((dayStatus(s, at(mon, 20)) as DayStatus.Next).tomorrow) // evening: tomorrow is a working day
    }
    @Test fun eveningBeforeHolidayOrFreeDayDoesNotShowAClass() {
        val tue = mon.plusDays(1)
        assertEquals(DayStatus.TomorrowOff(false, null), dayStatus(base, at(tue, 20)))     // Wednesday has no classes
        val s = base.copy(calendar = listOf(CalendarDay(tue, "Holiday - X", CalendarKind.HOLIDAY)))
        assertEquals(DayStatus.TomorrowOff(true, "Holiday - X"), dayStatus(s, at(mon, 19, 30)))
        assertEquals(DayStatus.DoneToday(TomorrowKind.HOLIDAY), dayStatus(s, at(mon, 15)))
    }
    @Test fun examDayFromCalendarHasNoClassesAndIsAnnouncedTheEveningBefore() {
        val s = base.copy(calendar = listOf(CalendarDay(mon, "CAT - I", CalendarKind.EXAM)))
        assertEquals(DayStatus.ExamDay("CAT - I"), dayStatus(s, at(mon, 9)))
        assertEquals(DayStatus.TomorrowOff(false, "CAT - I", exam = true), dayStatus(s, at(mon.minusDays(1), 20)))
        assertEquals(at(mon.plusDays(1), 8, 50), nextClassAlert(s, at(mon, 6))!!.at) // Monday's classes are skipped
    }
    @Test fun examScheduleAloneMakesItAnExamDay() {
        val s = base.copy(exams = listOf(ExamSlot("FAT", "ZZZ1001L", null, null, mon, "FN", null, "09:30 AM", "XB1", "R1C4", "12")))
        assertEquals(DayStatus.ExamDay("FAT"), dayStatus(s, at(mon, 9)))
        assertTrue(s.noClassesOn(mon))
    }
    @Test fun nextExamSkipsPapersThatAlreadyEnded() {
        val morning = ExamSlot("FAT", "ZZZ1001L", null, null, mon, "FN", null, "09:30 AM - 12:30 PM", "XB1", null, null)
        val later = ExamSlot("FAT", "ZZZ2002L", null, null, mon.plusDays(2), "AN", null, "02:00 PM - 05:00 PM", "XB1", null, null)
        val s = base.copy(exams = listOf(later, morning))
        assertEquals("ZZZ1001L", nextExam(s, at(mon, 12, 0))!!.courseCode)
        assertEquals("ZZZ2002L", nextExam(s, at(mon, 18, 0))!!.courseCode)
        assertNull(nextExam(s, at(mon.plusDays(2), 17, 1)))
    }
    @Test fun dayWithoutClassesOrLabelIsNoClasses() {
        assertEquals(DayStatus.NoClasses(null), dayStatus(base, at(mon.plusDays(2), 10)))
    }
    @Test fun classAlertTenMinutesBeforeOrRightAfterARunningClass() {
        val a = nextClassAlert(base, at(mon, 7))!!
        assertEquals(at(mon, 7, 50), a.at); assertEquals(8, a.next.meeting.start.hour)
        assertEquals(at(mon, 13, 50), nextClassAlert(base, at(mon, 7, 50))!!.at)
        // Back-to-back: a 13:00-13:55 class is still running at 13:50, so the 14:00 alert fires at 13:55.
        val busy = base.copy(meetings = base.meetings + ClassMeeting("ZZZ2002L", "ELA", DayOfWeek.MONDAY, LocalTime.of(13, 0), LocalTime.of(13, 55), "L1", "XB2"))
        assertEquals(at(mon, 12, 50), nextClassAlert(busy, at(mon, 9))!!.at)
        assertEquals(at(mon, 13, 55), nextClassAlert(busy, at(mon, 12, 50))!!.at)
        // Holiday tomorrow is skipped entirely: after Monday's last class the next alert is the following Monday.
        val hol = base.copy(calendar = listOf(CalendarDay(mon.plusDays(1), "Holiday - X", CalendarKind.HOLIDAY)))
        assertEquals(at(mon.plusDays(7), 7, 50), nextClassAlert(hol, at(mon, 15))!!.at)
    }
}
