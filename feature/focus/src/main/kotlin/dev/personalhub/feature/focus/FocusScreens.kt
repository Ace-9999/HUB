package dev.personalhub.feature.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.personalhub.design.*
import dev.personalhub.focus.api.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayStyle
import java.util.Locale

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM")
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")

@Composable fun moodColor(mood: Mood): Color = when (mood) {
    Mood.CALM_CONTENT -> Hub.colors.mint; Mood.GOOD_ENERGISED -> Hub.colors.lime; Mood.FLAT_NUMB -> Color(0xFFE2E0D0)
    Mood.FOGGY_UNREAL -> Hub.colors.lavender; Mood.ANXIOUS_RESTLESS -> Hub.colors.apricot; Mood.LOW_SAD -> Hub.colors.cyan
    Mood.IRRITABLE -> Hub.colors.peach; Mood.LONELY_LEFT_OUT -> Color(0xFFF3C7E6); Mood.BORED -> Color(0xFFF3EAA0)
}

/** Focus destination body (FOC-05/06/08/09). The host supplies scrolling and the title row. */
/** Google Tasks controls supplied by the app (the feature never touches the network adapter directly). */
class GoogleTasksControls(val state: kotlinx.coroutines.flow.StateFlow<GoogleSyncState>, val connected: kotlinx.coroutines.flow.Flow<Boolean>,
    val connect: () -> Unit, val syncNow: () -> Unit, val disconnect: () -> Unit)

@Composable fun FocusContent(vm: FocusViewModel, usage: UsageViewModel, tab: Int, onTab: (Int) -> Unit, openAccessibilitySettings: () -> Unit, google: GoogleTasksControls? = null) {
    val ui by vm.state.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PillTabs(listOf("Tasks", "Habits", "Mood", "Trends", "Usage"), tab, onTab)
        vm.message.collectAsStateWithLifecycle().value?.let { MessageBar(it, vm::dismissMessage) }
        androidx.compose.animation.AnimatedContent(tab, transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, 60)) + androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(280)) { it / 6 * dir }) togetherWith
                (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) + androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(280)) { -it / 6 * dir })
        }, label = "focusTab") { t ->
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (t) {
                    0 -> { google?.let { GoogleTasksCard(it) }; TasksPane(vm, ui) }
                    1 -> HabitsPane(vm, ui)
                    2 -> MoodPane(vm, ui)
                    3 -> TrendsPane(ui)
                    else -> UsagePane(usage, openAccessibilitySettings)
                }
            }
        }
    }
}

@Composable fun MessageBar(text: String, onDismiss: () -> Unit) {
    HubPanel(color = Hub.colors.peach, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Hub.colors.onAccent, modifier = Modifier.weight(1f))
            HubButton("OK", onDismiss, tone = ButtonTone.Plain, compact = true)
        }
    }
}

