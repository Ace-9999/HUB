package dev.personalhub.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.personalhub.core.DataStatus

// Original shared graphite/lime theme from the approved design tokens.
private val Dark = darkColorScheme(primary = Color(0xFFC8F542), onPrimary = Color(0xFF172000),
    background = Color(0xFF0D1013), surface = Color(0xFF171C21), surfaceVariant = Color(0xFF222A31),
    onBackground = Color(0xFFF4F6EE), onSurface = Color(0xFFF4F6EE), onSurfaceVariant = Color(0xFFB1BBB4),
    outline = Color(0xFF46534A), error = Color(0xFFFFA3A3), secondaryContainer = Color(0xFF222A31), onSecondaryContainer = Color(0xFFC8F542))
private val Light = lightColorScheme(primary = Color(0xFF315C00), onPrimary = Color.White,
    background = Color(0xFFFAFAF0), surface = Color.White, surfaceVariant = Color(0xFFEEF1E8),
    onBackground = Color(0xFF111713), onSurface = Color(0xFF111713), onSurfaceVariant = Color(0xFF505D54),
    outline = Color(0xFF68776B), error = Color(0xFFA52222), secondaryContainer = Color(0xFFEEF1E8), onSecondaryContainer = Color(0xFF315C00))

@Composable fun PersonalHubTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
@Composable fun HubCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}
@Composable fun StatusCard(status: DataStatus, title: String, detail: String) {
    HubCard(title) {
        Text(status.name.lowercase().replaceFirstChar { it.uppercase() }, color = MaterialTheme.colorScheme.primary)
        Text(detail, style = MaterialTheme.typography.bodyLarge)
        if (status == DataStatus.LOADING) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
