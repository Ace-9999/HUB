package dev.personalhub.feature.training

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.personalhub.design.*
import dev.personalhub.training.api.Muscle
import dev.personalhub.training.api.heatBin

/*
 * Original programmatic body map (GYM-03/GYM-11): simple rounded shapes on a 100 x 200 grid, drawn in code.
 * Asset manifest: "body map v1 — original vector drawn by PersonalHub, project licence, no third-party source".
 * Colours are heat bins with a legend; exact numbers are always listed beside the map.
 */
private data class Region(val muscle: Muscle?, val x: Float, val y: Float, val w: Float, val h: Float, val r: Float = 4f)

private val FRONT = listOf(
    Region(null, 42f, 2f, 16f, 18f, 8f),                                          // head
    Region(Muscle.FRONT_DELTS, 24f, 24f, 12f, 12f, 6f), Region(Muscle.FRONT_DELTS, 64f, 24f, 12f, 12f, 6f),
    Region(Muscle.SIDE_DELTS, 19f, 27f, 6f, 12f, 3f), Region(Muscle.SIDE_DELTS, 75f, 27f, 6f, 12f, 3f),
    Region(Muscle.CHEST, 36f, 25f, 13f, 17f), Region(Muscle.CHEST, 51f, 25f, 13f, 17f),
    Region(Muscle.BICEPS, 20f, 40f, 9f, 20f), Region(Muscle.BICEPS, 71f, 40f, 9f, 20f),
    Region(Muscle.FOREARMS, 17f, 62f, 9f, 24f), Region(Muscle.FOREARMS, 74f, 62f, 9f, 24f),
    Region(Muscle.ABS, 41f, 44f, 18f, 32f), Region(Muscle.OBLIQUES, 33f, 45f, 7f, 28f), Region(Muscle.OBLIQUES, 60f, 45f, 7f, 28f),
    Region(Muscle.QUADS, 33f, 84f, 15f, 44f, 7f), Region(Muscle.QUADS, 52f, 84f, 15f, 44f, 7f),
    Region(Muscle.ADDUCTORS, 46f, 86f, 4f, 26f, 2f), Region(Muscle.ADDUCTORS, 50f, 86f, 4f, 26f, 2f),
    Region(null, 35f, 132f, 11f, 40f, 5f), Region(null, 54f, 132f, 11f, 40f, 5f),  // shins (not tracked)
)
private val BACK = listOf(
    Region(null, 42f, 2f, 16f, 18f, 8f),
    Region(Muscle.TRAPS, 36f, 20f, 28f, 14f, 6f),
    Region(Muscle.REAR_DELTS, 22f, 25f, 12f, 12f, 6f), Region(Muscle.REAR_DELTS, 66f, 25f, 12f, 12f, 6f),
    Region(Muscle.UPPER_BACK, 37f, 34f, 26f, 12f),
    Region(Muscle.LATS, 32f, 46f, 15f, 22f), Region(Muscle.LATS, 53f, 46f, 15f, 22f),
    Region(Muscle.TRICEPS, 20f, 39f, 9f, 21f), Region(Muscle.TRICEPS, 71f, 39f, 9f, 21f),
    Region(Muscle.FOREARMS, 17f, 62f, 9f, 24f), Region(Muscle.FOREARMS, 74f, 62f, 9f, 24f),
    Region(Muscle.LOWER_BACK, 41f, 68f, 18f, 12f),
    Region(Muscle.GLUTES, 34f, 81f, 15f, 16f, 7f), Region(Muscle.GLUTES, 51f, 81f, 15f, 16f, 7f),
    Region(Muscle.HAMSTRINGS, 34f, 99f, 14f, 32f, 6f), Region(Muscle.HAMSTRINGS, 52f, 99f, 14f, 32f, 6f),
    Region(Muscle.CALVES, 35f, 134f, 12f, 30f, 6f), Region(Muscle.CALVES, 53f, 134f, 12f, 30f, 6f),
)

@Composable fun heatColor(bin: Int): Color = when (bin) { 0 -> Hub.colors.raised; 1 -> Hub.colors.mint; 2 -> Hub.colors.lime; 3 -> Hub.colors.apricot; else -> Hub.colors.peach }

@Composable fun BodyMap(values: Map<Muscle, Double>, modifier: Modifier = Modifier, front: Boolean = true, compact: Boolean = false) {
    val colors = (0..4).map { heatColor(it) }
    val outline = Hub.colors.outline; val skin = Hub.colors.surface
    val desc = values.entries.filter { it.value > 0 }.sortedByDescending { it.value }.joinToString { "${it.key.label} ${it.value}" }.ifEmpty { "no tracked muscles" }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
        val views = if (compact) listOf(true, false) else listOf(front)
        views.forEach { f ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.width(if (compact) 90.dp else 150.dp).height(if (compact) 180.dp else 300.dp).semantics { contentDescription = "${if (f) "Front" else "Back"} muscle map: $desc" }) {
                    drawFigure(if (f) FRONT else BACK, values, colors, outline, skin)
                }
                if (compact) Text(if (f) "Front" else "Back", style = MaterialTheme.typography.labelSmall, color = Hub.colors.inkSoft)
            }
        }
    }
}

private fun DrawScope.drawFigure(regions: List<Region>, values: Map<Muscle, Double>, colors: List<Color>, outline: Color, skin: Color) {
    val sx = size.width / 100f; val sy = size.height / 175f
    regions.forEach { r ->
        val fill = r.muscle?.let { colors[heatBin(values[it] ?: 0.0)] } ?: skin
        val tl = Offset(r.x * sx, r.y * sy); val sz = Size(r.w * sx, r.h * sy); val cr = CornerRadius(r.r * sx)
        drawRoundRect(fill, tl, sz, cr)
        drawRoundRect(outline, tl, sz, cr, style = Stroke(1.5.dp.toPx()))
    }
}

@Composable fun HeatLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("0", "≤3", "≤6", "≤10", ">10").forEachIndexed { i, l -> Dot(heatColor(i), 12.dp); Text(l, style = MaterialTheme.typography.labelSmall, color = Hub.colors.ink) }
    }
}
