package dev.personalhub.training.api

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * Training domain (GYM-01..GYM-11, SRS §6.1). Pure Kotlin: no Room, Android or presentation.
 * Calculations are project rules (not physiological measurements) and carry no copied third-party assets.
 */

enum class Muscle(val label: String, val front: Boolean) {
    CHEST("Chest", true), FRONT_DELTS("Front delts", true), SIDE_DELTS("Side delts", true), BICEPS("Biceps", true), FOREARMS("Forearms", true),
    ABS("Abs", true), OBLIQUES("Obliques", true), QUADS("Quads", true), ADDUCTORS("Adductors", true), CALVES("Calves", false),
    TRAPS("Traps", false), REAR_DELTS("Rear delts", false), LATS("Lats", false), UPPER_BACK("Upper back", false), TRICEPS("Triceps", false),
    LOWER_BACK("Lower back", false), GLUTES("Glutes", false), HAMSTRINGS("Hamstrings", false),
}

enum class Equipment(val label: String) { BARBELL("Barbell"), DUMBBELL("Dumbbell"), MACHINE("Machine"), CABLE("Cable"), BODYWEIGHT("Bodyweight"), KETTLEBELL("Kettlebell"), BAND("Band"), OTHER("Other") }

/** How a set is measured. Volume (kg x reps) applies only to WEIGHT_REPS. */
enum class Metric { WEIGHT_REPS, BODYWEIGHT_REPS, ASSISTED_REPS, DURATION, DISTANCE_DURATION }

enum class Category(val label: String) { PUSH("Push"), PULL("Pull"), LEGS("Legs"), CORE("Core"), CARDIO("Cardio") }

data class Exercise(
    val id: String, val name: String, val aliases: List<String>, val equipment: Equipment, val metric: Metric, val category: Category,
    val primary: Set<Muscle>, val secondary: Set<Muscle>, val instructions: List<String>, val custom: Boolean = false, val archived: Boolean = false,
) {
    fun matches(query: String): Boolean {
        val q = query.trim().lowercase()
        return q.isEmpty() || name.lowercase().contains(q) || aliases.any { it.lowercase().contains(q) }
    }
}

data class RoutineItem(val exerciseId: String, val sets: Int, val reps: Int?, val weightKg: Double?, val restSeconds: Int, val note: String? = null) {
    init { require(sets in 1..20); require(reps == null || reps in 0..500); require(weightKg == null || weightKg >= 0); require(restSeconds in 0..900) }
}
data class Routine(val id: String, val name: String, val items: List<RoutineItem>, val notes: String?, val archived: Boolean, val updatedAt: Instant) {
    init { require(name.isNotBlank() && name.length <= 80) }
}

enum class SetType { WARMUP, NORMAL, FAILURE, DROP }
data class WorkoutSet(val type: SetType, val weightKg: Double?, val reps: Int?, val seconds: Int?, val distanceM: Double?, val rpe: Double?, val done: Boolean) {
    init {
        require(weightKg == null || weightKg >= 0) { "Weight can't be negative." }
        require(reps == null || reps >= 0) { "Reps can't be negative." }
        require(seconds == null || seconds >= 0); require(distanceM == null || distanceM >= 0)
        require(rpe == null || rpe in 1.0..10.0) { "RPE is 1–10." }
    }
    val working: Boolean get() = done && type != SetType.WARMUP
}
data class WorkoutExercise(val exerciseId: String, val sets: List<WorkoutSet>, val restSeconds: Int, val note: String? = null)

/** A workout; [finishedAt] null = the single active session. Exercises are snapshotted, never linked to a live routine. */
data class Workout(val id: String, val name: String, val routineId: String?, val startedAt: Instant, val finishedAt: Instant?,
    val exercises: List<WorkoutExercise>, val notes: String? = null) {
    val active: Boolean get() = finishedAt == null
}

// ---------------------------------------------------------------- calculations (§6.1)
fun volumeKg(w: Workout, catalog: Map<String, Exercise>): Double = w.exercises.sumOf { e ->
    if (catalog[e.exerciseId]?.metric != Metric.WEIGHT_REPS) 0.0
    else e.sets.filter { it.working }.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
}
fun workingSets(w: Workout): Int = w.exercises.sumOf { e -> e.sets.count { it.working } }
fun totalReps(w: Workout): Int = w.exercises.sumOf { e -> e.sets.filter { it.working }.sumOf { it.reps ?: 0 } }

/** Epley estimate for positive-load sets of 1–12 reps on weighted exercises; null otherwise (labelled an estimate). */
fun estimatedOneRepMax(s: WorkoutSet, metric: Metric): Double? {
    val w = s.weightKg; val r = s.reps
    if (metric != Metric.WEIGHT_REPS || !s.working || w == null || w <= 0 || r == null || r !in 1..12) return null
    return w * (1 + r / 30.0)
}

