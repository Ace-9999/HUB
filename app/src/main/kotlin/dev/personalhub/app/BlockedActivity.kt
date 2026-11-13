package dev.personalhub.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.personalhub.design.*
import dev.personalhub.monitoring.UsageEngine
import dev.personalhub.usage.api.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Local explanation shown after a redirect (FOC-08/FOC-11). Labelled as a redirect, never a force-stop. Without strict
 * mode a deliberate, logged 5-minute override is available after a short wait; settings stay reachable either way.
 */
class BlockedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PersonalHubApplication
        val title = intent.getStringExtra(TITLE) ?: "Time's up"
        val body = intent.getStringExtra(BODY).orEmpty()
        val target = intent.getStringExtra(TARGET).orEmpty()
        val curfew = intent.getBooleanExtra(CURFEW, false)
        setContent {
            PersonalHubTheme("light") {
                val strict = app.usageEngine.settings.strict
                var wait by remember { mutableIntStateOf(10) }
                val scope = rememberCoroutineScope()
                LaunchedEffect(Unit) { while (wait > 0) { delay(1_000); wait-- } }
                HubCanvas {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Pill(if (curfew) "CURFEW" else "LIMIT REACHED", if (curfew) Hub.colors.lavender else Hub.colors.peach)
                        Text(title, style = MaterialTheme.typography.displayMedium, color = Hub.colors.ink, textAlign = TextAlign.Center)
                        HubPanel(padding = 20.dp) {
                            Text(body, style = MaterialTheme.typography.bodyLarge, color = Hub.colors.ink)
                            Text("PersonalHub sent you to the home screen. $target was not closed or force-stopped; it will redirect again while this limit is active.",
                                style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
                        }
                        HubButton("Back to home", { finish() }, Modifier.fillMaxWidth(), icon = HubIcons.Home)
                        if (!strict) HubButton(if (wait > 0) "Override available in ${wait}s" else "Give me 5 minutes", {
                            if (wait == 0) scope.launch {
                                val o = Override(Instant.now().plus(Duration.ofMinutes(5)), Instant.now(), if (curfew) "curfew" else "budget")
                                app.usage.addOverride(o); app.usageEngine.noteOverride(o); finish()
                            }
                        }, Modifier.fillMaxWidth(), tone = ButtonTone.Plain, enabled = wait == 0)
                        else Text("Strict mode is on, so there is no override. You can still change limits or turn tracking off in PersonalHub → Focus → Usage.",
                            style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft, textAlign = TextAlign.Center)
                        HubButton("Open Focus settings", {
                            startActivity(Intent(this@BlockedActivity, MainActivity::class.java).putExtra(MainActivity.OPEN_USAGE, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            finish()
                        }, tone = ButtonTone.Plain, compact = true)
                    }
                }
            }
        }
    }

    companion object {
        private const val TITLE = "title"; private const val BODY = "body"; private const val TARGET = "target"; private const val CURFEW = "curfew"
        private val clock = DateTimeFormatter.ofPattern("h:mm a")
        fun intent(context: Context, reason: BlockReason, ctx: UsageContext, engine: UsageEngine): Intent {
            val (title, body, curfew) = when (reason) {
                is BlockReason.CurfewWindow -> Triple("It's past your cut-off", "Scrolling is paused until ${reason.until.toLocalTime().format(clock)}. Get some rest.", true)
                is BlockReason.Budget -> {
                    val used = engine.usedToday(reason.rule).toMinutes()
                    Triple("You've used today's time", "${reason.rule.scopeLabel}: $used of ${reason.rule.dailyMinutes} min. Resets at midnight.", false)
                }
            }
            return Intent(context, BlockedActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(TITLE, title).putExtra(BODY, body).putExtra(TARGET, ctx.target.label).putExtra(CURFEW, curfew)
        }
    }
}
