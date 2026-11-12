package dev.personalhub.core

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ContractsTest {
    @Test fun reportingDayUsesDeclaredZoneAcrossMidnight() {
        val time = AppTime(Clock.fixed(Instant.parse("2026-11-11T20:00:00Z"), ZoneOffset.UTC))
        assertEquals(LocalDate.parse("2026-11-12"), time.today())
    }
    @Test fun newSectionRegistersWithoutFeatureKnowledge() {
        val registry = SectionRegistry(listOf(Section("today", "Today"), Section("sample", "Sample")))
        assertEquals("sample", registry.resolve("sample").id)
        assertEquals("today", registry.resolve("removed").id)
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateRoutesAreRejected() {
        SectionRegistry(listOf(Section("a", "First"), Section("a", "Second")))
    }
}