/** Sticky undo affordance for the host scaffold. */
@Composable fun FocusUndoBar(vm: FocusViewModel, modifier: Modifier = Modifier) {
    val notice by vm.undo.collectAsStateWithLifecycle()
    val current = notice ?: return
    LaunchedEffect(current.id) { kotlinx.coroutines.delay(6_000); if (vm.undo.value?.id == current.id) vm.dismissUndo() }
    HubPanel(modifier, color = Hub.colors.ink, padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(current.message, style = MaterialTheme.typography.bodyMedium, color = Hub.colors.canvas, modifier = Modifier.weight(1f).padding(start = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            HubButton("Undo", vm::performUndo, icon = HubIcons.Undo, compact = true)
        }
    }
}

// ---------------------------------------------------------------- Google Tasks
@Composable private fun GoogleTasksCard(g: GoogleTasksControls) {
    val state by g.state.collectAsStateWithLifecycle()
    val connected by g.connected.collectAsStateWithLifecycle(false)
    val (pill, color) = when {
        !connected -> "Off" to Hub.colors.raised
        state is GoogleSyncState.Syncing -> "Syncing" to Hub.colors.cyan
        state is GoogleSyncState.Failed || state is GoogleSyncState.NeedsConsent -> "Attention" to Hub.colors.peach
        else -> "On" to Hub.colors.mint
    }
    HubCard("Google Tasks", trailing = { Pill(pill, color) }) {
        if (!connected) {
            Text("Two-way sync with your Google Tasks: lists come in, one-off tasks go out, completions and deletes match. Repeating tasks stay on this phone.",
                style = MaterialTheme.typography.bodyMedium, color = Hub.colors.inkSoft)
            HubButton("Connect Google Tasks", g.connect, Modifier.fillMaxWidth(), icon = HubIcons.Sync)
        } else {
            val s = state
            Text(when (s) {
                is GoogleSyncState.Synced -> "Synced " + java.time.Duration.between(s.at, java.time.Instant.now()).toMinutes().let { if (it < 1) "just now" else "$it min ago" } + " · ${s.pulled} in · ${s.pushed} out"
                is GoogleSyncState.Failed -> s.reason
                GoogleSyncState.NeedsConsent -> "Google needs you to approve access again."
                GoogleSyncState.Syncing -> "Syncing…"
                GoogleSyncState.Off -> "Waiting for the first sync."
            }, style = MaterialTheme.typography.bodyMedium, color = Hub.colors.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val consent = s is GoogleSyncState.NeedsConsent
                HubButton(if (consent) "Approve" else "Sync now", if (consent) g.connect else g.syncNow, compact = true, icon = HubIcons.Sync)
                HubButton("Disconnect", g.disconnect, tone = ButtonTone.Plain, compact = true)
            }
        }
    }
}

// ---------------------------------------------------------------- Tasks
@Composable private fun TasksPane(vm: FocusViewModel, ui: FocusUi) {
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val day = ui.today.plusDays(offset.toLong())
    val labels = (0..6).map { when (it) { 0 -> "Today"; 1 -> "Tomorrow"; else -> ui.today.plusDays(it.toLong()).dayOfWeek.getDisplayName(DayStyle.SHORT, Locale.getDefault()) } }
    PillTabs(labels, offset, { offset = it }, color = Hub.colors.cyan)
    val items = ui.tasksOn(day)
    val done = items.count { it.completed }
    SectionHeader(day.format(dayFmt), action = { HubButton("Add", { adding = true; editing = null }, icon = HubIcons.Plus, compact = true) })
    if (items.isNotEmpty()) {
        Text("$done of ${items.size} done", style = MaterialTheme.typography.titleSmall, color = Hub.colors.inkSoft)
        HubBar(done / items.size.toFloat(), color = Hub.colors.mint)
    }
    if (adding || editing != null) TaskEditor(vm, ui, editing, day) { adding = false; editing = null }
    if (items.isEmpty() && !adding) EmptyState("Nothing planned", "Add a task for ${if (offset == 0) "today" else day.format(dayFmt)}. Daily and weekly tasks repeat automatically.")
    items.forEach { item -> TaskRow(item, canComplete = !day.isAfter(ui.today), onDone = { vm.setTaskDone(item, it) }, onEdit = { editing = item.task; adding = false }, onDelete = { vm.deleteTask(item.task) }) }
}

@Composable private fun TaskRow(item: TaskDay, canComplete: Boolean, onDone: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val t = item.task
    HubSurface(Modifier.fillMaxWidth(), color = if (item.completed) Hub.colors.raised else Hub.colors.surface) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (canComplete) HubCheck(item.completed, onDone, if (item.completed) "Mark ${t.title} not done" else "Complete ${t.title}", check = HubIcons.Check)
            else Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Dot(Hub.colors.raised, 14.dp) }
            Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t.title, style = MaterialTheme.typography.titleMedium, color = Hub.colors.ink, fontWeight = if (item.completed) FontWeight.Medium else FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (t.priority == Priority.HIGH) Pill("High", Hub.colors.peach)
                    if (t.priority == Priority.LOW) Pill("Low", Hub.colors.raised)
                    t.dueTime?.let { Pill(it.format(timeFmt), Hub.colors.apricot) }
                    if (t.google) Pill("Google", Hub.colors.cyan)
                    when (t.recurrence) { Recurrence.DAILY -> Pill("Daily", Hub.colors.mint); Recurrence.WEEKLY -> Pill("Weekly", Hub.colors.cyan); Recurrence.NONE -> Unit }
                    if (t.recurrence == Recurrence.NONE && t.carryForward) Pill("From ${item.occurrenceDay.format(dayFmt)}", Hub.colors.lavender)
                }
            }
            HubIconButton(HubIcons.Edit, "Edit ${t.title}", onEdit)
            Spacer(Modifier.width(4.dp))
            HubIconButton(HubIcons.Trash, "Delete ${t.title}", onDelete, color = Hub.colors.peach)
        }
    }
}

