package dev.personalhub.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.personalhub.app.update.UpdateState
import dev.personalhub.design.*
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Bell with an unread badge, shown beside Settings on every destination header. */
@Composable fun BellButton(app: PersonalHubApplication, onClick: () -> Unit) {
    val items by app.inbox.state.collectAsStateWithLifecycle()
    val unread = items.count { !it.read }
    Box {
        HubIconButton(HubIcons.Bell, if (unread > 0) "Notifications, $unread new" else "Notifications", onClick)
        if (unread > 0) Box(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-6).dp).size(22.dp).clip(CircleShape).background(Hub.colors.danger)
            .border(1.5.dp, Hub.colors.outline, CircleShape), contentAlignment = Alignment.Center) {
            Text(if (unread > 9) "9+" else "$unread", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White)
        }
    }
}

private val inboxFmt = DateTimeFormatter.ofPattern("d MMM, h:mm a").withZone(ZoneId.systemDefault())

/** The bell inbox: newest first; opening it marks everything read. */
@Composable fun InboxScreen(app: PersonalHubApplication, close: () -> Unit, open: (String) -> Unit) {
    val items by app.inbox.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // Snapshot which were unread on arrival so they stay highlighted while the page is open.
    val fresh = remember { items.filter { !it.read }.map { it.id }.toSet() }
    LaunchedEffect(Unit) { app.inbox.markAllRead() }
    ScreenTitle("Notifications", onBack = close, back = HubIcons.Back)
    if (items.isEmpty()) { EmptyState("All caught up 🎉", "New marks found during a sync and other updates appear here."); return }
    items.forEach { i ->
        HubCard(i.title, color = if (i.id in fresh) Hub.colors.lime else Hub.colors.surface, onClick = i.target?.let { t -> { open(t) } },
            trailing = { Pill(inboxFmt.format(i.at), Hub.colors.surface) }) {
            Text(i.body, style = MaterialTheme.typography.bodyMedium, color = if (i.id in fresh) Hub.colors.onAccent else Hub.colors.ink)
        }
    }
    HubButton("Clear all", { scope.launch { app.inbox.clear() } }, tone = ButtonTone.Plain, compact = true, icon = HubIcons.Trash)
}

@Composable private fun ToggleRow(title: String, body: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Hub.colors.ink)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
        }
        HubCheck(on, onChange, title, check = HubIcons.Check)
    }
}

/** Settings → Notifications: one switch per kind (v1.1 NOT-01). */
@Composable fun NotificationSettings(app: PersonalHubApplication, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted = it }
    fun ask() { if (!granted && Build.VERSION.SDK_INT >= 33) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    val classes by app.settings.notifyClasses.collectAsStateWithLifecycle(false)
    val marks by app.settings.notifyMarks.collectAsStateWithLifecycle(false)
    val rest by app.settings.notifyRest.collectAsStateWithLifecycle(true)
    val remind by app.settings.taskReminders.collectAsStateWithLifecycle(false)
    val nudge by app.settings.habitNudge.collectAsStateWithLifecycle(false)
    val nudgeTime by app.settings.habitNudgeTime.collectAsStateWithLifecycle("21:00")
    ScreenTitle("Notifications", onBack = back, back = HubIcons.Back)
    if (!granted) HubCard("Notifications are blocked", color = Hub.colors.peach) {
        Text("Android hasn't allowed HUB to show notifications yet. The bell inbox still works.", style = MaterialTheme.typography.bodyMedium, color = Hub.colors.onAccent)
        HubButton("Allow notifications", { if (Build.VERSION.SDK_INT >= 33) ask() else context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)) },
            Modifier.fillMaxWidth(), tone = ButtonTone.Plain, icon = HubIcons.Bell)
    }
    HubCard("🎓 College") {
        ToggleRow("Next class", "10 minutes before each class. If another class is still running then, right after it ends. Holidays are skipped.", classes) { on ->
            scope.launch { app.settings.notifyClasses(on); if (on) ask(); CollegeNotices.scheduleClassAlert(app) } }
        ToggleRow("New marks", "When a sync finds new or changed marks. They always appear in the 🔔 inbox; this adds a phone notification.", marks) { on ->
            scope.launch { app.settings.notifyMarks(on); if (on) ask() } }
    }
    HubCard("🏋️ Train") {
        ToggleRow("Rest finished", "When the rest timer between sets runs out.", rest) { on -> scope.launch { app.settings.notifyRest(on); if (on) ask() } }
    }
    HubCard("✅ Focus") {
        ToggleRow("Task due times", "At each task's due time.", remind) { on -> scope.launch { app.settings.taskReminders(on); if (on) ask(); Reminders.reschedule(app) } }
        ToggleRow("Daily habit check-in", "At $nudgeTime if habits aren't logged. Habit names are never shown.", nudge) { on ->
            scope.launch { app.settings.habitNudge(on); if (on) ask(); Reminders.scheduleNudge(app) } }
        if (nudge) {
            val times = listOf("08:00", "13:00", "18:00", "21:00", "22:30")
            PillTabs(times, times.indexOf(nudgeTime), { i -> scope.launch { app.settings.habitNudge(true, times[i]); Reminders.scheduleNudge(app) } }, color = Hub.colors.cyan)
        }
    }
    Text("Notifications may arrive a few minutes late under battery saving. More kinds are coming in later versions.", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
}

