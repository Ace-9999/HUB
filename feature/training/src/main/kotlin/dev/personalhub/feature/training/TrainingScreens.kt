package dev.personalhub.feature.training

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.personalhub.design.*
import dev.personalhub.training.api.*
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class TrainPage(val title: String) { OVERVIEW("Train"), CATALOG("Exercises"), EXERCISE("Exercise"), ROUTINE("Routine"), WORKOUT("Workout"), HISTORY("History"), STATS("Statistics") }
/** Navigation inside Train. [arg] = exercise/routine id; [pickFor] = page that receives a picked exercise. */
data class TrainNav(val page: TrainPage = TrainPage.OVERVIEW, val arg: String? = null, val pickFor: TrainPage? = null)

private val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM")
private fun kg(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else "%.1f".format(v)
private fun vol(v: Double) = if (v >= 1000) "%.1fk kg".format(v / 1000) else "${kg(v)} kg"
private fun dur(d: Duration) = d.seconds.let { if (it >= 3600) "${it / 3600}h ${(it % 3600) / 60}m" else "${it / 60}m" }

@Composable fun TrainingContent(vm: TrainingViewModel, nav: TrainNav, go: (TrainNav) -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val back = { go(if (nav.page == TrainPage.EXERCISE && nav.pickFor == null) TrainNav(TrainPage.CATALOG) else if (nav.pickFor != null) TrainNav(nav.pickFor, nav.arg) else TrainNav()) }
    if (nav.page != TrainPage.OVERVIEW) ScreenTitle(nav.page.title, onBack = back, back = HubIcons.Back)
    vm.message.collectAsStateWithLifecycle().value?.let { m ->
        HubPanel(color = Hub.colors.peach, padding = 12.dp) { Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m, style = MaterialTheme.typography.bodyMedium, color = Hub.colors.onAccent, modifier = Modifier.weight(1f)); HubButton("OK", vm::dismissMessage, tone = ButtonTone.Plain, compact = true) } }
    }
    if (!ui.loaded) return
    when (nav.page) {
        TrainPage.OVERVIEW -> Overview(vm, ui, go)
        TrainPage.CATALOG -> Catalog(ui, nav, go, vm)
        TrainPage.EXERCISE -> ExerciseDetail(ui, nav.arg)
        TrainPage.ROUTINE -> RoutineEditor(vm, ui, nav, go)
        TrainPage.WORKOUT -> ActiveWorkout(vm, ui, go)
        TrainPage.HISTORY -> History(vm, ui)
        TrainPage.STATS -> Stats(vm, ui)
    }
}