@Composable private fun TaskEditor(vm: FocusViewModel, ui: FocusUi, task: Task?, day: LocalDate, close: () -> Unit) {
    var title by rememberSaveable(task?.id) { mutableStateOf(task?.title ?: "") }
    var priority by rememberSaveable(task?.id) { mutableIntStateOf(task?.priority?.ordinal ?: 1) }
    var repeat by rememberSaveable(task?.id) { mutableIntStateOf(task?.recurrence?.ordinal ?: 0) }
    var carry by rememberSaveable(task?.id) { mutableStateOf(task?.carryForward ?: false) }
    var due by rememberSaveable(task?.id) { mutableStateOf(task?.dueTime?.toString() ?: "") }
    HubCard(if (task == null) "New task" else "Edit task", color = Hub.colors.surface) {
        HubField(title, { title = it }, "Title", placeholder = "What needs doing?")
        Text("PRIORITY", style = MaterialTheme.typography.labelLarge, color = Hub.colors.ink)
        ChoiceCells(listOf("Low", "Normal", "High"), priority, { priority = it }, description = "Priority")
        Text("REPEAT", style = MaterialTheme.typography.labelLarge, color = Hub.colors.ink)
        ChoiceCells(listOf("Once", "Daily", "Weekly"), repeat, { repeat = it }, description = "Repeat")
        HubField(due, { due = it }, "Due time (optional)", placeholder = "e.g. 17:30")
        if (repeat == 0) Row(verticalAlignment = Alignment.CenterVertically) {
            HubCheck(carry, { carry = it }, "Carry forward until done", check = HubIcons.Check)
            Text("Carry forward until done", style = MaterialTheme.typography.bodyLarge, color = Hub.colors.ink)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HubButton("Save", {
                vm.saveTask {
                    val time = due.trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(if (it.length == 4) "0$it" else it) }.getOrElse { throw IllegalArgumentException("Use 24-hour time like 07:30 or 17:30.") } }
                    TaskDraft(task?.id, title, task?.firstDay ?: day, Priority.entries[priority], time, Recurrence.entries[repeat], repeat == 0 && carry)
                }
                if (title.isNotBlank()) close()
            }, Modifier.weight(1f), icon = HubIcons.Check)
            HubButton("Cancel", close, tone = ButtonTone.Plain)
        }
    }
}

// ---------------------------------------------------------------- Habits
@Composable private fun HabitsPane(vm: FocusViewModel, ui: FocusUi) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Habit?>(null) }
    SectionHeader("Daily habits", action = { HubButton("Add", { adding = true; editing = null }, icon = HubIcons.Plus, compact = true) })
    Text("A blank day stays “not recorded”. Use None to confirm a zero.", style = MaterialTheme.typography.bodyMedium, color = Hub.colors.inkSoft)
    if (adding || editing != null) HabitEditor(vm, editing) { adding = false; editing = null }
    if (ui.snapshot.habits.isEmpty() && !adding) EmptyState("No habits yet", "Track counts, minutes or yes/no goals. Sensitive habits stay hidden until you reveal them.")
    ui.snapshot.habits.sortedBy { it.createdAt }.forEach { habit -> HabitCard(vm, ui, habit, onEdit = { editing = habit; adding = false }) }
}

