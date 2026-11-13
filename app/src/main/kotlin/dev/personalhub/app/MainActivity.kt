package dev.personalhub.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.personalhub.core.*
import dev.personalhub.design.*
import dev.personalhub.feature.college.*
import dev.personalhub.feature.focus.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import androidx.lifecycle.lifecycleScope
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    companion object { const val OPEN_USAGE = "openUsage" }
    private val consent = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val app = application as PersonalHubApplication
        val token = dev.personalhub.googletasks.GoogleAuth.tokenFromConsent(this, r.data)
        lifecycleScope.launch { if (token != null) { app.googleTasks.sync(token); app.googleTasks.schedule(this@MainActivity) } }
    }
    /** Enables sync and runs it; launches Google's consent screen when needed. */
    fun connectGoogleTasks() {
        val app = application as PersonalHubApplication
        lifecycleScope.launch {
            app.settings.googleTasks(true)
            when (val a = runCatching { dev.personalhub.googletasks.GoogleAuth.authorize(this@MainActivity) }.getOrNull()) {
                is dev.personalhub.googletasks.Auth.NeedsConsent -> consent.launch(androidx.activity.result.IntentSenderRequest.Builder(a.intent.intentSender).build())
                is dev.personalhub.googletasks.Auth.Token -> { app.googleTasks.sync(a.value); app.googleTasks.schedule(this@MainActivity) }
                null -> app.googleTasks.sync()
            }
        }
    }
    /** Device-credential/biometric prompt (platform API; no extra library). Unlock lasts until the app leaves the foreground. */
    fun unlockFocus() {
        val km = getSystemService(android.app.KeyguardManager::class.java)
        if (!km.isDeviceSecure) { FocusLock.unlock(); return }
        val builder = android.hardware.biometrics.BiometricPrompt.Builder(this).setTitle("Unlock Focus").setSubtitle("Tasks, habits and mood stay private")
        if (android.os.Build.VERSION.SDK_INT >= 30) builder.setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK or android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        else @Suppress("DEPRECATION") builder.setDeviceCredentialAllowed(true)
        builder.build().authenticate(android.os.CancellationSignal(), mainExecutor, object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) { FocusLock.unlock() }
        })
    }
    override fun onStop() { super.onStop(); if (!isChangingConfigurations) FocusLock.lock() }
    override fun onStart() {
        super.onStart()
        val app = application as PersonalHubApplication
        lifecycleScope.launch { if (app.googleTasks.enabled()) app.googleTasks.syncSoon(this@MainActivity, 1) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PersonalHubApplication
        setContent {
            val theme by app.settings.theme.collectAsStateWithLifecycle(SettingsStore.DEFAULT_THEME)
            PersonalHubTheme(theme) {
                val dark = Hub.colors.dark
                val view = androidx.compose.ui.platform.LocalView.current
                SideEffect { androidx.core.view.WindowCompat.getInsetsController(window, view).run { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark } }
                HubApp(app, theme, intent?.getBooleanExtra(OPEN_USAGE, false) == true)
            }
        }
    }
}

val sections = SectionRegistry(listOf(Section("today", "Today"), Section("college", "College"), Section("training", "Train"), Section("focus", "Focus")))
private val navItems = listOf(NavItem("today", "Today", HubIcons.Home), NavItem("college", "College", HubIcons.School),
    NavItem("training", "Train", HubIcons.Dumbbell), NavItem("focus", "Focus", HubIcons.Bolt))

private val tabs = listOf("today", "college", "training", "focus")

