package dev.personalhub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.personalhub.core.*
import dev.personalhub.design.*
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PersonalHubApplication
        setContent {
            val theme by app.settings.theme.collectAsStateWithLifecycle("system")
            PersonalHubTheme(theme) { HubApp(app, theme) }
        }
    }
}

val sections = SectionRegistry(listOf(Section("today", "Today"), Section("college", "College"), Section("training", "Train"), Section("focus", "Focus")))

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HubApp(app: PersonalHubApplication, theme: String) {
    var selected by rememberSaveable { mutableStateOf("today") }
    var settings by rememberSaveable { mutableStateOf(false) }
    val holder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val icons = listOf(Icons.Default.Home, Icons.Default.DateRange, Icons.Default.PlayArrow, Icons.Default.CheckCircle)
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (settings) "Settings" else "PersonalHub", style = MaterialTheme.typography.headlineSmall) },
            actions = { TextButton(onClick = { settings = !settings }) { Text(if (settings) "Done" else "Settings") } }) },
        bottomBar = { NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            sections.sections.forEachIndexed { i, section -> NavigationBarItem(selected = selected == section.id && !settings,
                onClick = { selected = section.id; settings = false }, icon = { Icon(icons[i], contentDescription = null) }, label = { Text(section.title) }) }
        } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (settings) {
                Text("Make it yours", style = MaterialTheme.typography.headlineMedium)
                HubCard("Appearance") { listOf("system", "dark", "light").forEach { mode ->
                    Row { RadioButton(selected = theme == mode, onClick = { scope.launch { app.settings.theme(mode) } }); TextButton(onClick = { scope.launch { app.settings.theme(mode) } }) { Text(mode.replaceFirstChar { it.uppercase() }) } }
                } }
                StatusCard(DataStatus.UNAVAILABLE, "Connections", "College and screen tracking are optional. Device feasibility checks are pending; local features need no permissions.")
                HubCard("Your data stays here") { Text("No account, ads, analytics or remote AI. Android cloud backup is disabled. Local JSON backup and restore arrive in I13.") }
                Text("PersonalHub ${BuildConfig.VERSION_NAME} · ${if (BuildConfig.DEMO) "Demo" else "Local / connected-capable"} · API 29+")
            } else holder.SaveableStateProvider(selected) {
                Text(sections.resolve(selected).title, style = MaterialTheme.typography.headlineLarge)
                when (selected) {
                    "today" -> {
                        Text(AppTime().today().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HubCard("A little more intentional.") { Text("Your day, in one place. College, training and daily focus — available locally.") }
                        StatusCard(DataStatus.EMPTY, "Start with one thing", "Your tasks and training will appear here. No personal records have been added.")
                    }
                    "college" -> {
                        StatusCard(DataStatus.UNAVAILABLE, "Not connected", "Chennai VTOP login and source validation need an owner-device probe. No academic values have been acquired.")
                        if (BuildConfig.DEBUG) Button(onClick = { context.startActivity(Intent().setClassName(context, "dev.personalhub.app.ProbeActivity")) }) { Text("Open device probes") }
                    }
                    "training" -> StatusCard(DataStatus.EMPTY, "Make room for progress", "Your offline exercise catalog, routines and workouts arrive in the training increments.")
                    "focus" -> StatusCard(DataStatus.EMPTY, "A clearer day", "Tasks, manual habits and timestamped moods arrive in I03. Monitoring stays off until you choose it and device checks pass.")
                }
            }
        }
    }
}