// ---------------------------------------------------------------- overview (TRN-01)
@Composable private fun Overview(vm: TrainingViewModel, ui: TrainingUi, go: (TrainNav) -> Unit) {
    val zone = vm.time.zone; val today = LocalDate.now(zone)
    val week = ui.finished.filter { inRange(it, today.minusDays(6), today, zone) }
    StatRow(Triple("WORKOUTS", week.size.toString(), "last 7 days"), Triple("SETS", week.sumOf { workingSets(it) }.toString(), "working"),
        Triple("VOLUME", vol(week.sumOf { volumeKg(it, ui.catalog) }), null))
    ui.active?.let { a ->
        HubCard("Workout in progress", color = Hub.colors.lime, onClick = { go(TrainNav(TrainPage.WORKOUT)) }) {
            Text("${a.name} · started ${a.startedAt.atZone(zone).toLocalTime().format(DateTimeFormatter.ofPattern("h:mm a"))} · ${a.exercises.sumOf { e -> e.sets.count { it.done } }} sets done",
                style = MaterialTheme.typography.bodyLarge, color = Hub.colors.onAccent)
            HubButton("Resume", { go(TrainNav(TrainPage.WORKOUT)) }, Modifier.fillMaxWidth(), tone = ButtonTone.Plain, trailingIcon = HubIcons.Arrow)
        }
    } ?: HubButton("Start empty workout", { vm.start(null); go(TrainNav(TrainPage.WORKOUT)) }, Modifier.fillMaxWidth(), icon = HubIcons.Plus)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Tile("Exercises", Hub.colors.cyan, Modifier.weight(1f)) { go(TrainNav(TrainPage.CATALOG)) }
        Tile("History", Hub.colors.apricot, Modifier.weight(1f)) { go(TrainNav(TrainPage.HISTORY)) }
        Tile("Statistics", Hub.colors.lavender, Modifier.weight(1f)) { go(TrainNav(TrainPage.STATS)) }
    }
    SectionHeader("Routines", action = { HubButton("New", { go(TrainNav(TrainPage.ROUTINE, "new")) }, icon = HubIcons.Plus, compact = true) })
    val routines = ui.routines.filter { !it.archived }
    if (routines.isEmpty()) EmptyState("No routines yet", "Build one — e.g. Push: bench, incline press, lateral raise, pushdown — and start it any time.")
    routines.forEach { r ->
        HubCard(r.name, trailing = { HubIconButton(HubIcons.Edit, "Edit ${r.name}", { go(TrainNav(TrainPage.ROUTINE, r.id)) }) }) {
            Text(r.items.joinToString(", ") { ui.catalog[it.exerciseId]?.name ?: "Removed exercise" }.ifEmpty { "No exercises" },
                style = MaterialTheme.typography.bodyMedium, color = Hub.colors.inkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
            HubButton("Start routine", { if (ui.active == null) { vm.start(r); go(TrainNav(TrainPage.WORKOUT)) } else go(TrainNav(TrainPage.WORKOUT)) },
                Modifier.fillMaxWidth(), enabled = r.items.isNotEmpty(), trailingIcon = HubIcons.Arrow)
        }
    }
    val archived = ui.routines.filter { it.archived }
    if (archived.isNotEmpty()) Text("${archived.size} archived routine(s) — open one from its editor to restore.", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
}

@Composable private fun Tile(label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    HubSurface(modifier, color = color, onClick = onClick, onClickLabel = "Open $label") {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Hub.colors.onAccent, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 18.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------- catalog (TRN-02/03)
@Composable private fun Catalog(ui: TrainingUi, nav: TrainNav, go: (TrainNav) -> Unit, vm: TrainingViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableIntStateOf(0) }
    var equipment by rememberSaveable { mutableIntStateOf(0) }
    var adding by rememberSaveable { mutableStateOf(false) }
    if (nav.pickFor != null) Pill("Tap an exercise to add it", Hub.colors.lime)
    HubField(query, { query = it }, "Search", placeholder = "Name or alias")
    PillTabs(listOf("All") + Category.entries.map { it.label }, category, { category = it }, color = Hub.colors.cyan)
    PillTabs(listOf("Any equipment") + Equipment.entries.map { it.label }, equipment, { equipment = it }, color = Hub.colors.apricot)
    val list = ui.exercises.filter { !it.archived && it.matches(query) && (category == 0 || it.category == Category.entries[category - 1]) &&
        (equipment == 0 || it.equipment == Equipment.entries[equipment - 1]) }.sortedBy { it.name }
    Text("${list.size} exercises", style = MaterialTheme.typography.labelMedium, color = Hub.colors.inkSoft)
    list.forEach { e ->
        HubSurface(Modifier.fillMaxWidth(), shadow = 3.dp, onClick = { if (nav.pickFor != null) go(TrainNav(nav.pickFor, nav.arg, null).also { pickedExercise = e.id }) else go(TrainNav(TrainPage.EXERCISE, e.id)) }) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExerciseThumb(e, 52.dp)
                Column(Modifier.weight(1f)) {
                    Text(e.name, style = MaterialTheme.typography.titleMedium, color = Hub.colors.ink)
                    Text("${e.equipment.label} · ${e.primary.joinToString { it.label }.ifEmpty { e.category.label }}${if (e.custom) " · custom" else ""}", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
                }
                Icon(if (nav.pickFor != null) HubIcons.Plus else HubIcons.Arrow)
            }
        }
    }
    if (nav.pickFor == null) {
        HubButton(if (adding) "Cancel custom exercise" else "Create custom exercise", { adding = !adding }, tone = ButtonTone.Plain, compact = true, icon = HubIcons.Plus)
        if (adding) CustomExercise(vm) { adding = false }
    }
}

/** Set by the catalog when picking, consumed by the receiving page (single-shot, UI-only). */
private var pickedExercise: String? = null

@Composable private fun Icon(v: androidx.compose.ui.graphics.vector.ImageVector) = androidx.compose.material3.Icon(v, null, tint = Hub.colors.ink)

@Composable private fun CustomExercise(vm: TrainingViewModel, done: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var eq by rememberSaveable { mutableIntStateOf(0) }
    var cat by rememberSaveable { mutableIntStateOf(0) }
    var metric by rememberSaveable { mutableIntStateOf(0) }
    var muscle by rememberSaveable { mutableIntStateOf(0) }
    HubCard("Custom exercise") {
        HubField(name, { name = it.take(80) }, "Name")
        Text("EQUIPMENT", style = MaterialTheme.typography.labelLarge, color = Hub.colors.ink)
        PillTabs(Equipment.entries.map { it.label }, eq, { eq = it }, color = Hub.colors.apricot)
        Text("CATEGORY", style = MaterialTheme.typography.labelLarge, color = Hub.colors.ink)
        PillTabs(Category.entries.map { it.label }, cat, { cat = it }, color = Hub.colors.cyan)
        Text("MEASURED BY", style = MaterialTheme.typography.labelLarge, color = Hub.colors.ink)
        ChoiceCells(listOf("kg × reps", "Reps", "Time"), metric, { metric = it }, description = "Metric")
        Text("MAIN MUSCLE", style = MaterialTheme.typography.labelLarge, color = Hub.colors.ink)
        PillTabs(Muscle.entries.map { it.label }, muscle, { muscle = it }, color = Hub.colors.mint)
        HubButton("Save exercise", {
            vm.saveCustom(Exercise("custom:${UUID.randomUUID()}", name.trim(), emptyList(), Equipment.entries[eq],
                listOf(Metric.WEIGHT_REPS, Metric.BODYWEIGHT_REPS, Metric.DURATION)[metric], Category.entries[cat], setOf(Muscle.entries[muscle]), emptySet(), emptyList(), custom = true))
            done()
        }, Modifier.fillMaxWidth(), enabled = name.isNotBlank(), icon = HubIcons.Check)
    }
}

@Composable private fun ExerciseDetail(ui: TrainingUi, id: String?) {
    val e = ui.catalog[id] ?: return
    ExerciseDemo(e)
    Text(e.name, style = MaterialTheme.typography.headlineMedium, color = Hub.colors.ink)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill(e.equipment.label, Hub.colors.apricot); Pill(e.category.label, Hub.colors.cyan); if (e.custom) Pill("Custom", Hub.colors.lavender)
    }
    HubCard("Muscles") {
        Text("Primary: ${e.primary.joinToString { it.label }.ifEmpty { "—" }}", style = MaterialTheme.typography.bodyLarge, color = Hub.colors.ink)
        Text("Secondary: ${e.secondary.joinToString { it.label }.ifEmpty { "—" }}", style = MaterialTheme.typography.bodyMedium, color = Hub.colors.inkSoft)
        BodyMap(muscleContributions(listOf(Workout("demo", "", null, Instant.EPOCH, Instant.EPOCH, listOf(WorkoutExercise(e.id, listOf(WorkoutSet(SetType.NORMAL, null, null, null, null, null, true)), 0)))), ui.catalog).byMuscle, compact = true)
    }
    HubCard("How to") {
        if (e.instructions.isEmpty()) Text("No instructions yet.", style = MaterialTheme.typography.bodyMedium, color = Hub.colors.inkSoft)
        e.instructions.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodyLarge, color = Hub.colors.ink) }
        if (!e.id.startsWith("seed:")) Text("Custom exercise — no demonstration photo.", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
    }
    val pr = personalRecords(ui.finished, ui.catalog)[e.id]
    val history = ui.finished.mapNotNull { w -> w.exercises.firstOrNull { it.exerciseId == e.id }?.let { w to it } }.take(10)
    HubCard("Records & history") {
        if (pr == null) Text("Not logged yet.", style = MaterialTheme.typography.bodyMedium, color = Hub.colors.inkSoft)
        pr?.let {
            Text(listOfNotNull(it.heaviestKg?.let { v -> "Heaviest ${kg(v)} kg" }, it.bestE1rm?.let { v -> "Est. 1RM ${kg(v)} kg" }, it.mostReps?.let { v -> "Most reps $v" }).joinToString(" · "),
                style = MaterialTheme.typography.titleSmall, color = Hub.colors.ink)
        }
        history.forEach { (w, ex) ->
            Text("${w.startedAt.atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt)}: " + ex.sets.filter { it.working }.joinToString("  ") { s ->
                listOfNotNull(s.weightKg?.let { "${kg(it)}kg" }, s.reps?.let { "×$it" }, s.seconds?.let { "${it}s" }).joinToString("") }, style = MaterialTheme.typography.bodySmall, color = Hub.colors.ink)
        }
        Text("1RM is an estimate (weight × (1 + reps/30), 1–12 reps).", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
    }
}

