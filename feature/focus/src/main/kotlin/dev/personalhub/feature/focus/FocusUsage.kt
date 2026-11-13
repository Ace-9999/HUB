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
    init {
        // Earlier builds stored free-form limits; fold those that fit the reels/feed grid into it (no silent loss).
        viewModelScope.launch {
            repo.rules.first().filter { r -> r.surface.let { s -> s != null && r.id != ruleId(r.target, s) } }.forEach { r ->
                repo.saveRule(r.copy(id = ruleId(r.target, r.surface!!))); repo.deleteRule(r.id)
            }
        }
    }
    fun setTracking(on: Boolean) = viewModelScope.launch {
        repo.saveSettings(state.value.settings.copy(tracking = on))
        if (on) repo.closeGap(Instant.now()) else repo.openGap(Instant.now(), GapReason.PAUSED)
    }
    fun setStrict(on: Boolean) = viewModelScope.launch { repo.saveSettings(state.value.settings.copy(strict = on)) }
    fun setCurfew(c: Curfew) = viewModelScope.launch { repo.saveSettings(state.value.settings.copy(curfew = c)) }
    fun saveRule(target: Target?, surface: Surface?, minutes: Int) = viewModelScope.launch { repo.saveRule(LimitRule(UUID.randomUUID().toString(), target, surface, minutes)) }
    fun setPerApp(on: Boolean) = viewModelScope.launch { repo.saveSettings(state.value.settings.copy(perApp = on)) }
    /** Sets or clears (null) the limit for one scope in the fixed global/per-app grid. */
    fun setLimit(target: Target?, surface: Surface, minutes: Int?) = viewModelScope.launch {
        val id = ruleId(target, surface)
        if (minutes == null) repo.deleteRule(id) else repo.saveRule(LimitRule(id, target, surface, minutes.coerceIn(0, 1440)))
    }
    fun toggleRule(rule: LimitRule) = viewModelScope.launch { repo.saveRule(rule.copy(enabled = !rule.enabled)) }
    fun deleteRule(rule: LimitRule) = viewModelScope.launch { repo.deleteRule(rule.id) }
    fun used(rule: LimitRule): Duration = usedFor(rule, state.value.today?.intervals.orEmpty())
}

private fun mins(d: Duration) = formatDuration(d)

@Composable fun UsagePane(vm: UsageViewModel, openAccessibilitySettings: () -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshDate() }
    HealthCard(ui, vm, openAccessibilitySettings)
    val day = ui.today
    if (day != null) {
        StatRow(Triple("REELS & VIDEO", mins(day.time(Surface.VIDEO)), null), Triple("FEEDS", mins(day.time(Surface.TEXT_FEED)), null),
            Triple("SCROLLS", day.bursts.toString(), day.screens?.let { "≈ %.0f screens".format(it) }))
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
                Text(h.current?.let { "Now: ${it.target.label} · ${it.surface.label}${if (it.source == Source.BROWSER) " (browser)" else ""} · ${formatDuration(Duration.ofSeconds(h.openSeconds))}" } ?: "Not on a tracked app right now.",
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
    SectionHeader("Daily limits")
    HubPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Per-app time limits", style = MaterialTheme.typography.titleMedium, color = Hub.colors.ink)
                Text(if (ui.settings.perApp) "Each app has its own reels/video and feed limits." else "Off: one reels/video limit and one feed limit shared by all apps and sites.",
                    style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
            }
            HubCheck(ui.settings.perApp, { vm.setPerApp(it) }, "Per-app time limits", check = HubIcons.Check)
        }
    }
    if (!ui.settings.perApp) {
        HubCard("All apps & sites") {
            LimitRow(ui, vm, null, Surface.VIDEO); HubDivider(); LimitRow(ui, vm, null, Surface.TEXT_FEED)
        }
    } else Target.entries.forEach { t ->
        HubCard(t.label) { LimitRow(ui, vm, t, Surface.VIDEO); HubDivider(); LimitRow(ui, vm, t, Surface.TEXT_FEED) }
    }
    val others = ui.rules.filter { r -> r.surface.let { s -> s == null || r.id != ruleId(r.target, s) } }
    if (others.isNotEmpty()) HubCard("Other limits") {
        others.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${r.scopeLabel} · ${r.dailyMinutes} min/day${if ((r.target != null) != ui.settings.perApp) " (inactive in this mode)" else ""}",
                    style = MaterialTheme.typography.bodyMedium, color = Hub.colors.ink, modifier = Modifier.weight(1f))
                HubIconButton(HubIcons.Trash, "Delete limit", { vm.deleteRule(r) }, color = Hub.colors.peach)
            }
        }
    }
    Text("Leave a field empty for no limit. 0 blocks immediately. When a limit runs out, opening it again sends you home until midnight. Unclassified time doesn't count toward these.",
        style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
}

@Composable private fun LimitRow(ui: UsageUi, vm: UsageViewModel, target: Target?, surface: Surface) {
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val rule = ui.rules.firstOrNull { it.id == ruleId(target, surface) }
    var text by remember(rule?.dailyMinutes, rule?.id) { mutableStateOf(rule?.dailyMinutes?.toString() ?: "") }
    val used = vm.used(rule ?: LimitRule("preview", target, surface, 0))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HubField(text, { text = it.filter(Char::isDigit).take(4) }, if (surface == Surface.VIDEO) "Reels & video · min/day" else "Feeds · min/day",
                Modifier.weight(1f), placeholder = "No limit", number = true, committed = rule != null && text == rule.dailyMinutes.toString())
            val value = text.toIntOrNull()
            val changed = value != rule?.dailyMinutes
            HubButton(if (value == null && rule != null) "Clear" else "Set", { vm.setLimit(target, surface, value); focus.clearFocus(); keyboard?.hide() }, enabled = changed && (value == null || value <= 1440), compact = true)
        }
        if (rule != null) {
            val over = used.seconds >= rule.dailyMinutes * 60L
            HubBar(if (rule.dailyMinutes == 0) 1f else used.seconds / (rule.dailyMinutes * 60f), color = if (over) Hub.colors.peach else Hub.colors.mint)
            Text("${formatDuration(used)} of ${rule.dailyMinutes}m used today${if (over) " · limit reached" else ""}", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
        } else Text("${formatDuration(used)} today · no limit", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
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
