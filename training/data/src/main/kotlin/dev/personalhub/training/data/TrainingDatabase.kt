package dev.personalhub.training.data

import android.content.Context
import androidx.room.*
import dev.personalhub.training.api.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant

// Training owns its database. Seed exercises live in code (stable ids); only custom exercises are stored.
@Entity(tableName = "custom_exercises") internal data class ExerciseRow(@PrimaryKey val id: String, val name: String, val aliases: String, val equipment: String,
    val metric: String, val category: String, val primary: String, val secondary: String, val instructions: String, val archived: Boolean)
@Entity(tableName = "routines") internal data class RoutineRow(@PrimaryKey val id: String, val name: String, val notes: String?, val archived: Boolean, val updatedAt: Long)
@Entity(tableName = "routine_items", primaryKeys = ["routineId", "position"])
internal data class RoutineItemRow(val routineId: String, val position: Int, val exerciseId: String, val sets: Int, val reps: Int?, val weightKg: Double?, val restSeconds: Int, val note: String?)
@Entity(tableName = "workouts", indices = [Index("finishedAt")])
internal data class WorkoutRow(@PrimaryKey val id: String, val name: String, val routineId: String?, val startedAt: Long, val finishedAt: Long?, val notes: String?)
@Entity(tableName = "workout_exercises", primaryKeys = ["workoutId", "position"])
internal data class WorkoutExerciseRow(val workoutId: String, val position: Int, val exerciseId: String, val restSeconds: Int, val note: String?)
@Entity(tableName = "workout_sets", primaryKeys = ["workoutId", "exercisePosition", "position"])
internal data class SetRow(val workoutId: String, val exercisePosition: Int, val position: Int, val type: String, val weightKg: Double?, val reps: Int?,
    val seconds: Int?, val distanceM: Double?, val rpe: Double?, val done: Boolean)

@Dao internal interface TrainingDao {
    @Query("SELECT * FROM custom_exercises") fun exercises(): Flow<List<ExerciseRow>>
    @Upsert suspend fun exercise(row: ExerciseRow)
    @Query("SELECT * FROM routines ORDER BY updatedAt DESC") fun routines(): Flow<List<RoutineRow>>
    @Query("SELECT * FROM routine_items ORDER BY routineId, position") fun routineItems(): Flow<List<RoutineItemRow>>
    @Upsert suspend fun routine(row: RoutineRow)
    @Query("DELETE FROM routine_items WHERE routineId = :id") suspend fun clearItems(id: String)
    @Insert suspend fun items(rows: List<RoutineItemRow>)
    @Query("DELETE FROM routines WHERE id = :id") suspend fun deleteRoutine(id: String)
    @Query("SELECT * FROM workouts ORDER BY startedAt DESC") fun workouts(): Flow<List<WorkoutRow>>
    @Query("SELECT * FROM workout_exercises") fun workoutExercises(): Flow<List<WorkoutExerciseRow>>
    @Query("SELECT * FROM workout_sets") fun sets(): Flow<List<SetRow>>
    @Query("SELECT id FROM workouts WHERE finishedAt IS NULL") suspend fun activeIds(): List<String>
    @Query("SELECT * FROM workouts WHERE id = :id") suspend fun workout(id: String): WorkoutRow?
    @Upsert suspend fun workout(row: WorkoutRow)
    @Query("DELETE FROM workout_exercises WHERE workoutId = :id") suspend fun clearExercises(id: String)
    @Query("DELETE FROM workout_sets WHERE workoutId = :id") suspend fun clearSets(id: String)
    @Insert suspend fun workoutExercises(rows: List<WorkoutExerciseRow>)
    @Insert suspend fun sets(rows: List<SetRow>)
    @Query("UPDATE workouts SET finishedAt = :at WHERE id = :id AND finishedAt IS NULL") suspend fun finish(id: String, at: Long): Int
    @Query("DELETE FROM workouts WHERE id = :id") suspend fun deleteWorkout(id: String)
}

@Database(entities = [ExerciseRow::class, RoutineRow::class, RoutineItemRow::class, WorkoutRow::class, WorkoutExerciseRow::class, SetRow::class], version = 1, exportSchema = true)
internal abstract class TrainingDatabase : RoomDatabase() { abstract fun dao(): TrainingDao }

class RoomTrainingRepository private constructor(private val db: TrainingDatabase) : TrainingRepository, java.io.Closeable {
    private val dao get() = db.dao()
    private fun csv(s: Set<Muscle>) = s.joinToString(",") { it.name }
    private fun muscles(s: String) = s.split(",").filter { it.isNotBlank() }.mapNotNull { runCatching { Muscle.valueOf(it) }.getOrNull() }.toSet()