@Composable private fun HabitCard(vm: FocusViewModel, ui: FocusUi, habit: Habit, onEdit: () -> Unit) {
    var revealed by remember(habit.id) { mutableStateOf(!habit.sensitive) }
    val today = ui.entry(habit.id, ui.today)
    val met = goalMet(habit, today)
    HubPanel(color = Hub.colors.surface) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (revealed) habit.label else "Private habit · hidden", style = MaterialTheme.typography.titleLarge, color = Hub.colors.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(when (habit.direction) { GoalDirection.INCREASE -> "At least ${habit.unit.format(habit.target)}"; GoalDirection.REDUCE -> "At most ${habit.unit.format(habit.target)}"; GoalDirection.ABSTAIN -> "Abstain" }, Hub.colors.cyan)
                    Pill("Manual", Hub.colors.raised)
                    if (habit.sensitive) Pill("Private", Hub.colors.lavender)
                }
            }
            if (habit.sensitive) HubIconButton(if (revealed) HubIcons.Lock else HubIcons.Eye, if (revealed) "Hide habit" else "Reveal habit", { revealed = !revealed })
        }
        if (revealed) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("TODAY", style = MaterialTheme.typography.labelMedium, color = Hub.colors.inkSoft)
                    Text(today?.let { habit.unit.format(it.value) } ?: "Not recorded", style = MaterialTheme.typography.headlineMedium, color = Hub.colors.ink)
                    met?.let { Text(if (it) "Goal met" else "Goal not met", style = MaterialTheme.typography.labelMedium, color = Hub.colors.inkSoft) }
                }
                when (habit.unit) {
                    HabitUnit.YES_NO -> {
                        HubButton("Yes", { vm.logHabit(habit, ui.today, 1.0) }, tone = if (today?.value == 1.0) ButtonTone.Primary else ButtonTone.Plain, compact = true)
                        HubButton("No", { vm.logHabit(habit, ui.today, 0.0) }, tone = if (today?.value == 0.0) ButtonTone.Primary else ButtonTone.Plain, compact = true)
                    }
                    else -> {
                        val step = if (habit.unit == HabitUnit.MINUTES) 5.0 else 1.0
                        HubIconButton(HubIcons.Minus, "Decrease ${habit.label}", { val v = (today?.value ?: 0.0) - step; vm.logHabit(habit, ui.today, if (v < 0) 0.0 else v) })
                        HubIconButton(HubIcons.Plus, "Increase ${habit.label}", { vm.logHabit(habit, ui.today, (today?.value ?: 0.0) + step) }, color = Hub.colors.lime)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (habit.unit != HabitUnit.YES_NO) HubButton("None today", { vm.logHabit(habit, ui.today, 0.0) }, tone = ButtonTone.Mint, compact = true)
                if (today != null) HubButton("Clear", { vm.logHabit(habit, ui.today, null) }, tone = ButtonTone.Plain, compact = true)
                Spacer(Modifier.weight(1f))
                HubButton("Edit", onEdit, tone = ButtonTone.Plain, compact = true)
                HubButton("Remove", { vm.deleteHabit(habit) }, tone = ButtonTone.Danger, compact = true)
            }
            WeekStrip(ui, habit)
        }
    }
}

@Composable private fun WeekStrip(ui: FocusUi, habit: Habit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        (6 downTo 0).forEach { back ->
            val day = ui.today.minusDays(back.toLong())
            val e = ui.entry(habit.id, day)
            val met = goalMet(habit, e)
            val shape = RoundedCornerShape(8.dp)
            val fill = when (met) { true -> Hub.colors.mint; false -> Hub.colors.apricot; null -> Hub.colors.raised }
            Column(Modifier.weight(1f).clip(shape).background(fill).border(1.5.dp, Hub.colors.outline, shape).padding(vertical = 6.dp)
                .semantics { contentDescription = "${day.dayOfWeek}: ${e?.let { habit.unit.format(it.value) } ?: "not recorded"}" },
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(day.dayOfWeek.getDisplayName(DayStyle.NARROW, Locale.getDefault()), style = MaterialTheme.typography.labelSmall, color = Hub.colors.onAccent)
                Text(e?.let { habit.unit.format(it.value).removeSuffix(" min") } ?: "–", style = MaterialTheme.typography.titleSmall, color = Hub.colors.onAccent, maxLines = 1)
            }
        }
    }
}

