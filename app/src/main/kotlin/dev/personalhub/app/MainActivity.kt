package dev.personalhub.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
                HubApp(app, theme)
            }
        }
    }
}

val sections = SectionRegistry(listOf(Section("today", "Today"), Section("college", "College"), Section("training", "Train"), Section("focus", "Focus")))
private val navItems = listOf(NavItem("today", "Today", HubIcons.Home), NavItem("college", "College", HubIcons.School),
    NavItem("training", "Train", HubIcons.Dumbbell), NavItem("focus", "Focus", HubIcons.Bolt))

@Composable fun HubApp(app: PersonalHubApplication, theme: String) {
    var selected by rememberSaveable { mutableStateOf("today") }
    var settings by rememberSaveable { mutableStateOf(false) }
    var collegePage by rememberSaveable { mutableStateOf(CollegePage.OVERVIEW) }
    var focusTab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val focus: FocusViewModel = viewModel(factory = viewModelFactory { initializer { FocusViewModel(app.focus, app.time) } })
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
                    if (settings) SettingsScreen(app, theme) { settings = false }
                    else when (selected) {
                        "today" -> TodayScreen(focus, college, onSettings = { settings = true }, openCollege = { collegePage = it; selected = "college" }, openFocus = { focusTab = it; selected = "focus" })
                        "college" -> {
                            if (collegePage == CollegePage.OVERVIEW) Header("College", college.state.collectAsStateWithLifecycle().value.snapshot.semester?.name ?: "VTOP · Chennai") { settings = true }
                            CollegeContent(college, collegePage, { collegePage = it }, connect)
                        }
                        "training" -> { Header("Train", "Routines & workouts") { settings = true }; TrainingPlaceholder() }
                        else -> { Header("Focus", "Tasks, habits & mood") { settings = true }; FocusContent(focus, focusTab) { focusTab = it } }
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
    if (BuildConfig.DEBUG) HubButton("Device probes (debug)", { context.startActivity(Intent().setClassName(context, "dev.personalhub.app.ProbeActivity")) }, tone = ButtonTone.Plain)
    Text("PersonalHub ${BuildConfig.VERSION_NAME} · ${if (BuildConfig.DEMO) "Demo" else "Connected"} · Fonts: Archivo Black & Space Grotesk (SIL OFL 1.1)",
        style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
}
