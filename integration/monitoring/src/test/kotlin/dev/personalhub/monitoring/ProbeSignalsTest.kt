package dev.personalhub.monitoring

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ProbeSignalsTest {
    @Before fun reset() { ProbeControl.reset() }
    @Test fun grantAloneDoesNotEnableEventCollection() {
        ProbeControl.event("com.reddit.frontpage", true, 10)
        assertEquals(0L, ProbeControl.signals.value.events)
    }
    @Test fun unrelatedPackagesAndContentAreExcluded() {
        ProbeControl.optedIn = true
        ProbeControl.event("com.example.passwordmanager", true, 5)
        ProbeControl.event("com.reddit.frontpage", true, 10)
        assertEquals(1L, ProbeControl.signals.value.events)
        assertEquals(1L, ProbeControl.signals.value.scrolls)
        assertEquals("com.reddit.frontpage", ProbeControl.signals.value.lastPackage)
    }
    @Test(expected = IllegalArgumentException::class) fun cannotBlanketRedirectBrowser() { ProbeControl.redirectPackage = "com.android.chrome" }
}
