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
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    companion object { const val OPEN_USAGE = "openUsage" }
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

@Composable fun HubApp(app: PersonalHubApplication, theme: String, openUsage: Boolean = false) {
    var selected by rememberSaveable { mutableStateOf(if (openUsage) "focus" else "today") }
    var settings by rememberSaveable { mutableStateOf(false) }
    var collegePage by rememberSaveable { mutableStateOf(CollegePage.OVERVIEW) }
    var focusTab by rememberSaveable { mutableIntStateOf(if (openUsage) 4 else 0) }
    val context = LocalContext.current
    val focus: FocusViewModel = viewModel(factory = viewModelFactory { initializer { FocusViewModel(app.focus, app.time) } })
    val usage: UsageViewModel = viewModel(factory = viewModelFactory { initializer { UsageViewModel(app.usage, app.usageEngine, app.time.zone) } })
    val openAccessibility = { context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    val college: CollegeViewModel = viewModel(factory = viewModelFactory { initializer { CollegeViewModel(app.college, app.time) } })
    val connect: (String?) -> Unit = { semester -> context.startActivity(Intent(context, CollegeConnectActivity::class.java).apply { semester?.let { putExtra(CollegeConnectActivity.SEMESTER, it) } }) }
    val scroll = rememberScrollState()
    LaunchedEffect(selected, settings, collegePage, focusTab) { scroll.scrollTo(0) }

    BackHandler(enabled = settings || (selected == "college" && collegePage != CollegePage.OVERVIEW) || selected != "today") {
        when {
            settings -> settings = false
            selected == "college" && collegePage != CollegePage.OVERVIEW -> collegePage = CollegePage.OVERVIEW
            else -> selected = "today"
        }
    }

    HubCanvas {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll).statusBarsPadding().padding(horizontal = Hub.ScreenPadding).padding(top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val screen = Screen(settings, selected, if (selected == "college") collegePage else CollegePage.OVERVIEW)
                    AnimatedContent(screen, transitionSpec = { pageTransition(initialState, targetState) }, label = "page") { s ->
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (s.settings) SettingsScreen(app, theme) { settings = false }
                            else when (s.tab) {
                                "today" -> TodayScreen(focus, college, onSettings = { settings = true }, openCollege = { collegePage = it; selected = "college" }, openFocus = { focusTab = it; selected = "focus" })
                                "college" -> {
                                    if (s.page == CollegePage.OVERVIEW) Header("College", college.state.collectAsStateWithLifecycle().value.snapshot.semester?.name ?: "VTOP · Chennai") { settings = true }
                                    CollegeContent(college, s.page, { collegePage = it }, connect)
                                }
                                "training" -> { Header("Train", "Routines & workouts") { settings = true }; TrainingPlaceholder() }
                                else -> { Header("Focus", "Tasks, habits & mood") { settings = true }; FocusContent(focus, usage, focusTab, { focusTab = it }, openAccessibility) }
                            }
                        }
                    }
                }
                // Canvas-coloured scrim keeps scrolled content out from under the status bar icons.
                Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Hub.colors.canvas).align(Alignment.TopCenter))
                FocusUndoBar(focus, Modifier.align(Alignment.BottomCenter).padding(horizontal = Hub.ScreenPadding, vertical = 12.dp))
            }
            HubBottomBar(navItems, if (settings) null else selected, { selected = it; settings = false; if (it == "college") collegePage = CollegePage.OVERVIEW })
        }
    }
}

/** Navigation position used to pick a transition direction. */
private data class Screen(val settings: Boolean, val tab: String, val page: CollegePage) {
    val order: Int get() = listOf("today", "college", "training", "focus").indexOf(tab)
    val depth: Int get() = (if (settings) 2 else 0) + (if (page != CollegePage.OVERVIEW) 1 else 0)
}

/** Deeper pages push in from the right; sibling tabs slide by their bar order; everything fades. */
private fun pageTransition(from: Screen, to: Screen): ContentTransform {
    val dir = when {
        to.depth != from.depth -> if (to.depth > from.depth) 1 else -1
        to.order != from.order -> if (to.order > from.order) 1 else -1
        else -> 0
    }
    val enter = fadeIn(tween(240, delayMillis = 40)) + slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 5 * dir } +
        scaleIn(tween(320), initialScale = if (dir == 0) 0.98f else 1f)
    val exit = fadeOut(tween(140)) + slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 8 * dir }
    return (enter togetherWith exit).apply { targetContentZIndex = to.depth.toFloat() }
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

@Composable private fun TodayScreen(focus: FocusViewModel, college: CollegeViewModel, onSettings: () -> Unit, openCollege: (CollegePage) -> Unit, openFocus: (Int) -> Unit) {
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
    HubCard("Training", trailing = { Pill("Soon", Hub.colors.apricot) }) {
        Text("Routines and workout logging arrive in the training update.", style = MaterialTheme.typography.bodyLarge, color = Hub.colors.inkSoft)
    }
}

@Composable private fun TrainingPlaceholder() {
    EmptyState("Make room for progress", "The offline exercise catalog, routines and live workout logging are the next training increments (I07–I09). Nothing here is tracked yet.")
}

@Composable private fun SettingsScreen(app: PersonalHubApplication, theme: String, close: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    ScreenTitle("Settings", onBack = close, back = HubIcons.Back)
    HubCard("Appearance") {
        val modes = listOf("light", "dark", "system")
        ChoiceCells(listOf("Light", "Dark", "System"), modes.indexOf(theme).takeIf { it >= 0 }, { scope.launch { app.settings.theme(modes[it]) } }, description = "Theme")
    }
    HubCard("Your data stays here", color = Hub.colors.mint) {
        Text("No account, ads, analytics or remote AI. College data is read from the official portal only when you sync, and saved on this device. Android cloud backup is disabled; local JSON backup arrives in I13.",
            style = MaterialTheme.typography.bodyLarge, color = Hub.colors.onAccent)
    }
    StatusCard(DataStatus.UNAVAILABLE, "Screen-time tracking", "Optional monitoring stays off until device checks pass (I10–I12). Local features need no permissions.")
    if (BuildConfig.DEMO) HubButton("Load sample college data (demo)", { app.scope.launch { app.college.save(DemoData.college(java.time.Instant.now())) } }, tone = ButtonTone.Mint)
    if (BuildConfig.DEBUG) {
        var calib by remember { mutableStateOf(dev.personalhub.monitoring.Calibration.enabled) }
        HubButton(if (calib) "Layout calibration log: ON (debug)" else "Layout calibration log: off (debug)", {
            calib = !calib; dev.personalhub.monitoring.Calibration.enabled = calib }, tone = if (calib) ButtonTone.Mint else ButtonTone.Plain, compact = true)
    }
    if (BuildConfig.DEBUG) HubButton("Device probes (debug)", { context.startActivity(Intent().setClassName(context, "dev.personalhub.app.ProbeActivity")) }, tone = ButtonTone.Plain)
    Text("PersonalHub ${BuildConfig.VERSION_NAME} · ${if (BuildConfig.DEMO) "Demo" else "Connected"} · Fonts: Archivo Black & Space Grotesk (SIL OFL 1.1)",
        style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
}
