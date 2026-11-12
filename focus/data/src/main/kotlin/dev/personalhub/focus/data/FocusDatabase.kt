package dev.personalhub.focus.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

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
@Database(entities = [FocusRevision::class], version = 1, exportSchema = true)
internal abstract class FocusDatabase : RoomDatabase() {
    abstract fun revision(): RevisionDao
    companion object {
        fun open(context: Context, name: String = "focus.db") = Room.databaseBuilder(context, FocusDatabase::class.java, name).build()
    }
}
