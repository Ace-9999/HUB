package dev.personalhub.googletasks

import dev.personalhub.focus.api.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

// Synthetic API payloads only; no network.
class GoogleTasksSyncTest {
    private val t = Instant.parse("2026-11-13T10:00:00Z")

    @Test fun parsesDateOnlyDueStatusAndDeletion() {
        val items = JSONArray()
            .put(JSONObject().put("id", "a").put("title", "Read").put("due", "2026-11-14T00:00:00.000Z").put("status", "needsAction").put("updated", t.toString()))
            .put(JSONObject().put("id", "b").put("title", "Done").put("status", "completed").put("hidden", true).put("updated", t.toString()))
            .put(JSONObject().put("id", "c").put("title", "Gone").put("deleted", true).put("updated", t.toString()))
            .put(JSONObject().put("title", "no id"))
        val out = TaskJson.parse("L", items)
        assertEquals(3, out.size)
        assertEquals(LocalDate.of(2026, 11, 14), out[0].due); assertFalse(out[0].completed)
        assertTrue(out[1].completed); assertFalse(out[1].deleted) // completed+hidden is not a deletion
        assertTrue(out[2].deleted)
    }
    @Test fun bodySendsDateOnlyDueAndStatus() {
        val b = TaskJson.body(LocalSyncTask("l", null, null, "X", LocalDate.of(2026, 11, 14), true, false, t, true))
        assertEquals("2026-11-14T00:00:00.000Z", b.getString("due")); assertEquals("completed", b.getString("status"))
        assertTrue(TaskJson.body(LocalSyncTask("l", null, null, "X", null, false, false, t, true)).isNull("due"))
    }
    @Test fun syncPullsThenPushesEachLocalChangeOnce() = runBlocking {
        val store = MemStore(listOf(
            LocalSyncTask("new", null, null, "New", null, false, false, t, false),
            LocalSyncTask("edited", "g2", "L", "Edited", null, true, false, t, true),
            LocalSyncTask("clean", "g3", "L", "Clean", null, false, false, t, false),
            LocalSyncTask("trashed", "g4", "L", "Trash", null, false, true, t, true),
            LocalSyncTask("neverSynced", null, null, "Gone", null, false, true, t, true),
            LocalSyncTask("missing", "g9", "L", "Deleted on server", null, false, false, t, true),
        ))
        val api = FakeApi(listOf(RemoteTask("r1", "L", "From Google", null, false, false, t)))
        val (pulled, pushed) = GoogleTasksSync(store, api).run(t)
        assertEquals(1, pulled); assertEquals(listOf("r1"), store.applied)
        assertEquals(listOf("insert new", "patch edited", "delete trashed", "patch missing", "insert missing"), api.calls)
        assertEquals(4, pushed)
        assertEquals("n-new", store.items.single { it.localId == "new" }.googleId)
        assertNull(store.items.single { it.localId == "trashed" }.googleId)
        assertFalse(store.items.single { it.localId == "neverSynced" }.dirty)
        assertEquals("n-missing", store.items.single { it.localId == "missing" }.googleId) // 404 on patch re-creates
        assertEquals(t, store.last)
        // second run is incremental: updatedMin passed, nothing left to push
        val again = GoogleTasksSync(store, api).run(t.plusSeconds(60))
        assertEquals(0, again.second); assertEquals(t.minusSeconds(120), api.lastUpdatedMin)
    }

    private class FakeApi(val remote: List<RemoteTask>) : TasksApi {
        val calls = mutableListOf<String>(); var lastUpdatedMin: Instant? = null
        override suspend fun lists() = listOf("L")
        override suspend fun tasks(list: String, updatedMin: Instant?): List<RemoteTask> { lastUpdatedMin = updatedMin; return if (updatedMin == null) remote else emptyList() }
        override suspend fun insert(list: String, t: LocalSyncTask): RemoteTask { calls += "insert ${t.localId}"; return RemoteTask("n-${t.localId}", "L", t.title, t.due, t.completed, false, t.updatedAt) }
        override suspend fun patch(list: String, id: String, t: LocalSyncTask): RemoteTask {
            calls += "patch ${t.localId}"; if (id == "g9") throw ApiException(404, "gone"); return RemoteTask(id, list, t.title, t.due, t.completed, false, t.updatedAt)
        }
        override suspend fun delete(list: String, id: String) { calls += "delete ${if (id == "g4") "trashed" else id}" }
    }

    private class MemStore(initial: List<LocalSyncTask>) : TaskSyncStore {
        val items = initial.toMutableList(); var last: Instant? = null; val applied = mutableListOf<String>()
        override suspend fun syncCandidates() = items.toList()
        override suspend fun applyRemote(remote: RemoteTask) { applied += remote.googleId }
        override suspend fun linkPushed(localId: String, googleId: String, list: String, remoteUpdated: Instant) { replace(localId) { it.copy(googleId = googleId, list = list, dirty = false) } }
        override suspend fun unlink(localId: String) { replace(localId) { it.copy(googleId = null, dirty = false) } }
        override suspend fun lastSync() = last
        override suspend fun setLastSync(at: Instant) { last = at }
        private fun replace(id: String, f: (LocalSyncTask) -> LocalSyncTask) { val i = items.indexOfFirst { it.localId == id }; items[i] = f(items[i]) }
    }
}
