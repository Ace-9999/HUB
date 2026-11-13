package dev.personalhub.college.api

import org.junit.Assert.*
import org.junit.Test

class CollegeRulesTest {
    @Test fun attendanceAtExactThresholdHasNoSkipsBeyondBoundary() {
        // 30/40 = 75%: one more absence drops below 75.
        assertEquals(AttendancePlan(75, 0, 0), attendancePlan(30, 40))
        // 33/40: can miss 4 (33/44 = 75%) but not 5.
        assertEquals(4, attendancePlan(33, 40).canSkip)
    }
    @Test fun belowTargetNeedsConsecutiveClasses() {
        // 20/30: need x with (20+x)/(30+x) >= .75 -> x = 10.
        val plan = attendancePlan(20, 30)
        assertEquals(10, plan.mustAttend); assertEquals(0, plan.canSkip)
    }
    @Test fun emptyAttendanceIsNotTreatedAsShortfall() { assertEquals(AttendancePlan(75, 0, 0), attendancePlan(0, 0)) }
    @Test(expected = IllegalArgumentException::class) fun attendedAboveTotalIsRejected() { AttendanceRecord("X", null, "TH", null, 5, 4, null) }
    @Test fun gpaIsCreditWeightedAndIgnoresNonLetterOutcomes() {
        val r = gpa(listOf(4.0 to "S", 3.0 to "B", 2.0 to "N"))
        assertEquals(7.0, r.credits, 0.0)
        assertEquals((40 + 24) / 7.0, r.gpa!!, 1e-9)
    }
    @Test fun scenarioRequiresOfficialDenominator() {
        assertNull(scenarioCgpa(8.5, null, listOf(3.0 to "A")))
        assertEquals((8.5 * 100 + 9 * 3) / 103, scenarioCgpa(8.5, 100.0, listOf(3.0 to "A"))!!, 1e-9)
        assertEquals(8.5, scenarioCgpa(8.5, 100.0, emptyList())!!, 0.0)
    }
    @Test fun componentLabelsNormalise() {
        assertEquals("THEORY", componentOf("Embedded Theory")); assertEquals("THEORY", componentOf("TH"))
        assertEquals("LAB", componentOf("ELA")); assertEquals("LAB", componentOf("Lab Only"))
        assertEquals(courseKey("BCSE302L", "ETH"), courseKey("BCSE302L", "Embedded Theory"))
    }
    @Test fun latestAttemptSupersedesEarlier() {
        val rows = listOf(GradeRecord("C1", "T", null, 3.0, "F", "Nov"), GradeRecord("C2", "T", null, 3.0, "A", "Nov"), GradeRecord("C1", "T", null, 3.0, "B", "May"))
        assertEquals(listOf("B", "A"), latestAttempts(rows).map { it.grade })
    }
}