// ---------------------------------------------------------------- routine editor (TRN-04)
@Composable private fun RoutineEditor(vm: TrainingViewModel, ui: TrainingUi, nav: TrainNav, go: (TrainNav) -> Unit) {
    val existing = ui.routines.firstOrNull { it.id == nav.arg }
    val draftKey = nav.arg ?: "new"
    // A draft held while the catalog picker is open comes back with the picked exercise appended.
    var draft by remember(draftKey) { mutableStateOf(
        routineDraftHold?.takeIf { it.first == draftKey }?.second?.let { held ->
            routineDraftHold = null
            held.copy(items = held.items + listOfNotNull(pickedExercise?.let { RoutineItem(it, 3, 10, null, 90) })).also { pickedExercise = null }
        } ?: RoutineDraft.of(existing)) }
    HubField(draft.name, { draft = draft.copy(name = it.take(80)) }, "Routine name", placeholder = "e.g. Push")
    draft.items.forEachIndexed { i, item ->
        HubPanel(padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExerciseThumb(ui.catalog[item.exerciseId], 40.dp)
                Text(ui.catalog[item.exerciseId]?.name ?: "Removed exercise", style = MaterialTheme.typography.titleMedium, color = Hub.colors.ink, modifier = Modifier.weight(1f))
                HubIconButton(HubIcons.Up, "Move up", { draft = draft.move(i, -1) })
                Spacer(Modifier.width(4.dp))
                HubIconButton(HubIcons.Trash, "Remove", { draft = draft.copy(items = draft.items.filterIndexed { k, _ -> k != i }) }, color = Hub.colors.peach)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberBox("Sets", item.sets.toString(), Modifier.weight(1f)) { v -> v.toIntOrNull()?.takeIf { it in 1..20 }?.let { draft = draft.update(i) { x -> x.copy(sets = it) } } }
                NumberBox("Reps", item.reps?.toString() ?: "", Modifier.weight(1f)) { v -> draft = draft.update(i) { x -> x.copy(reps = v.toIntOrNull()?.coerceIn(0, 500)) } }
                NumberBox("kg", item.weightKg?.let(::kg) ?: "", Modifier.weight(1f)) { v -> draft = draft.update(i) { x -> x.copy(weightKg = v.toDoubleOrNull()?.coerceAtLeast(0.0)) } }
                NumberBox("Rest s", item.restSeconds.toString(), Modifier.weight(1f)) { v -> v.toIntOrNull()?.coerceIn(0, 900)?.let { draft = draft.update(i) { x -> x.copy(restSeconds = it) } } }
            }
        }
    }
    HubButton("Add exercise", { routineDraftHold = draftKey to draft; go(TrainNav(TrainPage.CATALOG, draftKey, TrainPage.ROUTINE)) }, Modifier.fillMaxWidth(), tone = ButtonTone.Plain, icon = HubIcons.Plus)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HubButton("Save routine", {
            vm.saveRoutine(Routine(draft.id, draft.name.ifBlank { "Routine" }, draft.items, null, existing?.archived ?: false, vm.now())); go(TrainNav())
        }, Modifier.weight(1f), enabled = draft.items.isNotEmpty(), icon = HubIcons.Check)
    }
    existing?.let { r ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HubButton("Duplicate", { vm.duplicate(r); go(TrainNav()) }, tone = ButtonTone.Plain, compact = true)
            HubButton(if (r.archived) "Restore" else "Archive", { vm.archive(r, !r.archived); go(TrainNav()) }, tone = ButtonTone.Plain, compact = true)
            HubButton("Delete", { vm.deleteRoutine(r); go(TrainNav()) }, tone = ButtonTone.Danger, compact = true)
        }
        Text("Editing a routine never changes workouts you've already finished.", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
    }
}
private var routineDraftHold: Pair<String, RoutineDraft>? = null
private data class RoutineDraft(val id: String, val name: String, val items: List<RoutineItem>) {
    fun update(i: Int, f: (RoutineItem) -> RoutineItem) = copy(items = items.mapIndexed { k, x -> if (k == i) runCatching { f(x) }.getOrDefault(x) else x })
    fun move(i: Int, d: Int): RoutineDraft { val l = items.toMutableList(); val j = (i + d).coerceIn(0, l.lastIndex); val x = l.removeAt(i); l.add(j, x); return copy(items = l) }
    companion object { fun of(r: Routine?) = RoutineDraft(r?.id ?: UUID.randomUUID().toString(), r?.name ?: "", r?.items.orEmpty()) }
}

