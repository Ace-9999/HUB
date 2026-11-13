package dev.personalhub.training.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.personalhub.training.api.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

// Real SQLite (D01/G02): routine snapshot isolation, single active session, process-death restore, idempotent finish.
class TrainingPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val t0 = Instant.parse("2026-11-13T10:00:00Z")

    @Test fun activeSessionSurvivesReopenAndFinishIsIdempotent() = runBlocking<Unit> {
        val name = "test-${UUID.randomUUID()}.db"
        try {
            var repo = RoomTrainingRepository.open(context, name)
            val push = Routine("r1", "Push", listOf(RoutineItem("seed:bench_bb", 3, 5, 80.0, 120)), null, false, t0)
            repo.saveRoutine(push)
            val active = Workout("w1", "Push", "r1", t0, null, listOf(WorkoutExercise("seed:bench_bb",
                listOf(WorkoutSet(SetType.NORMAL, 80.0, 5, null, null, null, true), WorkoutSet(SetType.NORMAL, 80.0, 5, null, null, null, false)), 120)))
            repo.saveActive(active)
            try { repo.saveActive(active.copy(id = "w2")); fail("second active session accepted") } catch (_: IllegalArgumentException) {}
            repo.close(); repo = RoomTrainingRepository.open(context, name) // simulated process death
            try {
                val restored = repo.workouts.first().single()
                assertTrue(restored.active); assertTrue(restored.exercises.single().sets[0].done); assertFalse(restored.exercises.single().sets[1].done)
                assertTrue(repo.finish("w1", t0.plusSeconds(3000))); assertFalse(repo.finish("w1", t0.plusSeconds(3100))) // repeated Finish
                assertEquals(t0.plusSeconds(3000), repo.workouts.first().single().finishedAt)
                // editing the routine later does not change the finished workout
                repo.saveRoutine(push.copy(items = listOf(RoutineItem("seed:squat_bb", 5, 5, 100.0, 180)), updatedAt = t0.plusSeconds(4000)))
                assertEquals("seed:bench_bb", repo.workouts.first().single().exercises.single().exerciseId)
                try { repo.saveActive(repo.workouts.first().single().copy(finishedAt = null)); fail("finished workout reopened") } catch (_: IllegalArgumentException) {}
                repo.saveExercise(Exercise("custom:x", "Synthetic Move", listOf("alias"), Equipment.OTHER, Metric.WEIGHT_REPS, Category.PUSH, setOf(Muscle.CHEST), setOf(Muscle.CHEST, Muscle.TRICEPS), listOf("Do it"), custom = true))
                val custom = repo.exercises.first().single { it.id == "custom:x" }
                assertEquals(setOf(Muscle.TRICEPS), custom.secondary); assertTrue(repo.exercises.first().size > 40)
                repo.deleteWorkout("w1"); assertTrue(repo.workouts.first().isEmpty())
            } finally { repo.close() }
        } finally { context.deleteDatabase(name) }
    }
}
