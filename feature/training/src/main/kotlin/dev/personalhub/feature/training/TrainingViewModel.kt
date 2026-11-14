package dev.personalhub.feature.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.personalhub.core.AppTime
import dev.personalhub.training.api.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

data class TrainingUi(val loaded: Boolean = false, val exercises: List<Exercise> = emptyList(), val routines: List<Routine> = emptyList(),
    val workouts: List<Workout> = emptyList()) {
    val catalog: Map<String, Exercise> get() = exercises.associateBy { it.id }
    val active: Workout? get() = workouts.firstOrNull { it.active }
    val finished: List<Workout> get() = workouts.filter { !it.active }
}

/** Rest alert scheduling is platform work owned by the app (notification when the deadline passes). */
interface RestAlerts { fun schedule(at: Instant); fun cancel() }

/**
 * Presentation state for Train. Every change to the active workout is written immediately (autosave, GYM-08);
 * the rest countdown is a deadline, so it stays correct across pauses and recomposition (GYM-09).
 */
class TrainingViewModel(private val repo: TrainingRepository, val time: AppTime, private val alerts: RestAlerts? = null) : ViewModel() {
    val state: StateFlow<TrainingUi> = combine(repo.exercises, repo.routines, repo.workouts) { e, r, w -> TrainingUi(true, e, r, w) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainingUi())
    private val restState = MutableStateFlow(RestTimer.OFF)
    val rest: StateFlow<RestTimer> = restState
    private val error = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = error
    fun dismissMessage() { error.value = null }
    private fun run(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: IllegalArgumentException) { error.value = e.message ?: "That change couldn't be saved." }
    }
    fun now(): Instant = time.clock.instant()

    // ---------------------------------------------------------------- routines
    fun saveRoutine(r: Routine) = run { repo.saveRoutine(r.copy(updatedAt = now())) }
    fun duplicate(r: Routine) = run { repo.saveRoutine(r.copy(id = UUID.randomUUID().toString(), name = (r.name + " copy").take(80), updatedAt = now())) }
    fun archive(r: Routine, archived: Boolean = true) = run { repo.saveRoutine(r.copy(archived = archived, updatedAt = now())) }
    fun deleteRoutine(r: Routine) = run { repo.deleteRoutine(r.id) }
    fun saveCustom(e: Exercise) = run { repo.saveExercise(e) }

    // ---------------------------------------------------------------- active workout
    /** Starts a routine snapshot or an empty session; refuses while another is active (UI offers resume/discard). */
    fun start(routine: Routine?) = run {
        require(state.value.active == null) { "Finish or discard the current workout first." }
        val exercises = routine?.items.orEmpty().map { item ->
            WorkoutExercise(item.exerciseId, List(item.sets) { WorkoutSet(SetType.NORMAL, item.weightKg, item.reps, null, null, null, false) }, item.restSeconds, item.note)
        }
        repo.saveActive(Workout(UUID.randomUUID().toString(), routine?.name ?: "Workout", routine?.id, now(), null, exercises))
    }
    private fun edit(f: (Workout) -> Workout) = run { state.value.active?.let { repo.saveActive(f(it)) } }
    private fun editExercise(i: Int, f: (WorkoutExercise) -> WorkoutExercise?) = edit { w ->
        w.copy(exercises = w.exercises.mapIndexedNotNull { j, e -> if (j == i) f(e) else e })
    }
    fun addExercise(id: String) = edit { w -> w.copy(exercises = w.exercises + WorkoutExercise(id, listOf(WorkoutSet(SetType.NORMAL, null, null, null, null, null, false)), 90)) }
    fun removeExercise(i: Int) = editExercise(i) { null }
    fun moveExercise(i: Int, delta: Int) = edit { w ->
        val j = (i + delta).coerceIn(0, w.exercises.lastIndex); val l = w.exercises.toMutableList(); val x = l.removeAt(i); l.add(j, x); w.copy(exercises = l)
    }
    fun addSet(i: Int) = editExercise(i) { e -> val last = e.sets.lastOrNull(); e.copy(sets = e.sets + WorkoutSet(SetType.NORMAL, last?.weightKg, last?.reps, last?.seconds, last?.distanceM, null, false)) }
    fun removeSet(i: Int, s: Int) = editExercise(i) { e -> e.copy(sets = e.sets.filterIndexed { k, _ -> k != s }) }
    fun updateSet(i: Int, s: Int, f: (WorkoutSet) -> WorkoutSet) = editExercise(i) { e -> e.copy(sets = e.sets.mapIndexed { k, x -> if (k == s) f(x) else x }) }
    fun setRest(i: Int, seconds: Int) = editExercise(i) { e -> e.copy(restSeconds = seconds.coerceIn(0, 900)) }
    /** Completing a working set starts that exercise's rest countdown. */
    fun toggleDone(i: Int, s: Int) {
        val w = state.value.active ?: return
        val set = w.exercises.getOrNull(i)?.sets?.getOrNull(s) ?: return
        updateSet(i, s) { it.copy(done = !it.done) }
        if (!set.done && set.type != SetType.WARMUP) startRest(w.exercises[i].restSeconds)
    }
    fun rename(name: String) = edit { it.copy(name = name.take(60).ifBlank { "Workout" }) }
    fun finish() = run {
        val w = state.value.active ?: return@run
        repo.finish(w.id, now()); skipRest()
    }
    fun discard() = run { repo.discardActive(); skipRest() }
    fun deleteWorkout(w: Workout) = run { repo.deleteWorkout(w.id) }
    /** Saves an edited finished workout (replaces its exercises and sets). */
    fun saveEdited(w: Workout) = run { require(!w.active) { "Only finished workouts can be edited here." }; repo.saveFinished(w) }

    // ---------------------------------------------------------------- rest timer
    fun startRest(seconds: Int) { restState.value = RestTimer.start(seconds, now()); restState.value.deadline?.let { alerts?.schedule(it) } }
    fun extendRest(seconds: Int) { restState.value = restState.value.extend(seconds, now()); restState.value.deadline?.let { alerts?.schedule(it) } }
    fun pauseRest() { restState.value = restState.value.pause(now()); alerts?.cancel() }
    fun resumeRest() { restState.value = restState.value.resume(now()); restState.value.deadline?.let { alerts?.schedule(it) } }
    fun skipRest() { restState.value = RestTimer.OFF; alerts?.cancel() }
}