@Composable fun HubApp(app: PersonalHubApplication, theme: String, openUsage: Boolean = false) {
    // Bottom destinations are pages of a horizontal pager: swipe or tap to move between Today, College, Train and Focus.
    val pager = rememberPagerState(initialPage = if (openUsage) 3 else 0) { tabs.size }
    var settings by rememberSaveable { mutableStateOf(false) }
    var collegePage by rememberSaveable { mutableStateOf(CollegePage.OVERVIEW) }
    var focusTab by rememberSaveable { mutableIntStateOf(if (openUsage) 4 else 0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus: FocusViewModel = viewModel(factory = viewModelFactory { initializer { FocusViewModel(app.focus, app.time) } })
    val usage: UsageViewModel = viewModel(factory = viewModelFactory { initializer { UsageViewModel(app.usage, app.usageEngine, app.time.zone) } })
    val openAccessibility = { context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    val training: dev.personalhub.feature.training.TrainingViewModel = viewModel(factory = viewModelFactory { initializer { dev.personalhub.feature.training.TrainingViewModel(app.training, app.time, app.restAlerts) } })
    val workoutDays = remember {
        training.state.map { ui ->
            ui.finished.groupBy { it.startedAt.atZone(app.time.zone).toLocalDate() }.map { (d, ws) ->
                dev.personalhub.core.WorkoutDay(d, ws.size, ws.sumOf { java.time.Duration.between(it.startedAt, it.finishedAt).toMinutes() }, ws.sumOf { dev.personalhub.training.api.workingSets(it) })
            }
        }
    }
    var trainNav by rememberSaveable(stateSaver = TrainNavSaver) { mutableStateOf(dev.personalhub.feature.training.TrainNav()) }
    val college: CollegeViewModel = viewModel(factory = viewModelFactory { initializer { CollegeViewModel(app.college, app.time) } })
    val connect: (String?) -> Unit = { semester -> context.startActivity(Intent(context, CollegeConnectActivity::class.java).apply { semester?.let { putExtra(CollegeConnectActivity.SEMESTER, it) } }) }
    val selected = tabs[pager.currentPage]
    val googleControls = remember {
        GoogleTasksControls(app.googleTasks.state, app.settings.googleTasks,
            connect = { (context as? MainActivity)?.connectGoogleTasks() },
            syncNow = { scope.launch { app.googleTasks.sync() } },
            disconnect = { scope.launch { app.settings.googleTasks(false); app.googleTasks.cancel(context) } })
    }
    val go: (String) -> Unit = { tab -> settings = false; scope.launch { pager.animateScrollToPage(tabs.indexOf(tab)) } }

    BackHandler(enabled = settings || collegePage != CollegePage.OVERVIEW || trainNav.page != dev.personalhub.feature.training.TrainPage.OVERVIEW || pager.currentPage != 0) {
        when {
            settings -> settings = false
            selected == "college" && collegePage != CollegePage.OVERVIEW -> collegePage = CollegePage.OVERVIEW
            selected == "training" && trainNav.page != dev.personalhub.feature.training.TrainPage.OVERVIEW ->
                trainNav = trainNav.pickFor?.let { dev.personalhub.feature.training.TrainNav(it, trainNav.arg) }
                    ?: if (trainNav.page == dev.personalhub.feature.training.TrainPage.EXERCISE) dev.personalhub.feature.training.TrainNav(dev.personalhub.feature.training.TrainPage.CATALOG)
                    else dev.personalhub.feature.training.TrainNav()
            else -> go("today")
        }
    }

    HubCanvas {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !settings && !(selected == "college" && collegePage != CollegePage.OVERVIEW) && !(selected == "training" && trainNav.page != dev.personalhub.feature.training.TrainPage.OVERVIEW),
                    beyondViewportPageCount = 0, key = { tabs[it] }) { index ->
                    PageColumn {
                        when (tabs[index]) {
                            "today" -> TodayScreen(focus, college, training, onSettings = { settings = true }, openCollege = { collegePage = it; go("college") }, openFocus = { focusTab = it; go("focus") },
                                openTrain = { trainNav = it; go("training") })
                            "college" -> AnimatedContent(collegePage, transitionSpec = { subPageTransition(initialState == CollegePage.OVERVIEW) }, label = "college") { page ->
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    if (page == CollegePage.OVERVIEW) Header("College", college.state.collectAsStateWithLifecycle().value.snapshot.semester?.name ?: "VTOP · Chennai") { settings = true }
                                    CollegeContent(college, page, { collegePage = it }, connect)
                                }
                            }
                            "training" -> {
                                if (trainNav.page == dev.personalhub.feature.training.TrainPage.OVERVIEW) Header("Train", "Routines & workouts") { settings = true }
                                dev.personalhub.feature.training.TrainingContent(training, trainNav) { trainNav = it }
                            }
                            else -> {
                                Header("Focus", "Tasks, habits & mood") { settings = true }
                                val locked by app.settings.focusLock.collectAsStateWithLifecycle(false)
                                if (locked && !FocusLock.unlocked) FocusLockedCard { (context as? MainActivity)?.unlockFocus() }
                                else FocusContent(focus, usage, focusTab, { focusTab = it }, openAccessibility, googleControls, workoutDays)
                            }
                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(visible = settings, enter = slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(200)), exit = slideOutHorizontally(tween(220)) { it / 4 } + fadeOut(tween(160))) {
                    HubCanvas { PageColumn { SettingsScreen(app, theme) { settings = false } } }
                }
                // Canvas-coloured scrim keeps scrolled content out from under the status bar icons.
                Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Hub.colors.canvas).align(Alignment.TopCenter))
                FocusUndoBar(focus, Modifier.align(Alignment.BottomCenter).padding(horizontal = Hub.ScreenPadding, vertical = 12.dp))
            }
            HubBottomBar(navItems, if (settings) null else selected, { tab -> if (tab == "college" && selected == "college") collegePage = CollegePage.OVERVIEW; go(tab) })
        }
    }
}

