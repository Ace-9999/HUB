package dev.personalhub.app.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.personalhub.app.PersonalHubApplication
import dev.personalhub.design.*
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val fmt = DateTimeFormatter.ofPattern("d MMM, h:mm a").withZone(ZoneId.systemDefault())
private fun size(b: Long) = if (b > 1_048_576) "%.1f MB".format(b / 1_048_576.0) else "${(b / 1024).coerceAtLeast(1)} KB"

/** Settings card for JSON backups (I13). Nothing is ever sent anywhere; copies go only where the owner picks. */
@Composable fun BackupCard(app: PersonalHubApplication) {
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val snaps = remember(refresh) { app.backups.snapshots() }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<FullBackup?>(null) }
    var showAll by remember { mutableStateOf(false) }
    fun run(label: String, block: suspend () -> Unit) { busy = true; scope.launch {
        message = runCatching { block(); label }.getOrElse { it.message ?: "Backup failed." }; busy = false; refresh++ } }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { run("Copy saved.") { app.backups.exportTo(it) } } }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { busy = true; scope.launch { runCatching { app.backups.read(it) }.onSuccess { b -> preview = b }.onFailure { e -> message = e.message }; busy = false } }
    }
    HubCard("Backup", trailing = { Pill(snaps.firstOrNull()?.let { "Last ${fmt.format(it.createdAt)}" } ?: "None yet", if (snaps.isEmpty()) Hub.colors.apricot else Hub.colors.mint) }) {
        Text("Full plain-JSON backups of tasks, habits, moods (with notes), workouts, usage, limits and college data — automatically once a day, last 7 kept on this phone. " +
            "No passwords or permissions are included. Copies stay wherever you save them; nothing is uploaded.", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HubButton("Back up now", { run("Backup saved.") { app.backups.backupNow() } }, Modifier.weight(1f), enabled = !busy, icon = HubIcons.Check, compact = true)
            HubButton("Save a copy…", { save.launch("personalhub-backup.json") }, Modifier.weight(1f), enabled = !busy, tone = ButtonTone.Plain, compact = true)
        }
        HubButton("Restore from file…", { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !busy, tone = ButtonTone.Plain, compact = true)
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Hub.colors.ink) }
        if (snaps.isNotEmpty()) {
            HubButton(if (showAll) "Hide saved backups" else "Saved backups (${snaps.size})", { showAll = !showAll }, tone = ButtonTone.Plain, compact = true)
            if (showAll) snaps.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${fmt.format(s.createdAt)} · ${size(s.bytes)}", style = MaterialTheme.typography.bodyMedium, color = Hub.colors.ink, modifier = Modifier.weight(1f))
                    HubButton("Restore", { busy = true; scope.launch { runCatching { app.backups.read(s.file) }.onSuccess { preview = it }.onFailure { message = it.message }; busy = false } },
                        tone = ButtonTone.Plain, compact = true)
                }
            }
        }
    }
    preview?.let { b ->
        Dialog(onDismissRequest = { preview = null }) {
            HubPanel(padding = 20.dp) {
                Text("Restore this backup?", style = MaterialTheme.typography.headlineSmall, color = Hub.colors.ink)
                Text("Created ${fmt.format(b.createdAt)} · zone ${b.zone.id}", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
                b.counts.forEach { (k, v) -> Row { Text(k, style = MaterialTheme.typography.bodyMedium, color = Hub.colors.ink, modifier = Modifier.weight(1f)); Text("$v", style = MaterialTheme.typography.titleSmall, color = Hub.colors.ink) } }
                Text("Everything in PersonalHub is replaced by this file. A safety backup of your current data is saved first. " +
                    "Afterwards, sign in to VTOP and re-enable tracking/notification permissions yourself.", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HubButton("Cancel", { preview = null }, Modifier.weight(1f), tone = ButtonTone.Plain, compact = true)
                    HubButton("Replace everything", { preview = null; run("Restored.") { app.backups.restore(b) } }, Modifier.weight(1f), tone = ButtonTone.Danger, compact = true)
                }
            }
        }
    }
}