/** Muscle map v1: +1.0 per primary, +0.5 per secondary (secondary overlaps with primary removed), per completed working set. */
data class MuscleTotals(val byMuscle: Map<Muscle, Double>, val unmappedSets: Int)
fun muscleContributions(workouts: List<Workout>, catalog: Map<String, Exercise>): MuscleTotals {
    val totals = mutableMapOf<Muscle, Double>(); var unmapped = 0
    workouts.filter { !it.active }.forEach { w -> w.exercises.forEach { e ->
        val ex = catalog[e.exerciseId]
        val n = e.sets.count { it.working }
        if (ex == null || ex.primary.isEmpty()) { unmapped += n; return@forEach }
        ex.primary.forEach { m -> totals[m] = (totals[m] ?: 0.0) + n }
        (ex.secondary - ex.primary).forEach { m -> totals[m] = (totals[m] ?: 0.0) + 0.5 * n }
    } }
    return MuscleTotals(totals, unmapped)
}
/** Heat bins: 0, >0–3, >3–6, >6–10, >10. */
fun heatBin(v: Double): Int = when { v <= 0 -> 0; v <= 3 -> 1; v <= 6 -> 2; v <= 10 -> 3; else -> 4 }

data class PersonalRecord(val exerciseId: String, val bestE1rm: Double?, val heaviestKg: Double?, val mostReps: Int?, val workoutId: String)
/** Records from finished workouts only (active sessions never update records). */
fun personalRecords(workouts: List<Workout>, catalog: Map<String, Exercise>): Map<String, PersonalRecord> {
    val out = mutableMapOf<String, PersonalRecord>()
    workouts.filter { !it.active }.sortedBy { it.startedAt }.forEach { w -> w.exercises.forEach { e ->
        val metric = catalog[e.exerciseId]?.metric ?: return@forEach
        e.sets.filter { it.working }.forEach { s ->
            val prev = out[e.exerciseId]
            val e1 = estimatedOneRepMax(s, metric)
            out[e.exerciseId] = PersonalRecord(e.exerciseId,
                listOfNotNull(prev?.bestE1rm, e1).maxOrNull(),
                listOfNotNull(prev?.heaviestKg, s.weightKg?.takeIf { metric == Metric.WEIGHT_REPS }).maxOrNull(),
                listOfNotNull(prev?.mostReps, s.reps).maxOrNull(),
                if (e1 != null && (prev?.bestE1rm == null || e1 > prev.bestE1rm)) w.id else prev?.workoutId ?: w.id)
        }
    } }
    return out
}

/** Most recent completed values for an exercise, used as greyed suggestions (never counted). */
fun previousSets(workouts: List<Workout>, exerciseId: String): List<WorkoutSet> =
    workouts.filter { !it.active }.sortedByDescending { it.startedAt }.firstNotNullOfOrNull { w -> w.exercises.firstOrNull { it.exerciseId == exerciseId }?.sets?.filter { it.done }?.takeIf { it.isNotEmpty() } }.orEmpty()

fun inRange(w: Workout, from: LocalDate, to: LocalDate, zone: ZoneId) = w.startedAt.atZone(zone).toLocalDate().let { it >= from && it <= to }

// ---------------------------------------------------------------- rest timer (deadline-based, GYM-09)
data class RestTimer(val deadline: Instant?, val pausedRemainingMs: Long?, val totalSeconds: Int) {
    fun remainingMs(now: Instant): Long = pausedRemainingMs ?: deadline?.let { (it.toEpochMilli() - now.toEpochMilli()).coerceAtLeast(0) } ?: 0
    fun running(now: Instant) = pausedRemainingMs == null && deadline != null && deadline.isAfter(now)
    fun pause(now: Instant) = if (pausedRemainingMs != null || deadline == null) this else copy(pausedRemainingMs = remainingMs(now), deadline = null)
    fun resume(now: Instant) = pausedRemainingMs?.let { copy(deadline = now.plusMillis(it), pausedRemainingMs = null) } ?: this
    fun extend(seconds: Int, now: Instant) = pausedRemainingMs?.let { copy(pausedRemainingMs = it + seconds * 1000L, totalSeconds = totalSeconds + seconds) }
        ?: copy(deadline = (deadline?.takeIf { it.isAfter(now) } ?: now).plusSeconds(seconds.toLong()), totalSeconds = totalSeconds + seconds)
    companion object {
        val OFF = RestTimer(null, null, 0)
        fun start(seconds: Int, now: Instant) = if (seconds <= 0) OFF else RestTimer(now.plusSeconds(seconds.toLong()), null, seconds)
    }
}

interface TrainingRepository {
    val exercises: Flow<List<Exercise>>
    val routines: Flow<List<Routine>>
    val workouts: Flow<List<Workout>>
    suspend fun saveExercise(e: Exercise)
    suspend fun saveRoutine(r: Routine)
    suspend fun deleteRoutine(id: String)
    /** Stores the active session after every change (autosave). Throws if another session is active. */
    suspend fun saveActive(w: Workout)
    /** Idempotent: finishing an already-finished id is a no-op returning false. */
    suspend fun finish(id: String, at: Instant): Boolean
    suspend fun discardActive()
    suspend fun deleteWorkout(id: String)
    suspend fun saveFinished(w: Workout)
}