@Composable private fun NumberBox(label: String, value: String, modifier: Modifier, onValue: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Hub.colors.inkSoft)
        val shape = RoundedCornerShape(8.dp)
        BasicTextField(text, { t -> text = t.filter { it.isDigit() || it == '.' }.take(6); onValue(text) }, singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = Hub.colors.ink, textAlign = TextAlign.Center), cursorBrush = SolidColor(Hub.colors.ink),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().height(44.dp).clip(shape).background(Hub.colors.raised).border(1.5.dp, Hub.colors.outline, shape).wrapContentHeight(Alignment.CenterVertically))
    }
}

// ---------------------------------------------------------------- active workout (TRN-05)
@Composable private fun ActiveWorkout(vm: TrainingViewModel, ui: TrainingUi, go: (TrainNav) -> Unit) {
    val w = ui.active
    if (w == null) { EmptyState("No workout in progress", "Start one from a routine or an empty workout."); HubButton("Start empty workout", { vm.start(null) }, Modifier.fillMaxWidth()); return }
    LaunchedEffect(Unit) { pickedExercise?.let { vm.addExercise(it); pickedExercise = null } }
    var now by remember { mutableStateOf(vm.now()) }
    LaunchedEffect(Unit) { while (true) { now = vm.now(); delay(500) } }
    val rest by vm.rest.collectAsStateWithLifecycle()
    StatRow(Triple("TIME", dur(Duration.between(w.startedAt, now)), null), Triple("SETS", workingSets(w).toString(), "done"),
        Triple("VOLUME", vol(volumeKg(w, ui.catalog)), null))
    if (rest.remainingMs(now) > 0 || rest.pausedRemainingMs != null) RestBanner(vm, rest, now)
    w.exercises.forEachIndexed { i, e -> ExerciseBlock(vm, ui, i, e) }
    HubButton("Add exercise", { go(TrainNav(TrainPage.CATALOG, null, TrainPage.WORKOUT)) }, Modifier.fillMaxWidth(), tone = ButtonTone.Plain, icon = HubIcons.Plus)
    var confirm by remember { mutableStateOf<String?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HubButton(if (confirm == "finish") "Tap again to finish" else "Finish", { if (confirm == "finish") { vm.finish(); go(TrainNav()) } else confirm = "finish" }, Modifier.weight(1f), icon = HubIcons.Check)
        HubButton(if (confirm == "discard") "Confirm discard" else "Discard", { if (confirm == "discard") { vm.discard(); go(TrainNav()) } else confirm = "discard" }, tone = ButtonTone.Danger)
    }
    Text("Every change is saved as you go. Unfinished sets are not counted.", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
}

@Composable private fun RestBanner(vm: TrainingViewModel, rest: RestTimer, now: Instant) {
    val left = rest.remainingMs(now) / 1000
    HubPanel(color = Hub.colors.ink, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("REST", style = MaterialTheme.typography.labelMedium, color = Hub.colors.canvas)
                Text("%d:%02d".format(left / 60, left % 60), style = MaterialTheme.typography.displaySmall, color = Hub.colors.lime)
            }
            HubButton("+15s", { vm.extendRest(15) }, compact = true, tone = ButtonTone.Plain)
            Spacer(Modifier.width(6.dp))
            HubButton(if (rest.pausedRemainingMs != null) "Resume" else "Pause", { if (rest.pausedRemainingMs != null) vm.resumeRest() else vm.pauseRest() }, compact = true, tone = ButtonTone.Plain)
            Spacer(Modifier.width(6.dp))
            HubButton("Skip", vm::skipRest, compact = true)
        }
    }
}

