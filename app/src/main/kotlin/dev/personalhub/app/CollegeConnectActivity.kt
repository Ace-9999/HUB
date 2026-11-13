package dev.personalhub.app

import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import dev.personalhub.college.api.ImportState
import dev.personalhub.design.*
import dev.personalhub.vtop.SyncOutcome
import dev.personalhub.vtop.VtopDiagnostics
import dev.personalhub.vtop.VtopSyncView
import kotlinx.coroutines.launch

/*
 * Owner-operated VTOP sign-in and sync (I04-T1/T2). The owner types credentials and solves captchas inside the
 * official page; the app only reads bounded table text after sign-in and saves typed rows locally.
 * Native host keeps the WebView outside Compose (I02 paint fix); the header is Compose.
 */
class CollegeConnectActivity : ComponentActivity() {
    private lateinit var portal: VtopSyncView
    private val status = mutableStateOf("Opening the official portal…")
    private val phase = mutableStateOf(Phase.SIGN_IN)
    private val result = mutableStateOf<List<String>>(emptyList())
    private enum class Phase { SIGN_IN, SYNCING, DONE, FAILED }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) // owner login is never captured
        val app = application as PersonalHubApplication
        val semester = intent.getStringExtra(SEMESTER)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true }
        val header = ComposeView(this).apply { setContent { PersonalHubTheme("light") { Header(::finish, ::retry) } } }
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        portal = VtopSyncView(this) { status.value = it }
        portal.onSignedIn = { startSync(app, semester) }
        root.addView(portal, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        portal.post { portal.openPortal(); portal.watchSignIn() }
    }

    private fun startSync(app: PersonalHubApplication, semester: String?) {
        if (phase.value == Phase.SYNCING) return
        phase.value = Phase.SYNCING
        portal.sync(semester) { outcome ->
            when (outcome) {
                is SyncOutcome.Success -> app.scope.launch {
                    runCatching { app.college.save(outcome.import) }.onSuccess {
                        val s = outcome.import
                        result.value = s.statuses.map { "${it.dataset.label}: ${it.detail}" }
                        status.value = "Saved ${s.semester.name}."
                        phase.value = if (s.statuses.any { it.state == ImportState.OK }) Phase.DONE else Phase.FAILED
                        if (BuildConfig.DEBUG) Log.i(TAG, "sync statuses: " + s.statuses.joinToString { "${it.dataset}=${it.state}/${it.rows}" })
                    }.onFailure { status.value = "Couldn't save on this device. Previously saved data is unchanged."; phase.value = Phase.FAILED }
                }
                is SyncOutcome.Failure -> { status.value = outcome.reason; phase.value = Phase.FAILED }
            }
            if (BuildConfig.DEBUG) VtopDiagnostics.last?.let { Log.i(TAG, "structure: $it") }
        }
    }

    private fun retry() {
        result.value = emptyList()
        phase.value = Phase.SIGN_IN
        portal.openPortal(); portal.watchSignIn()
    }

    @Composable private fun Header(close: () -> Unit, retry: () -> Unit) {
        val c = Hub.colors
        Column(Modifier.fillMaxWidth().background(c.canvas).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HubIconButton(HubIcons.Close, "Close", close)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Connect VTOP", style = MaterialTheme.typography.headlineMedium, color = c.ink)
                    Text("Official portal · vtopcc.vit.ac.in", style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
                }
                when (phase.value) {
                    Phase.SYNCING -> Pill("Syncing", c.cyan)
                    Phase.DONE -> Pill("Saved", c.mint)
                    Phase.FAILED -> Pill("Stopped", c.peach)
                    Phase.SIGN_IN -> Pill("Sign in", c.lime)
                }
            }
            Text(status.value, style = MaterialTheme.typography.bodyMedium, color = c.ink)
            if (phase.value == Phase.DONE || phase.value == Phase.FAILED) {
                result.value.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = c.ink) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (phase.value == Phase.DONE) HubButton("Done", close, Modifier.weight(1f), icon = HubIcons.Check)
                    HubButton("Try again", retry, tone = ButtonTone.Plain, icon = HubIcons.Sync, compact = phase.value == Phase.DONE)
                }
            }
            HubDivider()
        }
    }

    override fun onDestroy() {
        (portal.parent as? ViewGroup)?.removeView(portal)
        portal.release()
        super.onDestroy()
    }

    companion object { const val SEMESTER = "semester"; private const val TAG = "PersonalHubSync" }
}