/** Settings card: app version, auto-update switch and status (v1.1 UPD). */
@Composable fun UpdatesCard(app: PersonalHubApplication) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val auto by app.settings.autoUpdate.collectAsStateWithLifecycle(true)
    val state by app.updates.state.collectAsStateWithLifecycle()
    var canInstall by remember { mutableStateOf(app.updates.canInstall()) }
    LifecycleResume { canInstall = app.updates.canInstall() }
    HubCard("⬆️ App updates", trailing = { Pill("v${BuildConfig.VERSION_NAME}", Hub.colors.cyan) }) {
        ToggleRow("Install updates automatically", "Checks the project's public GitHub releases every few hours. Updates install while you're not using HUB and keep all your data and permissions.", auto) { on ->
            scope.launch { app.settings.autoUpdate(on) } }
        Text(when (val s = state) {
            UpdateState.Idle -> "Not checked yet this session."
            UpdateState.Checking -> "Checking…"
            is UpdateState.UpToDate -> "You're on the latest version."
            is UpdateState.Downloading -> "Downloading v${s.version}… ${s.percent}%"
            is UpdateState.Scheduled -> if (s.whenIdle) "v${s.version} is ready and will install when you leave HUB." else "Installing v${s.version}… HUB will restart."
            is UpdateState.NeedsApproval -> "v${s.version} needs your one-time approval — tap the notification or Install now."
            is UpdateState.Failed -> s.reason
        }, style = MaterialTheme.typography.bodyMedium, color = Hub.colors.ink)
        if (!canInstall) {
            Text("One-time step: allow HUB to install its own updates. After that, updates need no taps.", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
            HubButton("Allow HUB to install updates", {
                context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            }, Modifier.fillMaxWidth(), tone = ButtonTone.Plain, icon = HubIcons.Lock)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val busy = state is UpdateState.Checking || state is UpdateState.Downloading
            HubButton("Check now", { app.scope.launch { app.updates.run(now = false) } }, Modifier.weight(1f), enabled = !busy, tone = ButtonTone.Plain, compact = true, icon = HubIcons.Sync)
            HubButton("Install now", { app.scope.launch { app.updates.run(now = true) } }, Modifier.weight(1f), enabled = !busy, compact = true, icon = HubIcons.Up)
        }
    }
}

/** Runs [block] whenever the screen resumes (e.g. returning from a system settings page). */
@Composable private fun LifecycleResume(block: () -> Unit) {
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) block() }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
}

/** Bundled fun facts (assets/fun_facts.txt, 500 lines) for days off; one is picked at random each time Today opens. */
object FunFacts {
    @Volatile private var cache: List<String>? = null
    fun random(context: android.content.Context): String? {
        val list = cache ?: runCatching { context.assets.open("fun_facts.txt").bufferedReader().readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList()).also { cache = it }
        return list.randomOrNull()
    }
}