@Composable private fun ExerciseBlock(vm: TrainingViewModel, ui: TrainingUi, i: Int, e: WorkoutExercise) {
    val ex = ui.catalog[e.exerciseId]
    val prev = previousSets(ui.finished, e.exerciseId)
    val metric = ex?.metric ?: Metric.WEIGHT_REPS
    HubPanel(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ExerciseThumb(ex, 46.dp)
            Text(ex?.name ?: "Removed exercise", style = MaterialTheme.typography.titleLarge, color = Hub.colors.ink, modifier = Modifier.weight(1f))
            HubIconButton(HubIcons.Up, "Move up", { vm.moveExercise(i, -1) })
            Spacer(Modifier.width(4.dp))
            HubIconButton(HubIcons.Trash, "Remove exercise", { vm.removeExercise(i) }, color = Hub.colors.peach)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("SET" to 0.6f, "PREVIOUS" to 1.4f, (if (metric == Metric.DURATION) "SEC" else if (metric == Metric.WEIGHT_REPS) "KG" else "+KG") to 1f,
                (if (metric == Metric.DURATION) "" else "REPS") to 1f, "✓" to 0.7f).forEach { (h, w) ->
                Text(h, style = MaterialTheme.typography.labelSmall, color = Hub.colors.inkSoft, modifier = Modifier.weight(w), textAlign = TextAlign.Center)
            }
        }
        e.sets.forEachIndexed { s, set ->
            val p = prev.getOrNull(s)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (set.done) Hub.colors.mint else androidx.compose.ui.graphics.Color.Transparent).padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (set.type == SetType.WARMUP) "W" else "${e.sets.take(s + 1).count { it.type != SetType.WARMUP }}", style = MaterialTheme.typography.titleSmall,
                    color = Hub.colors.ink, textAlign = TextAlign.Center, modifier = Modifier.weight(0.6f).clickable(role = Role.Button, onClickLabel = "Toggle warm-up") {
                        vm.updateSet(i, s) { it.copy(type = if (it.type == SetType.WARMUP) SetType.NORMAL else SetType.WARMUP) } })
                Text(p?.let { listOfNotNull(it.weightKg?.let { v -> "${kg(v)}kg" }, it.reps?.let { r -> "×$r" }, it.seconds?.let { x -> "${x}s" }).joinToString("") } ?: "—",
                    style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft, textAlign = TextAlign.Center, modifier = Modifier.weight(1.4f))
                if (metric == Metric.DURATION) {
                    SetCell(set.seconds?.toString() ?: "", p?.seconds?.toString(), Modifier.weight(1f)) { v -> vm.updateSet(i, s) { it.copy(seconds = v.toIntOrNull()) } }
                    Spacer(Modifier.weight(1f))
                } else {
                    SetCell(set.weightKg?.let(::kg) ?: "", p?.weightKg?.let(::kg), Modifier.weight(1f)) { v -> vm.updateSet(i, s) { it.copy(weightKg = v.toDoubleOrNull()) } }
                    SetCell(set.reps?.toString() ?: "", p?.reps?.toString(), Modifier.weight(1f)) { v -> vm.updateSet(i, s) { it.copy(reps = v.toIntOrNull()) } }
                }
                Box(Modifier.weight(0.7f), contentAlignment = Alignment.Center) {
                    HubCheck(set.done, { vm.toggleDone(i, s) }, if (set.done) "Mark set ${s + 1} not done" else "Complete set ${s + 1}", color = Hub.colors.lime, check = HubIcons.Check)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            HubButton("Add set", { vm.addSet(i) }, compact = true, tone = ButtonTone.Plain, icon = HubIcons.Plus)
            if (e.sets.isNotEmpty()) HubButton("Remove last", { vm.removeSet(i, e.sets.lastIndex) }, compact = true, tone = ButtonTone.Plain)
            Spacer(Modifier.weight(1f))
            Text("Rest ${e.restSeconds}s", style = MaterialTheme.typography.labelMedium, color = Hub.colors.inkSoft,
                modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Change rest") { vm.setRest(i, listOf(0, 60, 90, 120, 180).let { l -> l[(l.indexOf(e.restSeconds) + 1) % l.size] }) })
        }
    }
}

