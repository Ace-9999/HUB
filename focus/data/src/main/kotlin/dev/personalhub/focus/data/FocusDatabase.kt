package dev.personalhub.focus.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Focus owns its database and dirty revision; no other domain can query its tables.
@Entity(tableName = "focus_revision")
internal data class FocusRevision(@PrimaryKey val id: Int = 1, val revision: Long = 0)
@Dao internal interface RevisionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun seed(value: FocusRevision)
    @Query("UPDATE focus_revision SET revision = revision + 1 WHERE id = 1") suspend fun bump()
    @Query("SELECT revision FROM focus_revision WHERE id = 1") suspend fun current(): Long?
    @Query("SELECT revision FROM focus_revision WHERE id = 1") fun observe(): Flow<Long?>
    @Transaction suspend fun committedChange() { seed(FocusRevision()); bump() }
}
@Database(entities = [FocusRevision::class, TaskRow::class, OccurrenceRow::class, HabitRow::class, HabitEntryRow::class, MoodRow::class], version = 2, exportSchema = true)
internal abstract class FocusDatabase : RoomDatabase() {
    abstract fun revision(): RevisionDao
    abstract fun records(): FocusDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS tasks (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, firstDay TEXT NOT NULL, priority TEXT NOT NULL, dueTime TEXT, recurrence TEXT NOT NULL, carryForward INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deleted INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS task_occurrences (taskId TEXT NOT NULL, day TEXT NOT NULL, completed INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(taskId,day), FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE TABLE IF NOT EXISTS habits (id TEXT NOT NULL PRIMARY KEY, label TEXT NOT NULL, unit TEXT NOT NULL, direction TEXT NOT NULL, target REAL NOT NULL, sensitive INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deleted INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS habit_entries (habitId TEXT NOT NULL, day TEXT NOT NULL, value REAL NOT NULL, explicitZero INTEGER NOT NULL, note TEXT, updatedAt INTEGER NOT NULL, PRIMARY KEY(habitId,day), FOREIGN KEY(habitId) REFERENCES habits(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE TABLE IF NOT EXISTS moods (id TEXT NOT NULL PRIMARY KEY, code TEXT NOT NULL, occurredAt INTEGER NOT NULL, originalZone TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, note TEXT, revision INTEGER NOT NULL, deleted INTEGER NOT NULL)")
            }
        }
        fun open(context: Context, name: String = "focus.db") = Room.databaseBuilder(context, FocusDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
    }
}
