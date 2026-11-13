package dev.personalhub.training.api

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

// Hand-calculated synthetic dataset (G03).
class TrainingRulesTest {
    private val t0 = Instant.parse("2026-11-10T10:00:00Z")
    private val cat = SeedCatalog.byId
    private fun set(w: Double?, r: Int?, done: Boolean = true, type: SetType = SetType.NORMAL) = WorkoutSet(type, w, r, null, null, null, done)
    private val bench = "seed:bench_bb"; private val pullup = "seed:pullup"
    private val workout = Workout("w1", "Push", null, t0, t0.plusSeconds(3600), listOf(
        WorkoutExercise(bench, listOf(set(40.0, 10, type = SetType.WARMUP), set(80.0, 5), set(80.0, 5), set(85.0, 3, done = false)), 120),
        WorkoutExercise(pullup, listOf(set(null, 8), set(null, 6)), 90),
    ))

    @Test fun catalogHasEnoughUniqueMovementsAcrossCategories() {
        assertTrue(SeedCatalog.exercises.size >= 40)
        assertEquals(SeedCatalog.exercises.size, SeedCatalog.exercises.map { it.id }.distinct().size)
        assertTrue(Category.entries.all { c -> SeedCatalog.exercises.any { it.category == c } })
        assertTrue(SeedCatalog.exercises.filter { it.category != Category.CARDIO }.all { it.primary.isNotEmpty() && it.instructions.isNotEmpty() })
        assertTrue(cat.getValue(bench).matches("flat bench")); assertFalse(cat.getValue(bench).matches("squat"))
    }
    @Test fun volumeCountsOnlyCompletedWorkingWeightedSets() {
        assertEquals(800.0, volumeKg(workout, cat), 0.0) // 80x5 + 80x5; warm-up, unfinished and bodyweight excluded
        assertEquals(4, workingSets(workout)); assertEquals(24, totalReps(workout))
    }
    @Test fun e1rmOnlyForSupportedSets() {
        assertEquals(80.0 * (1 + 5 / 30.0), estimatedOneRepMax(set(80.0, 5), Metric.WEIGHT_REPS)!!, 1e-9)
        assertNull(estimatedOneRepMax(set(80.0, 15), Metric.WEIGHT_REPS)); assertNull(estimatedOneRepMax(set(null, 8), Metric.BODYWEIGHT_REPS))
        assertNull(estimatedOneRepMax(set(80.0, 5, type = SetType.WARMUP), Metric.WEIGHT_REPS))
    }
    @Test fun muscleMapV1AddsPrimaryOneSecondaryHalfAndIgnoresActive() {
        val active = workout.copy(id = "a", finishedAt = null)
        val unknown = workout.copy(id = "u", exercises = listOf(WorkoutExercise("custom:gone", listOf(set(10.0, 10)), 60)))
        val m = muscleContributions(listOf(workout, active, unknown), cat)
        assertEquals(2.0, m.byMuscle[Muscle.CHEST]!!, 0.0)         // 2 working bench sets x 1.0
        assertEquals(1.0, m.byMuscle[Muscle.TRICEPS]!!, 0.0)  // bench secondary 2 x 0.5 = 1, pullup none
        assertEquals(2.0, m.byMuscle[Muscle.LATS]!!, 0.0); assertEquals(1.0, m.byMuscle[Muscle.BICEPS]!!, 0.0)
        assertEquals(1, m.unmappedSets)
        assertEquals(listOf(0, 1, 2, 3, 4), listOf(0.0, 3.0, 6.0, 10.0, 10.5).map(::heatBin))
    }
    @Test fun recordsAndPreviousValuesUseFinishedWorkoutsOnly() {
        val later = workout.copy(id = "w2", startedAt = t0.plusSeconds(86400), finishedAt = t0.plusSeconds(90000),
            exercises = listOf(WorkoutExercise(bench, listOf(set(82.5, 5)), 120)))
        val active = later.copy(id = "a", finishedAt = null, exercises = listOf(WorkoutExercise(bench, listOf(set(200.0, 5)), 120)))
        val pr = personalRecords(listOf(workout, later, active), cat).getValue(bench)
        assertEquals(82.5, pr.heaviestKg!!, 0.0); assertEquals("w2", pr.workoutId)
        assertEquals(listOf(82.5), previousSets(listOf(workout, later, active), bench).map { it.weightKg })
    }
    @Test fun restTimerIsDeadlineBased() {
        val r = RestTimer.start(90, t0)
        assertEquals(30_000, r.remainingMs(t0.plusSeconds(60)))                 // survives UI pause: computed from deadline
        val p = r.pause(t0.plusSeconds(60)); assertEquals(30_000, p.remainingMs(t0.plusSeconds(600)))
        val resumed = p.resume(t0.plusSeconds(600)); assertEquals(t0.plusSeconds(630), resumed.deadline)
        assertEquals(t0.plusSeconds(165), r.extend(15, t0.plusSeconds(60)).deadline!!.plusSeconds(60))
        assertEquals(0, RestTimer.start(0, t0).remainingMs(t0))
    }
    @Test(expected = IllegalArgumentException::class) fun negativeWeightRejected() { set(-5.0, 5) }
}
