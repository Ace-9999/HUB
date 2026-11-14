package dev.personalhub.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.personalhub.college.api.CalendarKind
import dev.personalhub.college.api.noClassesOn
import dev.personalhub.design.*
import dev.personalhub.feature.college.CollegeViewModel
import dev.personalhub.feature.focus.FocusViewModel
import dev.personalhub.feature.focus.UsageViewModel
import dev.personalhub.feature.focus.format
import dev.personalhub.feature.training.TrainingViewModel
import dev.personalhub.focus.api.SectionKind
import dev.personalhub.focus.api.agenda
import dev.personalhub.focus.api.goalMet
import dev.personalhub.usage.api.Surface
import dev.personalhub.usage.api.formatDuration
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/** Small emoji stat tile for the Today glance grid: plain surface, bold value (calm, like the workout history boxes). [color] is unused. */
@Composable private fun Glance(emoji: String, value: String, label: String, @Suppress("UNUSED_PARAMETER") color: Color, modifier: Modifier, onClick: () -> Unit) {
    HubSurface(modifier, color = Hub.colors.surface, radius = 16.dp, shadow = 3.dp, onClick = onClick, onClickLabel = label) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(emoji, style = MaterialTheme.typography.headlineMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Hub.colors.ink, maxLines = 1, softWrap = false)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Hub.colors.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

@Composable fun TodayGlance(focus: FocusViewModel, college: CollegeViewModel, training: TrainingViewModel, usage: UsageViewModel,
    openCollege: () -> Unit, openTasks: () -> Unit, openHabits: () -> Unit, openUsage: () -> Unit, openTrain: () -> Unit) {
    val f by focus.state.collectAsStateWithLifecycle()
    val c by college.state.collectAsStateWithLifecycle()
    val t by training.state.collectAsStateWithLifecycle()
    val u by usage.state.collectAsStateWithLifecycle()
    val today = f.today
    val holiday = c.snapshot.calendarOn(today).any { it.kind == CalendarKind.HOLIDAY }
    val classes = if (c.snapshot.noClassesOn(today)) 0 else c.snapshot.meetingsOn(today.dayOfWeek).size
    val tasksLeft = agenda(f.snapshot, today).filter { it.kind == SectionKind.OVERDUE || it.kind == SectionKind.TODAY }.sumOf { it.items.size }
    val habits = f.snapshot.habits
    val logged = habits.count { h -> f.snapshot.habitEntries.any { it.habitId == h.id && it.day == today } }
    val screen = u.today?.time() ?: Duration.ZERO
    val week = t.finished.count { dev.personalhub.training.api.inRange(it, today.minusDays(6), today, ZoneId.systemDefault()) }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Glance(if (holiday) "🎉" else "📚", if (holiday) "Off" else "$classes", if (holiday) "holiday" else "classes", Hub.colors.cyan, Modifier.weight(1f), openCollege)
        Glance(if (tasksLeft == 0) "✅" else "📝", "$tasksLeft", "tasks left", Hub.colors.lime, Modifier.weight(1f), openTasks)
        Glance("💧", if (habits.isEmpty()) "–" else "$logged/${habits.size}", "habits", Hub.colors.mint, Modifier.weight(1f), openHabits)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Glance("📱", short(screen), "scrolling today", Hub.colors.lavender, Modifier.weight(1f), openUsage)
        Glance("🏋️", "$week", "workouts · 7d", Hub.colors.apricot, Modifier.weight(1f), openTrain)
        Glance("🔥", "${streak(f, today)}", "day streak", Hub.colors.peach, Modifier.weight(1f), openHabits)
    }
}

/** Tile-sized duration: "45s", "12m", "1h 28m" (no seconds once past a minute). */
private fun short(d: Duration): String = when {
    d.toMinutes() < 1 -> "${d.seconds}s"
    d.toHours() < 1 -> "${d.toMinutes()}m"
    else -> "${d.toHours()}h ${d.toMinutes() % 60}m"
}

/** Consecutive days (ending today or yesterday) on which every non-private habit with an entry met its goal. Unknown days break it. */
private fun streak(f: dev.personalhub.feature.focus.FocusUi, today: LocalDate): Int {
    val habits = f.snapshot.habits.filter { !it.sensitive }
    if (habits.isEmpty()) return 0
    fun good(d: LocalDate) = habits.all { h -> goalMet(h, f.snapshot.habitEntries.firstOrNull { it.habitId == h.id && it.day == d }) == true }
    var d = if (good(today)) today else today.minusDays(1)
    var n = 0
    while (n < 365 && good(d)) { n++; d = d.minusDays(1) }
    return n
}

/** Quick habit chips: tap to open Habits; shows today's value or "–" (private habits stay hidden). */
@Composable fun HabitsTodayCard(focus: FocusViewModel, onOpen: () -> Unit) {
    val f by focus.state.collectAsStateWithLifecycle()
    val habits = f.snapshot.habits
    if (habits.isEmpty()) return
    HubCard("💧 Habits today", onClick = onOpen, trailing = { Pill("${habits.count { h -> f.snapshot.habitEntries.any { it.habitId == h.id && it.day == f.today } }}/${habits.size}", Hub.colors.mint) }) {
        Row(Modifier.horizontalScrollRow(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            habits.take(8).forEach { h ->
                val e = f.snapshot.habitEntries.firstOrNull { it.habitId == h.id && it.day == f.today }
                val met = goalMet(h, e)
                Pill(if (h.sensitive) "🔒 Private" else "${h.label} · ${e?.let { h.unit.format(it.value) } ?: "–"}",
                    when (met) { true -> Hub.colors.mint; false -> Hub.colors.apricot; null -> Hub.colors.surface })
            }
        }
    }
}

@Composable private fun Modifier.horizontalScrollRow() = this.horizontalScroll(androidx.compose.foundation.rememberScrollState())

/** Screen-time widget: reels and feed minutes against the active limits. */
@Composable fun ScreenTimeCard(usage: UsageViewModel, onOpen: () -> Unit) {
    val u by usage.state.collectAsStateWithLifecycle()
    if (!u.settings.tracking) return
    val day = u.today ?: return
    val rules = dev.personalhub.usage.api.activeRules(u.rules, u.settings).filter { it.target == null }
    HubCard("📱 Screen time", onClick = onOpen, trailing = { Pill(formatDuration(day.time()), Hub.colors.lavender) }) {
        listOf(Surface.VIDEO to "🎬 Reels", Surface.TEXT_FEED to "📰 Feeds").forEach { (s, label) ->
            val used = day.time(s)
            val limit = rules.firstOrNull { it.surface == s }?.dailyMinutes
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = Hub.colors.ink, modifier = Modifier.width(132.dp))
                HubBar(if (limit == null || limit == 0) (if (used > Duration.ZERO) 1f else 0f).coerceAtMost(if (limit == null) 0.3f else 1f) else used.seconds / (limit * 60f),
                    Modifier.weight(1f), color = if (limit != null && used.seconds >= limit * 60L) Hub.colors.peach else Hub.colors.mint)
                Text(" ${formatDuration(used)}${limit?.let { " / ${it}m" } ?: ""}", style = MaterialTheme.typography.labelMedium, color = Hub.colors.ink)
            }
        }
    }
}
