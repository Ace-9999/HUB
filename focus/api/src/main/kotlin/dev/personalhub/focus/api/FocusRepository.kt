package dev.personalhub.focus.api

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

// Commands acknowledge only after the owned database transaction commits.
interface FocusRepository {
    val snapshots: Flow<FocusSnapshot>
    suspend fun saveTask(draft: TaskDraft): String
    suspend fun completeTask(id: String, day: LocalDate, completed: Boolean)
    suspend fun trashTask(id: String, deleted: Boolean = true)
    suspend fun saveHabit(draft: HabitDraft): String
    suspend fun trashHabit(id: String, deleted: Boolean = true)
    suspend fun putHabitEntry(habitId: String, day: LocalDate, value: Double?, note: String? = null)
    suspend fun saveMood(draft: MoodDraft): String
    suspend fun trashMood(id: String, deleted: Boolean = true)
}

// ---------------------------------------------------------------- Google Tasks sync contract
/** A one-off local task as seen by sync. [dirty] = changed locally since the last successful push. */
data class LocalSyncTask(val localId: String, val googleId: String?, val list: String?, val title: String, val due: LocalDate?,
    val completed: Boolean, val deleted: Boolean, val updatedAt: java.time.Instant, val dirty: Boolean)
/** A task from the Google Tasks API (due is date-only there). */
data class RemoteTask(val googleId: String, val list: String, val title: String, val due: LocalDate?, val completed: Boolean,
    val deleted: Boolean, val updated: java.time.Instant)

/** Last-writer-wins: a remote change applies unless the local copy has unpushed edits newer than it. */
fun remoteWins(local: LocalSyncTask?, remote: RemoteTask): Boolean = local == null || !local.dirty || !local.updatedAt.isAfter(remote.updated)

/** Focus-owned persistence operations used only by the Google Tasks adapter. */
interface TaskSyncStore {
    suspend fun syncCandidates(): List<LocalSyncTask>
    suspend fun applyRemote(remote: RemoteTask)
    suspend fun linkPushed(localId: String, googleId: String, list: String, remoteUpdated: java.time.Instant)
    suspend fun unlink(localId: String)
    suspend fun lastSync(): java.time.Instant?
    suspend fun setLastSync(at: java.time.Instant)
}

/** Google Tasks connection state shown on the Tasks page. */
sealed interface GoogleSyncState {
    data object Off : GoogleSyncState
    data object NeedsConsent : GoogleSyncState
    data object Syncing : GoogleSyncState
    data class Synced(val at: java.time.Instant, val pulled: Int, val pushed: Int) : GoogleSyncState
    data class Failed(val reason: String) : GoogleSyncState
}