@Composable private fun HabitEditor(vm: FocusViewModel, habit: Habit?, close: () -> Unit) {
    var label by rememberSaveable(habit?.id) { mutableStateOf(habit?.label ?: "") }
    var unit by rememberSaveable(habit?.id) { mutableIntStateOf(habit?.unit?.ordinal ?: 0) }
    var direction by rememberSaveable(habit?.id) { mutableIntStateOf(habit?.direction?.ordinal ?: 0) }
    var target by rememberSaveable(habit?.id) { mutableStateOf(habit?.target?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "1") }
    var sensitive by rememberSaveable(habit?.id) { mutableStateOf(habit?.sensitive ?: false) }
    HubCard(if (habit == null) "New habit" else "Edit habit") {
        HubField(label, { label = it }, "Label", placeholder = "e.g. Read, Water, Walk")
        if (habit == null) {
            Text("UNIT", style = MaterialTheme.typography.labelLarge, color = Hub.colors.ink)
            ChoiceCells(HabitUnit.entries.map { it.label.replaceFirstChar(Char::uppercase) }, unit, { unit = it }, description = "Unit")
        } else Text("Unit: ${habit.unit.label} (create a new habit to change units)", style = MaterialTheme.typography.bodyMedium, color = Hub.colors.inkSoft)
        Text("GOAL", style = MaterialTheme.typography.labelLarge, color = Hub.colors.ink)
        ChoiceCells(listOf("Increase", "Reduce", "Abstain"), direction, { direction = it; if (it == 2) target = "0" }, description = "Goal")
        if (direction != 2) HubField(target, { target = it }, "Daily target", number = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            HubCheck(sensitive, { sensitive = it }, "Private habit", color = Hub.colors.lavender, check = HubIcons.Check)
            Text("Private — hide label and values by default", style = MaterialTheme.typography.bodyLarge, color = Hub.colors.ink)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HubButton("Save", {
                vm.saveHabit {
                    val goal = GoalDirection.entries[direction]
                    val value = if (goal == GoalDirection.ABSTAIN) 0.0 else target.trim().toDoubleOrNull() ?: throw IllegalArgumentException("Enter a numeric target.")
                    HabitDraft(habit?.id, label, habit?.unit ?: HabitUnit.entries[unit], goal, value, sensitive)
                }
                if (label.isNotBlank()) close()
            }, Modifier.weight(1f), icon = HubIcons.Check)
            HubButton("Cancel", close, tone = ButtonTone.Plain)
        }
    }
}

// ---------------------------------------------------------------- Mood
private val backdates = listOf("Now" to 0L, "15 min ago" to 15L, "1 hr ago" to 60L, "3 hr ago" to 180L)

@Composable private fun MoodPane(vm: FocusViewModel, ui: FocusUi) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var back by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<MoodEntry?>(null) }
    SectionHeader(if (editing == null) "How are you?" else "Edit entry")
    Text("Every save adds a timestamped entry. Log as often as you like.", style = MaterialTheme.typography.bodyMedium, color = Hub.colors.inkSoft)
    MoodGrid(selected?.let(Mood::fromCode)) { selected = it.code }
    if (editing == null) PillTabs(backdates.map { it.first }, back, { back = it }, color = Hub.colors.apricot)
    HubField(note, { note = it }, "Private note (optional)", singleLine = false)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HubButton(if (editing == null) "Log mood" else "Save changes", {
            val mood = selected?.let(Mood::fromCode) ?: return@HubButton
            val at = editing?.occurredAt ?: vm.now().minus(Duration.ofMinutes(backdates[back].second))
            vm.saveMood(editing?.id, mood, at, note)
            selected = null; note = ""; back = 0; editing = null
        }, Modifier.weight(1f), enabled = selected != null, trailingIcon = HubIcons.Arrow)
        if (editing != null) HubButton("Cancel", { editing = null; selected = null; note = "" }, tone = ButtonTone.Plain)
    }
    val todays = ui.recentMoods.filter { it.occurredAt.atZone(ui.zone).toLocalDate() == ui.today }
    SectionHeader("Today · ${todays.size}")
    if (ui.recentMoods.isEmpty()) EmptyState("No entries yet", "Pick a mood above. Entries stay on this device.")
    ui.recentMoods.take(20).forEach { entry ->
        MoodRow(entry, ui.zone, onEdit = { editing = entry; selected = entry.mood.code; note = entry.note ?: "" }, onDelete = { vm.deleteMood(entry) })
    }
}

