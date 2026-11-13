package dev.personalhub.feature.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.personalhub.design.*
import dev.personalhub.usage.api.*
import dev.personalhub.usage.api.Target
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayStyle
import java.util.Locale
import java.util.UUID

data class UsageUi(val settings: FocusSettings = FocusSettings(), val rules: List<LimitRule> = emptyList(), val week: List<UsageDay> = emptyList(),
    val health: TrackerHealth = TrackerHealth()) { val today: UsageDay? get() = week.lastOrNull() }

/** Usage presentation state (FOC-01/04/05/06/10/11). Writes go through the public UsageRepository. */
@OptIn(ExperimentalCoroutinesApi::class)
class UsageViewModel(private val repo: UsageRepository, status: TrackerStatus, private val zone: ZoneId) : ViewModel() {
    private val date = MutableStateFlow(LocalDate.now(zone))
    val state: StateFlow<UsageUi> = combine(repo.settings, repo.rules, date.flatMapLatest { d -> repo.days(d.minusDays(6), d, zone) }, status.health) { s, r, w, h -> UsageUi(s, r, w, h) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UsageUi())
    fun refreshDate() { date.value = LocalDate.now(zone) }
    fun setTracking(on: Boolean) = viewModelScope.launch {
        repo.saveSettings(state.value.settings.copy(tracking = on))
        if (on) repo.closeGap(Instant.now()) else repo.openGap(Instant.now(), GapReason.PAUSED)
    }
    fun setStrict(on: Boolean) = viewModelScope.launch { repo.saveSettings(state.value.settings.copy(strict = on)) }
    fun setCurfew(c: Curfew) = viewModelScope.launch { repo.saveSettings(state.value.settings.copy(curfew = c)) }
    fun saveRule(target: Target?, surface: Surface?, minutes: Int) = viewModelScope.launch { repo.saveRule(LimitRule(UUID.randomUUID().toString(), target, surface, minutes)) }
    fun toggleRule(rule: LimitRule) = viewModelScope.launch { repo.saveRule(rule.copy(enabled = !rule.enabled)) }
    fun deleteRule(rule: LimitRule) = viewModelScope.launch { repo.deleteRule(rule.id) }
    fun used(rule: LimitRule): Duration = usedFor(rule, state.value.today?.intervals.orEmpty())
}

private fun mins(d: Duration) = d.toMinutes().let { if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m" }

@Composable fun UsagePane(vm: UsageViewModel, openAccessibilitySettings: () -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshDate() }
    HealthCard(ui, vm, openAccessibilitySettings)
    val day = ui.today
    if (day != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            BigStat("Reels", mins(day.time(Surface.VIDEO)), caption = "& video")
            BigStat("Feeds", mins(day.time(Surface.TEXT_FEED)), caption = "scrolling")
            BigStat("Scrolls", day.bursts.toString(), caption = day.screens?.let { "≈ %.0f screens".format(it) } ?: "bursts")
        }
        Text("Scrolls are grouped swipes (bursts), not posts or reels watched. Screens are estimated where the app reports distance.",
            style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
        HubCard("Today by app") {
            val total = day.time().toMinutes().coerceAtLeast(1)
            Target.entries.filter { day.time(target = it) > Duration.ZERO }.ifEmpty { null }?.forEach { t ->
                val v = day.time(Surface.VIDEO, t); val f = day.time(Surface.TEXT_FEED, t); val u = day.time(target = t) - v - f
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row { Text(t.label, style = MaterialTheme.typography.titleMedium, color = Hub.colors.ink, modifier = Modifier.weight(1f)); Text(mins(day.time(target = t)), style = MaterialTheme.typography.titleSmall, color = Hub.colors.ink) }
                    HubBar(day.time(target = t).toMinutes() / total.toFloat(), color = Hub.colors.lime)
                    Text("Reels/video ${mins(v)} · feed ${mins(f)}${if (u > Duration.ZERO) " · unclassified ${mins(u)}" else ""}", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
                }
            } ?: Text("No tracked time today.", style = MaterialTheme.typography.bodyLarge, color = Hub.colors.inkSoft)
        }
        WeekChart(ui.week)
    }
    LimitsCard(ui, vm)
    CurfewCard(ui, vm)
    HubCard("Strict mode", trailing = { HubCheck(ui.settings.strict, { vm.setStrict(it) }, "Strict mode", check = HubIcons.Check) }) {
        Text("Removes the 5-minute override on the block screen. Turning tracking off here always remains possible.", style = MaterialTheme.typography.bodyMedium, color = Hub.colors.inkSoft)
    }
}

