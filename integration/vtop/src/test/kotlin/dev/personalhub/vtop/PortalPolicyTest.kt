package dev.personalhub.vtop

import org.junit.Assert.*
import org.junit.Test

class PortalPolicyTest {
    @Test fun onlyExactSecurePortalOriginIsPermitted() {
        assertTrue(PortalPolicy.permits(PortalPolicy.landing))
        assertTrue(PortalPolicy.permits("https://vtopcc.vit.ac.in:443/vtop/login"))
        listOf("http://vtopcc.vit.ac.in/vtop/", "https://vtopcc.vit.ac.in.evil.test/", "https://vtopcc.vit.ac.in@evil.test/", "file:///data/data/a", "javascript:alert(1)", "https://vtopcc.vit.ac.in:8443/").forEach { assertFalse(it, PortalPolicy.permits(it)) }
    }
    @Test fun structuralCountsNeverPretendToBeValidatedAcademicRecords() {
        assertFalse(ProbeResult(PortalPage.TABLE_CANDIDATE, 3, 0).validatedDataset)
    }
    @Test(expected = IllegalArgumentException::class) fun oversizedCountsAreRejected() { ProbeResult(PortalPage.UNKNOWN, 101, 0) }
}