/** One scrollable page; each pager page keeps its own scroll position. */
@Composable private fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = Hub.ScreenPadding).padding(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

/** College sub-pages push in from the right and slide back on return. */
private fun subPageTransition(forward: Boolean): ContentTransform {
    val dir = if (forward) 1 else -1
    return (fadeIn(tween(200, 40)) + slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 6 * dir }) togetherWith
        (fadeOut(tween(120)) + slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 10 * dir })
}

@Composable private fun Header(title: String, subtitle: String, onSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.displayMedium, color = Hub.colors.ink)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = Hub.colors.inkSoft)
        }
        HubIconButton(HubIcons.Settings, "Settings", onSettings)
    }
}

private val TrainNavSaver = androidx.compose.runtime.saveable.listSaver<dev.personalhub.feature.training.TrainNav, String?>(
    save = { listOf(it.page.name, it.arg, it.pickFor?.name) },
    restore = { dev.personalhub.feature.training.TrainNav(dev.personalhub.feature.training.TrainPage.valueOf(it[0]!!), it[1], it[2]?.let(dev.personalhub.feature.training.TrainPage::valueOf)) })

@Composable private fun TodayScreen(focus: FocusViewModel, college: CollegeViewModel, training: dev.personalhub.feature.training.TrainingViewModel, onSettings: () -> Unit,
    openCollege: (CollegePage) -> Unit, openFocus: (Int) -> Unit, openTrain: (dev.personalhub.feature.training.TrainNav) -> Unit) {
    val now = LocalTime.now()
    val greeting = when (now.hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Pill(AppTime().today().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")).uppercase(), Hub.colors.lime)
        Spacer(Modifier.weight(1f))
        HubIconButton(HubIcons.Settings, "Settings", onSettings)
    }
    Text(greeting, style = MaterialTheme.typography.displayLarge, color = Hub.colors.ink)
    Text("Your day, in one place.", style = MaterialTheme.typography.bodyLarge, color = Hub.colors.inkSoft)
    CollegeTodayCards(college, openCollege)
    FocusTodayCard(focus) { openFocus(0) }
    MoodQuickCard(focus) { openFocus(2) }
    val t by training.state.collectAsStateWithLifecycle()
    val active = t.active
    val week = t.finished.count { dev.personalhub.training.api.inRange(it, java.time.LocalDate.now().minusDays(6), java.time.LocalDate.now(), java.time.ZoneId.systemDefault()) }
    HubCard(if (active != null) "Workout in progress" else "Training", color = if (active != null) Hub.colors.lime else Hub.colors.surface,
        trailing = { Pill("$week this week", Hub.colors.mint) },
        onClick = { openTrain(dev.personalhub.feature.training.TrainNav(if (active != null) dev.personalhub.feature.training.TrainPage.WORKOUT else dev.personalhub.feature.training.TrainPage.OVERVIEW)) }) {
        Text(active?.let { "${it.name} — tap to resume." } ?: t.finished.firstOrNull()?.let { "Last: ${it.name}, ${it.startedAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("EEE d MMM"))}." }
            ?: "No workouts yet. Build a routine in Train.", style = MaterialTheme.typography.bodyLarge, color = if (active != null) Hub.colors.onAccent else Hub.colors.inkSoft)
    }
}


/** Session unlock state for the Focus lock; observable so the page re-composes on unlock. */
object FocusLock {
    var unlocked by mutableStateOf(false); private set
    fun unlock() { unlocked = true }
    fun lock() { unlocked = false }
}

@Composable private fun FocusLockedCard(onUnlock: () -> Unit) {
    EmptyState("Focus is locked", "Tasks, habits and mood are protected. Unlock with your fingerprint or screen-lock PIN.") {
        HubButton("Unlock", onUnlock, Modifier.fillMaxWidth(), icon = HubIcons.Lock)
    }
}

@Composable private fun SettingsScreen(app: PersonalHubApplication, theme: String, close: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    ScreenTitle("Settings", onBack = close, back = HubIcons.Back)
    val lock by app.settings.focusLock.collectAsStateWithLifecycle(false)
    val remind by app.settings.taskReminders.collectAsStateWithLifecycle(false)
    val nudge by app.settings.habitNudge.collectAsStateWithLifecycle(false)
    val nudgeTime by app.settings.habitNudgeTime.collectAsStateWithLifecycle("21:00")
    val notifPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    fun askNotifications() { if (android.os.Build.VERSION.SDK_INT >= 33) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    HubCard("Privacy lock") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lock Focus with fingerprint / screen lock", style = MaterialTheme.typography.bodyLarge, color = Hub.colors.ink, modifier = Modifier.weight(1f))
            HubCheck(lock, { on -> scope.launch { app.settings.focusLock(on); if (on) FocusLock.lock() } }, "Lock Focus", check = HubIcons.Check)
        }
        Text("Unlocks until you leave the app. Mood values never appear on Today; private habits stay hidden until revealed.", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
    }
    HubCard("Reminders") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Notify at task due times", style = MaterialTheme.typography.bodyLarge, color = Hub.colors.ink, modifier = Modifier.weight(1f))
            HubCheck(remind, { on -> scope.launch { app.settings.taskReminders(on); if (on) askNotifications(); Reminders.reschedule(app) } }, "Task reminders", check = HubIcons.Check)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Daily habit check-in at $nudgeTime", style = MaterialTheme.typography.bodyLarge, color = Hub.colors.ink, modifier = Modifier.weight(1f))
            HubCheck(nudge, { on -> scope.launch { app.settings.habitNudge(on); if (on) askNotifications(); Reminders.scheduleNudge(app) } }, "Habit check-in", check = HubIcons.Check)
        }
        if (nudge) PillTabs(listOf("08:00", "13:00", "18:00", "21:00", "22:30"), listOf("08:00", "13:00", "18:00", "21:00", "22:30").indexOf(nudgeTime),
            { i -> scope.launch { app.settings.habitNudge(true, listOf("08:00", "13:00", "18:00", "21:00", "22:30")[i]); Reminders.scheduleNudge(app) } }, color = Hub.colors.cyan)
        Text("Neutral wording only — habit names, private values and moods never appear in notifications. Delivery may be a few minutes late under battery saving.",
            style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
    }
    HubCard("Appearance") {
        val modes = listOf("light", "dark", "system")
        ChoiceCells(listOf("Light", "Dark", "System"), modes.indexOf(theme).takeIf { it >= 0 }, { scope.launch { app.settings.theme(modes[it]) } }, description = "Theme")
    }
    HubCard("Your data stays here", color = Hub.colors.mint) {
        Text("No account, ads, analytics or remote AI. College data is read from the official portal only when you sync, and saved on this device. Android cloud backup is disabled; local JSON backup arrives in I13.",
            style = MaterialTheme.typography.bodyLarge, color = Hub.colors.onAccent)
    }
    StatusCard(DataStatus.CONTENT, "Screen-time tracking", "Managed in Focus → Usage: opt-in accessibility tracking, limits and night cut-off.")
    if (BuildConfig.DEMO) HubButton("Load sample college data (demo)", { app.scope.launch { app.college.save(DemoData.college(java.time.Instant.now())) } }, tone = ButtonTone.Mint)
    if (BuildConfig.DEMO) HubButton("Load sample workouts (demo)", { app.scope.launch { DemoData.training(java.time.Instant.now()).let { (r, ws) -> app.training.saveRoutine(r); ws.forEach { app.training.saveFinished(it) } } } }, tone = ButtonTone.Mint)
    if (BuildConfig.DEBUG) {
        var calib by remember { mutableStateOf(dev.personalhub.monitoring.Calibration.enabled) }
        HubButton(if (calib) "Layout calibration log: ON (debug)" else "Layout calibration log: off (debug)", {
            calib = !calib; dev.personalhub.monitoring.Calibration.enabled = calib }, tone = if (calib) ButtonTone.Mint else ButtonTone.Plain, compact = true)
    }
    if (BuildConfig.DEBUG) HubButton("Device probes (debug)", { context.startActivity(Intent().setClassName(context, "dev.personalhub.app.ProbeActivity")) }, tone = ButtonTone.Plain)
    Text("PersonalHub ${BuildConfig.VERSION_NAME} · ${if (BuildConfig.DEMO) "Demo" else "Connected"} · Fonts: Archivo Black & Space Grotesk (SIL OFL 1.1)",
        style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
}