@Composable fun MoodGrid(selected: Mood?, onSelect: (Mood) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Mood.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { mood ->
                    HubSurface(Modifier.weight(1f), color = if (mood == selected) moodColor(mood) else Hub.colors.surface, radius = 14.dp, shadow = if (mood == selected) 4.dp else 2.dp,
                        onClick = { onSelect(mood) }, onClickLabel = mood.label) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(mood.emoji, style = MaterialTheme.typography.headlineMedium)
                            Text(mood.label, style = MaterialTheme.typography.labelSmall, color = if (mood == selected) Hub.colors.onAccent else Hub.colors.ink, textAlign = TextAlign.Center, minLines = 2)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun MoodRow(entry: MoodEntry, zone: ZoneId, onEdit: () -> Unit, onDelete: () -> Unit) {
    HubSurface(Modifier.fillMaxWidth(), shadow = 3.dp) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(moodColor(entry.mood)).border(1.5.dp, Hub.colors.outline, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text(entry.mood.emoji, style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f)) {
                Text(entry.mood.label, style = MaterialTheme.typography.titleMedium, color = Hub.colors.ink)
                val at = entry.occurredAt.atZone(zone)
                Text("${at.toLocalDate().format(dayFmt)} · ${at.toLocalTime().format(timeFmt)}${if (entry.note != null) " · note" else ""}", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
            }
            HubIconButton(HubIcons.Edit, "Edit mood entry", onEdit)
            HubIconButton(HubIcons.Trash, "Delete mood entry", onDelete, color = Hub.colors.peach)
        }
    }
}

// ---------------------------------------------------------------- Trends
@Composable private fun TrendsPane(ui: FocusUi) {
    var rangeIndex by rememberSaveable { mutableIntStateOf(1) }
    val range = TrendRange.entries[rangeIndex].range(ui.today, ui.zone)
    PillTabs(TrendRange.entries.map { it.label }, rangeIndex, { rangeIndex = it }, color = Hub.colors.cyan)
    val dist = moodDistribution(ui.snapshot.moods, range)
    val days = range.start.datesUntil(range.endInclusive.plusDays(1)).count()
    HubCard("Mood distribution", trailing = { Pill("${dist.observationCount} entries", Hub.colors.lavender) }) {
        Text("Logged on ${dist.recordedDays} of $days days. Categories are counted, never averaged into a score.", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
        if (dist.observationCount == 0) Text("No mood entries in this range.", style = MaterialTheme.typography.bodyLarge, color = Hub.colors.ink)
        else Mood.entries.filter { (dist.counts[it] ?: 0) > 0 }.sortedByDescending { dist.counts[it] }.forEach { mood ->
            val n = dist.counts[mood] ?: 0
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(mood.emoji, style = MaterialTheme.typography.titleMedium)
                Column(Modifier.weight(1f)) {
                    Text("${mood.label} · $n", style = MaterialTheme.typography.labelMedium, color = Hub.colors.ink)
                    HubBar(n / dist.observationCount.toFloat(), color = moodColor(mood))
                }
            }
        }
    }
    if (rangeIndex == 0) {
        val events = moodEvents(ui.snapshot.moods, range)
        HubCard("Day timeline") {
            if (events.isEmpty()) Text("Nothing logged today.", style = MaterialTheme.typography.bodyLarge, color = Hub.colors.inkSoft)
            events.forEach { e -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(e.occurredAt.atZone(ui.zone).toLocalTime().format(timeFmt), style = MaterialTheme.typography.labelMedium, color = Hub.colors.inkSoft, modifier = Modifier.width(72.dp))
                Dot(moodColor(e.mood), 14.dp); Text("${e.mood.emoji} ${e.mood.label}", style = MaterialTheme.typography.bodyLarge, color = Hub.colors.ink)
            } }
        }
    } else MoodDailyStack(ui, range)
    ui.snapshot.habits.forEach { habit -> HabitTrend(habit, ui, range) }
}

@Composable private fun MoodDailyStack(ui: FocusUi, range: dev.personalhub.core.DateRange) {
    val days = range.start.datesUntil(range.endInclusive.plusDays(1)).toList()
    val byDay = days.associateWith { d -> moodEvents(ui.snapshot.moods, dev.personalhub.core.DateRange(d, d, ui.zone)) }
    val max = (byDay.values.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    HubCard("Entries per day") {
        Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(if (days.size > 10) 2.dp else 6.dp), verticalAlignment = Alignment.Bottom) {
            days.forEach { d ->
                val events = byDay[d].orEmpty()
                Column(Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = "$d: ${events.size} entries" }, verticalArrangement = Arrangement.Bottom) {
                    if (events.isEmpty()) Box(Modifier.fillMaxWidth().height(3.dp).background(Hub.colors.dot))
                    events.sortedBy { it.mood.ordinal }.forEach { e -> Box(Modifier.fillMaxWidth().weight(1f / max, fill = false).height((100f / max).dp).background(moodColor(e.mood)).border(0.5.dp, Hub.colors.outline)) }
                }
            }
        }
        Text("Each block is one entry; empty days were not logged.", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
    }
}