@Composable private fun SetCell(value: String, hint: String?, modifier: Modifier, onValue: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    val shape = RoundedCornerShape(8.dp)
    Box(modifier.height(40.dp).clip(shape).background(Hub.colors.surface).border(1.5.dp, Hub.colors.outline, shape), contentAlignment = Alignment.Center) {
        if (text.isEmpty() && hint != null) Text(hint, style = MaterialTheme.typography.bodyMedium, color = Hub.colors.inkSoft.copy(alpha = 0.6f))
        BasicTextField(text, { t -> text = t.filter { it.isDigit() || it == '.' }.take(6); onValue(text) }, singleLine = true,
            textStyle = MaterialTheme.typography.titleSmall.copy(color = Hub.colors.ink, textAlign = TextAlign.Center), cursorBrush = SolidColor(Hub.colors.ink),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
    }
}

// ---------------------------------------------------------------- history + stats (TRN-06/07)
private val ranges = listOf("7 days" to 7L, "30 days" to 30L, "90 days" to 90L, "All" to 36_500L)

@Composable private fun History(vm: TrainingViewModel, ui: TrainingUi) {
    var confirm by remember { mutableStateOf<String?>(null) }
    if (ui.finished.isEmpty()) { EmptyState("No workouts yet", "Finished workouts appear here with their sets and volume."); return }
    ui.finished.forEach { w ->
        HubCard(w.name, trailing = { Pill(w.startedAt.atZone(vm.time.zone).toLocalDate().format(dateFmt), Hub.colors.cyan) }) {
            Text("${dur(Duration.between(w.startedAt, w.finishedAt))} · ${workingSets(w)} sets · ${totalReps(w)} reps · ${kg(volumeKg(w, ui.catalog))} kg",
                style = MaterialTheme.typography.bodyMedium, color = Hub.colors.ink)
            w.exercises.forEach { e ->
                Text("${ui.catalog[e.exerciseId]?.name ?: "Removed exercise"} — " + e.sets.filter { it.working }.joinToString("  ") { s ->
                    listOfNotNull(s.weightKg?.let { "${kg(it)}kg" }, s.reps?.let { "×$it" }, s.seconds?.let { "${it}s" }).joinToString("") }.ifEmpty { "no completed sets" },
                    style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
            }
            HubButton(if (confirm == w.id) "Tap again to delete" else "Delete", { if (confirm == w.id) vm.deleteWorkout(w) else confirm = w.id }, tone = ButtonTone.Danger, compact = true)
        }
    }
}

@Composable private fun Stats(vm: TrainingViewModel, ui: TrainingUi) {
    var range by rememberSaveable { mutableIntStateOf(1) }
    var front by rememberSaveable { mutableStateOf(true) }
    val zone = vm.time.zone; val today = LocalDate.now(zone)
    val list = ui.finished.filter { inRange(it, today.minusDays(ranges[range].second - 1), today, zone) }
    PillTabs(ranges.map { it.first }, range, { range = it }, color = Hub.colors.cyan)
    StatRow(Triple("WORKOUTS", list.size.toString(), null), Triple("TIME", dur(list.fold(Duration.ZERO) { a, w -> a + Duration.between(w.startedAt, w.finishedAt) }), null),
        Triple("SETS", list.sumOf { workingSets(it) }.toString(), null))
    StatRow(Triple("REPS", list.sumOf { totalReps(it) }.toString(), null), Triple("VOLUME", vol(list.sumOf { volumeKg(it, ui.catalog) }), "weighted lifts"))
    if (list.isEmpty()) { EmptyState("Nothing in this range", "No finished workouts — charts stay empty rather than showing zeros."); return }
    val totals = muscleContributions(list, ui.catalog)
    HubCard("Muscle map", trailing = { PillTabs(listOf("Front", "Back"), if (front) 0 else 1, { front = it == 0 }, color = Hub.colors.lime) }) {
        BodyMap(totals.byMuscle, front = front)
        HeatLegend()
        Text("Each completed working set adds 1 to its primary muscles and 0.5 to secondary ones — a heuristic, not a measure of effort.", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
    }
    HubCard("Sets by muscle") {
        Muscle.entries.sortedByDescending { totals.byMuscle[it] ?: 0.0 }.forEach { m ->
            val v = totals.byMuscle[m] ?: 0.0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.label, style = MaterialTheme.typography.bodyMedium, color = Hub.colors.ink, modifier = Modifier.width(100.dp))
                HubBar((v / (totals.byMuscle.values.maxOrNull() ?: 1.0)).toFloat(), Modifier.weight(1f), color = heatColor(heatBin(v)))
                Text(kg(v), style = MaterialTheme.typography.labelMedium, color = Hub.colors.ink, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
            }
        }
        if (totals.unmappedSets > 0) Text("${totals.unmappedSets} set(s) from exercises without muscle tags are not shown.", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
    }
    val prs = personalRecords(list, ui.catalog).values.filter { it.bestE1rm != null }.sortedByDescending { it.bestE1rm }.take(8)
    if (prs.isNotEmpty()) HubCard("Best estimated 1RM in range") {
        prs.forEach { pr -> Row { Text(ui.catalog[pr.exerciseId]?.name ?: "—", style = MaterialTheme.typography.bodyMedium, color = Hub.colors.ink, modifier = Modifier.weight(1f)); Text("${kg(pr.bestE1rm!!)} kg", style = MaterialTheme.typography.titleSmall, color = Hub.colors.ink) } }
    }
}
