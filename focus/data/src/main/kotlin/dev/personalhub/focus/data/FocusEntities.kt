package dev.personalhub.focus.data

import androidx.room.*

// Domain-owned persistence rows; public API never exposes Room entities.
@Entity(tableName = "tasks")
internal data class TaskRow(@PrimaryKey val id: String, val title: String, val firstDay: String, val priority: String,
    val dueTime: String?, val recurrence: String, val carryForward: Boolean, val createdAt: Long, val updatedAt: Long, val deleted: Boolean = false,
    val googleId: String? = null, val googleList: String? = null, @ColumnInfo(defaultValue = "0") val dirty: Boolean = false,
    @ColumnInfo(defaultValue = "0") val undated: Boolean = false)
@Entity(tableName = "sync_state") internal data class SyncStateRow(@PrimaryKey val key: String, val value: String)
@Entity(tableName = "task_occurrences", primaryKeys = ["taskId", "day"], foreignKeys = [ForeignKey(entity = TaskRow::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)])
internal data class OccurrenceRow(val taskId: String, val day: String, val completed: Boolean, val updatedAt: Long)
@Entity(tableName = "habits")
internal data class HabitRow(@PrimaryKey val id: String, val label: String, val unit: String, val direction: String,
    val target: Double, val sensitive: Boolean, val createdAt: Long, val updatedAt: Long, val deleted: Boolean = false)
@Entity(tableName = "habit_entries", primaryKeys = ["habitId", "day"], foreignKeys = [ForeignKey(entity = HabitRow::class, parentColumns = ["id"], childColumns = ["habitId"], onDelete = ForeignKey.CASCADE)])
internal data class HabitEntryRow(val habitId: String, val day: String, val value: Double, val explicitZero: Boolean, val note: String?, val updatedAt: Long)
@Entity(tableName = "moods")
internal data class MoodRow(@PrimaryKey val id: String, val code: String, val occurredAt: Long, val originalZone: String,
    val createdAt: Long, val updatedAt: Long, val note: String?, val revision: Long, val deleted: Boolean = false)

@Dao internal interface FocusDao {
    @Query("SELECT * FROM tasks WHERE deleted = 0") suspend fun tasks(): List<TaskRow>
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun task(id: String): TaskRow?
    @Query("SELECT * FROM tasks WHERE recurrence = 'NONE'") suspend fun oneOffTasks(): List<TaskRow>
    @Query("SELECT * FROM tasks WHERE googleId = :googleId LIMIT 1") suspend fun taskByGoogle(googleId: String): TaskRow?
    @Query("SELECT value FROM sync_state WHERE `key` = :key") suspend fun syncValue(key: String): String?
    @Upsert suspend fun syncValue(row: SyncStateRow)
    @Upsert suspend fun task(row: TaskRow)
    @Query("SELECT * FROM task_occurrences") suspend fun occurrences(): List<OccurrenceRow>
    @Upsert suspend fun occurrence(row: OccurrenceRow)
    @Query("SELECT * FROM habits WHERE deleted = 0") suspend fun habits(): List<HabitRow>
    @Query("SELECT * FROM habits WHERE id = :id") suspend fun habit(id: String): HabitRow?
    @Upsert suspend fun habit(row: HabitRow)
    @Query("SELECT * FROM habit_entries") suspend fun entries(): List<HabitEntryRow>
    @Upsert suspend fun entry(row: HabitEntryRow)
    @Query("DELETE FROM habit_entries WHERE habitId = :id AND day = :day") suspend fun removeEntry(id: String, day: String)
    @Query("SELECT * FROM moods WHERE deleted = 0") suspend fun moods(): List<MoodRow>
    @Query("SELECT * FROM moods WHERE id = :id") suspend fun mood(id: String): MoodRow?
    @Upsert suspend fun mood(row: MoodRow)
}
