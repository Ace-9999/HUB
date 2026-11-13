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