@Composable private fun HealthCard(ui: UsageUi, vm: UsageViewModel, openSettings: () -> Unit) {
    val h = ui.health
    val (label, color) = when {
        !ui.settings.tracking -> "Off" to Hub.colors.raised
        !h.connected -> "Needs access" to Hub.colors.peach
        else -> "Tracking" to Hub.colors.mint
    }
    HubCard("Tracking", trailing = { Pill(label, color) }) {
        if (!ui.settings.tracking) {
            Text("Measures Instagram, X, Reddit and YouTube — apps and those sites in Chrome or Brave — splitting reels/video from feed scrolling, and enforces your limits and curfew. " +
                "Only app identity, layout IDs and the site address are read; never posts, messages or screenshots. Everything stays on this phone.",
                style = MaterialTheme.typography.bodyMedium, color = Hub.colors.ink)
            HubButton("Turn on tracking", { vm.setTracking(true) }, Modifier.fillMaxWidth(), icon = HubIcons.Eye)
        } else {
            if (!h.connected) {
                Text("Step 2: enable “PersonalHub Focus tracking” in Android Accessibility settings (Installed/Downloaded apps). Until then nothing is measured or blocked.",
                    style = MaterialTheme.typography.bodyMedium, color = Hub.colors.ink)
                HubButton("Open accessibility settings", openSettings, Modifier.fillMaxWidth(), trailingIcon = HubIcons.Arrow)
            } else {
                Text(h.current?.let { "Now: ${it.target.label} · ${it.surface.label}${if (it.source == Source.BROWSER) " (browser)" else ""} · ${h.openSeconds / 60} min" } ?: "Not on a tracked app right now.",
                    style = MaterialTheme.typography.bodyLarge, color = Hub.colors.ink)
                Text("Classifier v${h.classifierVersion}. Unrecognised screens count as unclassified, never guessed.", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
            }
            HubButton("Pause tracking", { vm.setTracking(false) }, tone = ButtonTone.Plain, compact = true)
        }
    }
}

@Composable private fun WeekChart(week: List<UsageDay>) {
    val max = week.maxOfOrNull { it.time().toMinutes() }?.coerceAtLeast(1) ?: 1
    HubCard("Last 7 days") {
        Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            week.forEach { d ->
                val v = d.time(Surface.VIDEO).toMinutes(); val f = d.time(Surface.TEXT_FEED).toMinutes(); val o = d.time().toMinutes() - v - f
                Column(Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = "${d.date.dayOfWeek}: video $v min, feed $f min" }, verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    listOf(o to Hub.colors.raised, f to Hub.colors.cyan, v to Hub.colors.lime).forEach { (m, c) ->
                        if (m > 0) Box(Modifier.fillMaxWidth().height((90f * m / max).dp.coerceAtLeast(3.dp)).background(c).border(1.dp, Hub.colors.outline))
                    }
                    Text(d.date.dayOfWeek.getDisplayName(DayStyle.NARROW, Locale.getDefault()), style = MaterialTheme.typography.labelSmall, color = Hub.colors.inkSoft)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Dot(Hub.colors.lime, 12.dp); Text("Reels/video", style = MaterialTheme.typography.bodySmall, color = Hub.colors.ink)
            Dot(Hub.colors.cyan, 12.dp); Text("Feeds", style = MaterialTheme.typography.bodySmall, color = Hub.colors.ink)
            Dot(Hub.colors.raised, 12.dp); Text("Unclassified", style = MaterialTheme.typography.bodySmall, color = Hub.colors.ink)
        }
    }
}

@Composable private fun LimitsCard(ui: UsageUi, vm: UsageViewModel) {
    var adding by rememberSaveable { mutableStateOf(false) }
    SectionHeader("Daily limits", action = { HubButton("Add", { adding = !adding }, icon = HubIcons.Plus, compact = true) })
    if (adding) {
        var target by rememberSaveable { mutableIntStateOf(0) }
        var surface by rememberSaveable { mutableIntStateOf(1) }
        var minutes by rememberSaveable { mutableStateOf("30") }
        HubCard("New limit") {
            Text("APP", style = MaterialTheme.typography.labelLarge, color = Hub.colors.ink)
            ChoiceCells(listOf("All") + Target.entries.map { it.label.take(5) }, target, { target = it }, description = "App")
            Text("WHAT", style = MaterialTheme.typography.labelLarge, color = Hub.colors.ink)
            ChoiceCells(listOf("Everything", "Reels/video", "Feeds"), surface, { surface = it }, description = "Surface")
            HubField(minutes, { minutes = it.filter(Char::isDigit).take(4) }, "Minutes per day", number = true)
            val m = minutes.toIntOrNull()
            val t = if (target == 0) null else Target.entries[target - 1]
            val s = when (surface) { 1 -> Surface.VIDEO; 2 -> Surface.TEXT_FEED; else -> null }
            Text(if (m == null) "Enter minutes." else "${t?.label ?: "All apps & sites"} · ${s?.label ?: "everything"} · $m min/day · resets at midnight · " +
                if (s == null) "unclassified time counts" else "unclassified time does not count", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
            HubButton("Save limit", { if (m != null && m <= 1440) { vm.saveRule(t, s, m); adding = false } }, Modifier.fillMaxWidth(), enabled = m != null && m <= 1440, icon = HubIcons.Check)
        }
    }
    if (ui.rules.isEmpty() && !adding) EmptyState("No limits yet", "Add one, e.g. 30 minutes of reels a day. When it runs out you're sent home whenever you open it again.")
    ui.rules.forEach { r ->
        val used = vm.used(r)
        HubPanel(color = if (r.enabled) Hub.colors.surface else Hub.colors.raised) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${r.dailyMinutes} min / day", style = MaterialTheme.typography.titleLarge, color = Hub.colors.ink)
                    Text(r.scopeLabel, style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
                }
                HubCheck(r.enabled, { vm.toggleRule(r) }, "Enable limit", check = HubIcons.Check)
                HubIconButton(HubIcons.Trash, "Delete limit", { vm.deleteRule(r) }, color = Hub.colors.peach)
            }
            HubBar(if (r.dailyMinutes == 0) 1f else used.toMinutes() / r.dailyMinutes.toFloat(), color = if (used.toMinutes() >= r.dailyMinutes) Hub.colors.peach else Hub.colors.mint)
            Text("${mins(used)} used today", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
        }
    }
}

@Composable private fun CurfewCard(ui: UsageUi, vm: UsageViewModel) {
    val c = ui.settings.curfew
    var start by remember(c) { mutableStateOf(c.start.toString()) }
    var end by remember(c) { mutableStateOf(c.end.toString()) }
    HubCard("Night cut-off", color = Hub.colors.lavender, trailing = { HubCheck(c.enabled, { vm.setCurfew(c.copy(enabled = it)) }, "Enable cut-off", color = Hub.colors.lime, check = HubIcons.Check) }) {
        Text("Between these times every tracked app and site redirects home, regardless of remaining budget.", style = MaterialTheme.typography.bodyMedium, color = Hub.colors.onAccent)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HubField(start, { start = it.take(5) }, "From", Modifier.weight(1f))
            HubField(end, { end = it.take(5) }, "Until", Modifier.weight(1f))
        }
        val s = runCatching { LocalTime.parse(start.padStart(5, '0')) }.getOrNull(); val e = runCatching { LocalTime.parse(end.padStart(5, '0')) }.getOrNull()
        HubButton("Save times", { if (s != null && e != null && s != e) vm.setCurfew(c.copy(start = s, end = e)) }, enabled = s != null && e != null && s != e, compact = true)
        Text(if (c.enabled) "Active ${c.start.format(DateTimeFormatter.ofPattern("h:mm a"))} – ${c.end.format(DateTimeFormatter.ofPattern("h:mm a"))}" else "Off", style = MaterialTheme.typography.labelMedium, color = Hub.colors.onAccent)
    }
}
