package dev.personalhub.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.personalhub.design.*
import dev.personalhub.monitoring.ProbeControl

// Owner-operated debug UI; no credentials, portal text or academic rows enter diagnostics.
class ProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PersonalHubTheme("dark") {
            val signals by ProbeControl.signals.collectAsState()
            var opted by remember { mutableStateOf(false) }
            var redirect by remember { mutableStateOf(false) }
            DisposableEffect(Unit) { onDispose { ProbeControl.reset() } }
            Surface(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Device feasibility probes", style = MaterialTheme.typography.titleLarge)
                Text("Debug only · no connected-data claim", color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { startActivity(Intent(this@ProbeActivity, PortalProbeActivity::class.java)) }) { Text("VTOP") }
                    TextButton(onClick = { finish() }) { Text("Close & clear") }
                }
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HubCard("Monitoring disclosure") {
                        Text("This probe counts selected package/window/scroll events only. It does not read screen text, recognize video, calculate time or save browsing content. Counts disappear when closed.")
                        Row { Switch(checked = opted, onCheckedChange = { opted = it; ProbeControl.optedIn = it; if (!it) { redirect = false; ProbeControl.redirectPackage = null } }); Text("Observe selected events") }
                        Button(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Open accessibility settings") }
                        Text("Service: ${if (signals.connected) "connected" else "not connected"} · event count ${signals.events} · raw scroll events ${signals.scrolls}")
                        Text("Last selected package: ${signals.lastPackage ?: "none"}. Raw events are not scroll bursts or items viewed.")
                    }
                    HubCard("Reversible Home redirect test") {
                        Text("Reddit native only, while this toggle is on. Chrome, Android settings and PersonalHub cannot be selected. This is a probe, not a limit.")
                        Row { Switch(checked = redirect, enabled = opted && signals.connected, onCheckedChange = { redirect = it; ProbeControl.redirectPackage = if (it) "com.reddit.frontpage" else null }); Text("Redirect Reddit Home") }
                    }
                    Text("Grant/revoke access yourself in Android settings. Missing signals remain unknown. All native/web classifications require physical evaluation.")
                }
            } }
        } }
    }
}