    override val exercises: Flow<List<Exercise>> = dao.exercises().map { rows ->
        val custom = rows.map { Exercise(it.id, it.name, it.aliases.split("\n").filter(String::isNotBlank), Equipment.valueOf(it.equipment), Metric.valueOf(it.metric),
            Category.valueOf(it.category), muscles(it.primary), muscles(it.secondary), it.instructions.split("\n").filter(String::isNotBlank), true, it.archived) }
        SeedCatalog.exercises + custom
    }
    override val routines: Flow<List<Routine>> = combine(dao.routines(), dao.routineItems()) { rs, items ->
        val byRoutine = items.groupBy { it.routineId }
        rs.map { r -> Routine(r.id, r.name, byRoutine[r.id].orEmpty().sortedBy { it.position }.map { RoutineItem(it.exerciseId, it.sets, it.reps, it.weightKg, it.restSeconds, it.note) },
            r.notes, r.archived, Instant.ofEpochMilli(r.updatedAt)) }
    }
    override val workouts: Flow<List<Workout>> = combine(dao.workouts(), dao.workoutExercises(), dao.sets()) { ws, exs, sets ->
        val exBy = exs.groupBy { it.workoutId }; val setBy = sets.groupBy { it.workoutId to it.exercisePosition }
        ws.map { w -> Workout(w.id, w.name, w.routineId, Instant.ofEpochMilli(w.startedAt), w.finishedAt?.let(Instant::ofEpochMilli),
            exBy[w.id].orEmpty().sortedBy { it.position }.map { e -> WorkoutExercise(e.exerciseId,
                setBy[w.id to e.position].orEmpty().sortedBy { it.position }.map { s -> WorkoutSet(SetType.valueOf(s.type), s.weightKg, s.reps, s.seconds, s.distanceM, s.rpe, s.done) },
                e.restSeconds, e.note) }, w.notes) }
    }

    override suspend fun saveExercise(e: Exercise) {
        require(e.custom && e.name.isNotBlank())
        dao.exercise(ExerciseRow(e.id, e.name.trim().take(80), e.aliases.joinToString("\n"), e.equipment.name, e.metric.name, e.category.name, csv(e.primary), csv(e.secondary - e.primary),
            e.instructions.joinToString("\n"), e.archived))
    }
    override suspend fun saveRoutine(r: Routine) = db.withTransaction {
        dao.routine(RoutineRow(r.id, r.name.trim(), r.notes, r.archived, r.updatedAt.toEpochMilli()))
        dao.clearItems(r.id); dao.items(r.items.mapIndexed { i, it -> RoutineItemRow(r.id, i, it.exerciseId, it.sets, it.reps, it.weightKg, it.restSeconds, it.note) })
    }
    override suspend fun deleteRoutine(id: String) = db.withTransaction { dao.clearItems(id); dao.deleteRoutine(id) }

    private suspend fun write(w: Workout) {
        dao.workout(WorkoutRow(w.id, w.name, w.routineId, w.startedAt.toEpochMilli(), w.finishedAt?.toEpochMilli(), w.notes))
        dao.clearExercises(w.id); dao.clearSets(w.id)
        dao.workoutExercises(w.exercises.mapIndexed { i, e -> WorkoutExerciseRow(w.id, i, e.exerciseId, e.restSeconds, e.note) })
        dao.sets(w.exercises.flatMapIndexed { i, e -> e.sets.mapIndexed { j, s -> SetRow(w.id, i, j, s.type.name, s.weightKg, s.reps, s.seconds, s.distanceM, s.rpe, s.done) } })
    }
    override suspend fun saveActive(w: Workout) = db.withTransaction {
        require(w.active)
        require(dao.activeIds().all { it == w.id }) { "Another workout is already in progress." }
        val existing = dao.workout(w.id)
        require(existing == null || existing.finishedAt == null) { "This workout is already finished." }
        write(w)
    }
    override suspend fun finish(id: String, at: Instant): Boolean = dao.finish(id, at.toEpochMilli()) == 1
    override suspend fun discardActive() = db.withTransaction { dao.activeIds().forEach { id -> dao.clearSets(id); dao.clearExercises(id); dao.deleteWorkout(id) } }
    override suspend fun deleteWorkout(id: String) = db.withTransaction { dao.clearSets(id); dao.clearExercises(id); dao.deleteWorkout(id) }
    override suspend fun saveFinished(w: Workout) = db.withTransaction { require(!w.active); write(w) }
    override fun close() = db.close()

    companion object { fun open(context: Context, name: String = "training.db") = RoomTrainingRepository(Room.databaseBuilder(context, TrainingDatabase::class.java, name).build()) }
}
