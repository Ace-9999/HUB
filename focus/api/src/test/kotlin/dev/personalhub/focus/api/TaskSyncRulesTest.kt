package dev.personalhub.focus.api

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class TaskSyncRulesTest {
    private val t = Instant.parse("2026-11-13T10:00:00Z")
    private fun local(dirty: Boolean, at: Instant) = LocalSyncTask("l", "g", "list", "Local", null, false, false, at, dirty)
    private val remote = RemoteTask("g", "list", "Remote", null, true, false, t)
    @Test fun newRemoteTaskAlwaysApplies() = assertTrue(remoteWins(null, remote))
    @Test fun cleanLocalTakesRemoteChange() = assertTrue(remoteWins(local(false, t.plusSeconds(60)), remote))
    @Test fun newerUnpushedLocalEditIsKept() = assertFalse(remoteWins(local(true, t.plusSeconds(60)), remote))
    @Test fun olderUnpushedLocalEditLosesToNewerRemote() = assertTrue(remoteWins(local(true, t.minusSeconds(60)), remote))
}