@Composable private fun HabitTrend(habit: Habit, ui: FocusUi, range: dev.personalhub.core.DateRange) {
    val series = habitSeries(habit, ui.snapshot.habitEntries, range)
    val known = series.points.mapNotNull { it.value }
    val max = (known.maxOrNull() ?: 0.0).coerceAtLeast(habit.target).coerceAtLeast(1.0)
    HubCard(if (habit.sensitive) "Private habit" else habit.label, trailing = { Pill("${known.size}/${series.points.size} days logged", Hub.colors.apricot) }) {
        if (habit.sensitive) Text("Values hidden. Reveal on the Habits tab.", style = MaterialTheme.typography.bodyMedium, color = Hub.colors.inkSoft)
        else {
            Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(if (series.points.size > 10) 2.dp else 6.dp), verticalAlignment = Alignment.Bottom) {
                series.points.forEach { p ->
                    Box(Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = "${p.day}: ${p.value?.let { habit.unit.format(it) } ?: "not recorded"}" }, contentAlignment = Alignment.BottomCenter) {
                        if (p.value == null) Text("·", color = Hub.colors.inkSoft)
                        else Box(Modifier.fillMaxWidth().fillMaxHeight((p.value!! / max).toFloat().coerceAtLeast(0.03f)).background(if (goalMet(habit, HabitEntry(habit.id, p.day, p.value!!, p.value == 0.0, null, Instant.EPOCH)) == true) Hub.colors.mint else Hub.colors.apricot).border(1.dp, Hub.colors.outline))
                    }
                }
            }
            Text(if (known.isEmpty()) "No entries in range." else "Total ${habit.unit.format(known.sum())} · dots mark days not recorded (unknown, not zero).", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
        }
    }
}

/** Compact Today card: today's tasks with completion controls. */
@Composable fun FocusTodayCard(vm: FocusViewModel, onOpen: () -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val items = ui.tasksOn(ui.today)
    HubCard("Today's tasks", trailing = { Pill(if (items.isEmpty()) "None" else "${items.count { it.completed }}/${items.size}", Hub.colors.mint) }, onClick = onOpen) {
        if (items.isEmpty()) Text("Nothing planned. Tap to add a task.", style = MaterialTheme.typography.bodyLarge, color = Hub.colors.inkSoft)
        items.take(5).forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                HubCheck(item.completed, { vm.setTaskDone(item, it) }, "Complete ${item.task.title}", check = HubIcons.Check)
                Text(item.task.title, style = MaterialTheme.typography.bodyLarge, color = Hub.colors.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                item.task.dueTime?.let { Text(it.format(timeFmt), style = MaterialTheme.typography.labelMedium, color = Hub.colors.inkSoft) }
            }
        }
        if (items.size > 5) Text("+${items.size - 5} more", style = MaterialTheme.typography.labelMedium, color = Hub.colors.inkSoft)
    }
}

@Composable fun MoodQuickCard(vm: FocusViewModel, onOpen: () -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val last = ui.recentMoods.firstOrNull()
    HubCard("Check in", color = Hub.colors.lavender, onClick = onOpen, trailing = { Icon(HubIcons.Arrow, null, tint = Hub.colors.onAccent) }) {
        Text(last?.let { "Last: ${it.mood.emoji} ${it.mood.label} at ${it.occurredAt.atZone(ui.zone).toLocalTime().format(timeFmt)}" } ?: "How are you feeling right now?",
            style = MaterialTheme.typography.bodyLarge, color = Hub.colors.onAccent)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Mood.CALM_CONTENT, Mood.GOOD_ENERGISED, Mood.ANXIOUS_RESTLESS, Mood.LOW_SAD, Mood.BORED).forEach { m ->
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Hub.colors.surface).border(1.5.dp, Hub.colors.outline, RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = "Log ${m.label}") { vm.saveMood(null, m, vm.now(), null) }, contentAlignment = Alignment.Center) { Text(m.emoji, style = MaterialTheme.typography.titleLarge) }
            }
        }
    }
}
