package dev.personalhub.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.personalhub.core.DataStatus

/*
 * Shared neo-brutalist design language: ivory dotted canvas, 2dp ink outlines, hard offset
 * shadows, pastel status pills and heavy display type. Features consume only these components
 * and tokens, so the visual layer can be replaced without touching data or ViewModels.
 */

@Immutable
data class HubPalette(
    val canvas: Color, val dot: Color, val surface: Color, val raised: Color,
    val ink: Color, val inkSoft: Color, val outline: Color, val shadow: Color,
    val lime: Color, val mint: Color, val cyan: Color, val peach: Color, val apricot: Color,
    val lavender: Color, val danger: Color, val onAccent: Color, val dark: Boolean,
)

private val LightPalette = HubPalette(
    canvas = Color(0xFFFBFAEE), dot = Color(0xFFD9D8C6), surface = Color.White, raised = Color(0xFFF2F1E3),
    ink = Color(0xFF111111), inkSoft = Color(0xFF4A4A44), outline = Color(0xFF111111), shadow = Color(0xFF111111),
    lime = Color(0xFFD5F53A), mint = Color(0xFF9EE8C7), cyan = Color(0xFF93E3EA), peach = Color(0xFFF5AA9C),
    apricot = Color(0xFFFAD6AC), lavender = Color(0xFFD9D0F7), danger = Color(0xFFB3261E), onAccent = Color(0xFF111111), dark = false,
)
private val DarkPalette = HubPalette(
    canvas = Color(0xFF111310), dot = Color(0xFF2A2E27), surface = Color(0xFF1B1E1A), raised = Color(0xFF262A24),
    ink = Color(0xFFF3F4EA), inkSoft = Color(0xFFB8BCAE), outline = Color(0xFFE6E8DA), shadow = Color(0xFF4D5446),
    lime = Color(0xFFD5F53A), mint = Color(0xFF9EE8C7), cyan = Color(0xFF93E3EA), peach = Color(0xFFF5AA9C),
    apricot = Color(0xFFFAD6AC), lavender = Color(0xFFD9D0F7), danger = Color(0xFFFF9C94), onAccent = Color(0xFF111111), dark = true,
)

val LocalHubPalette = staticCompositionLocalOf { LightPalette }
object Hub {
    val colors: HubPalette @Composable @ReadOnlyComposable get() = LocalHubPalette.current
    val Stroke = 2.dp
    val Shadow = 4.dp
    val CardShape = RoundedCornerShape(16.dp)
    val FieldShape = RoundedCornerShape(12.dp)
    val ScreenPadding = 20.dp
}

private val Display = FontFamily(Font(R.font.archivo_black, FontWeight.Black))
@OptIn(ExperimentalTextApi::class) private fun grotesk(weight: Int) = Font(R.font.space_grotesk, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
private val Body = FontFamily(grotesk(400), grotesk(500), grotesk(600), grotesk(700))
val HubDisplayFont: FontFamily get() = Display
val HubBodyFont: FontFamily get() = Body

private val HubTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = Display, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.3).sp),
    displaySmall = TextStyle(fontFamily = Display, fontSize = 28.sp, lineHeight = 34.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontSize = 26.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontSize = 22.sp, lineHeight = 28.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontSize = 19.sp, lineHeight = 25.sp),
    titleLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.6.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.3.sp),
)

@Composable fun PersonalHubTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) darkColorScheme(primary = p.lime, onPrimary = p.onAccent, background = p.canvas, surface = p.surface,
        surfaceVariant = p.raised, onBackground = p.ink, onSurface = p.ink, onSurfaceVariant = p.inkSoft, outline = p.outline, error = p.danger,
        secondaryContainer = p.lime, onSecondaryContainer = p.onAccent)
    else lightColorScheme(primary = p.ink, onPrimary = Color.White, background = p.canvas, surface = p.surface,
        surfaceVariant = p.raised, onBackground = p.ink, onSurface = p.ink, onSurfaceVariant = p.inkSoft, outline = p.outline, error = p.danger,
        secondaryContainer = p.lime, onSecondaryContainer = p.onAccent)
    CompositionLocalProvider(LocalHubPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = HubTypography, content = content)
    }
}

/** Hard, unblurred offset shadow drawn behind a rounded outline (the reference "sticker" depth). */
fun Modifier.hardShadow(color: Color, offset: Dp, radius: Dp): Modifier = drawBehind {
    if (offset > 0.dp) {
        val o = offset.toPx()
        drawRoundRect(color, topLeft = Offset(o, o), size = Size(size.width, size.height), cornerRadius = CornerRadius(radius.toPx()))
    }
}

/** Faint dotted grid used on the screen canvas (decorative; never behind dense tables). */
/** One dot rendered into a small tile once, then repeated by a shader: a single draw call per frame. */
fun Modifier.dottedCanvas(background: Color, dot: Color, spacing: Dp = 22.dp): Modifier = drawWithCache {
    val step = spacing.toPx().coerceAtLeast(4f).toInt()
    val tile = androidx.compose.ui.graphics.ImageBitmap(step, step)
    androidx.compose.ui.graphics.Canvas(tile).apply {
        drawRect(0f, 0f, step.toFloat(), step.toFloat(), androidx.compose.ui.graphics.Paint().apply { color = background })
        drawCircle(Offset(step / 2f, step / 2f), 1.1.dp.toPx(), androidx.compose.ui.graphics.Paint().apply { color = dot; isAntiAlias = true })
    }
    val brush = androidx.compose.ui.graphics.ShaderBrush(androidx.compose.ui.graphics.ImageShader(tile, androidx.compose.ui.graphics.TileMode.Repeated, androidx.compose.ui.graphics.TileMode.Repeated))
    onDrawBehind { drawRect(brush) }
}

@Composable fun HubCanvas(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = Hub.colors
    Box(modifier.fillMaxSize().dottedCanvas(c.canvas, c.dot), content = content)
}

/** Outlined sticker surface with hard shadow. Pressing a clickable surface sinks it into its shadow. */
@Composable fun HubSurface(
    modifier: Modifier = Modifier, color: Color = Hub.colors.surface, radius: Dp = 16.dp, shadow: Dp = Hub.Shadow,
    stroke: Dp = Hub.Stroke, onClick: (() -> Unit)? = null, onClickLabel: String? = null, enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = Hub.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val depth by animateDpAsState(if (pressed && onClick != null) 0.dp else shadow,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "press")
    val fill by animateColorAsState(color, tween(220), label = "fill")
    val shape = RoundedCornerShape(radius)
    val travel = shadow - depth
    Box(modifier.padding(end = shadow, bottom = shadow)) {
        Box(
            Modifier.offset(travel, travel).hardShadow(c.shadow, depth, radius).clip(shape).background(fill).border(stroke, c.outline, shape)
                .then(if (onClick != null) Modifier.clickable(source, LocalIndicationNone, enabled = enabled, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier),
            content = content,
        )
    }
}
private val LocalIndicationNone: Indication? = null

@Composable fun HubCard(title: String, modifier: Modifier = Modifier, color: Color = Hub.colors.surface, trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    HubSurface(modifier.fillMaxWidth(), color = color, onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = if (color == Hub.colors.surface) Hub.colors.ink else Hub.colors.onAccent, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable fun HubPanel(modifier: Modifier = Modifier, color: Color = Hub.colors.surface, padding: Dp = 16.dp, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    HubSurface(modifier.fillMaxWidth(), color = color, onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(padding), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

enum class ButtonTone { Primary, Plain, Danger, Mint }
@Composable fun HubButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tone: ButtonTone = ButtonTone.Primary,
    icon: ImageVector? = null, trailingIcon: ImageVector? = null, enabled: Boolean = true, compact: Boolean = false) {
    val c = Hub.colors
    val fill = when { !enabled -> c.raised; tone == ButtonTone.Primary -> c.lime; tone == ButtonTone.Danger -> c.peach; tone == ButtonTone.Mint -> c.mint; else -> c.surface }
    val fg = if (fill == c.surface || fill == c.raised) c.ink else c.onAccent
    HubSurface(modifier, color = fill, radius = if (compact) 10.dp else 12.dp, shadow = if (compact) 3.dp else Hub.Shadow, onClick = onClick, enabled = enabled) {
        Row(Modifier.defaultMinSize(minHeight = if (compact) 40.dp else 52.dp).padding(horizontal = if (compact) 14.dp else 22.dp).align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(if (compact) 18.dp else 20.dp)) }
            Text(if (tone == ButtonTone.Primary && !compact) text.uppercase() else text, style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge, color = fg, textAlign = TextAlign.Center)
            trailingIcon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(20.dp)) }
        }
    }
}

/** Square outlined icon button, e.g. the reference back arrow. */
@Composable fun HubIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Hub.colors.surface) {
    HubSurface(modifier, color = color, radius = 12.dp, shadow = 3.dp, onClick = onClick, onClickLabel = contentDescription) {
        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) { Icon(icon, contentDescription, tint = if (color == Hub.colors.surface) Hub.colors.ink else Hub.colors.onAccent, modifier = Modifier.size(22.dp)) }
    }
}

@Composable fun Pill(text: String, color: Color = Hub.colors.lime, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, selected: Boolean = true) {
    val c = Hub.colors
    val target = if (selected) color else c.surface
    val fill by animateColorAsState(target, tween(200), label = "pill")
    val shape = RoundedCornerShape(50)
    Box(modifier.clip(shape).background(fill).border(1.5.dp, c.outline, shape)
        .then(if (onClick != null) Modifier.clickable(role = Role.Tab, onClick = onClick).defaultMinSize(minHeight = 40.dp) else Modifier)
        .padding(horizontal = 12.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (target == c.surface) c.ink else c.onAccent, maxLines = 1, softWrap = false)
    }
}

/** Single-choice pill row; used for tabs, ranges and filters. */
@Composable fun PillTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, color: Color = Hub.colors.lime) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label -> Pill(label, color, onClick = { onSelect(i) }, selected = i == selected) }
    }
}

/** Reference-style outlined square choice cells (grade selector). */
@Composable fun ChoiceCells(options: List<String>, selected: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, description: String = "") {
    val c = Hub.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val shape = RoundedCornerShape(8.dp)
            val cell by animateColorAsState(if (on) c.lime else c.surface, tween(200), label = "cell")
            val pop by animateFloatAsState(if (on) 1f else 0.96f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "cellScale")
            Box(Modifier.weight(1f).heightIn(min = 44.dp).scale(pop).clip(shape).background(cell).border(1.5.dp, c.outline, shape)
                .clickable(role = Role.RadioButton, onClickLabel = "$description $label") { onSelect(i) }, contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = if (on) c.onAccent else c.ink)
            }
        }
    }
}

@Composable fun ScreenTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null, onBack: (() -> Unit)? = null, back: ImageVector? = null, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 52.dp), contentAlignment = Alignment.Center) {
            if (onBack != null && back != null) HubIconButton(back, "Back", onBack, Modifier.align(Alignment.CenterStart))
            Text(title, style = MaterialTheme.typography.displayMedium, color = Hub.colors.ink, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = if (onBack != null || action != null) 60.dp else 0.dp).align(if (onBack != null) Alignment.Center else Alignment.CenterStart))
            action?.let { Box(Modifier.align(Alignment.CenterEnd)) { it() } }
        }
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Hub.colors.inkSoft, textAlign = if (onBack != null) TextAlign.Center else TextAlign.Start, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Hub.colors.ink, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable fun BigStat(label: String, value: String, modifier: Modifier = Modifier, caption: String? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Hub.colors.ink)
        Text(value, style = MaterialTheme.typography.displayLarge, color = Hub.colors.ink)
        caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft, textAlign = TextAlign.Center) }
    }
}

/** Equal-width stat tiles for long values ("2m 41s"): one line each, never overlapping neighbours. */
@Composable fun StatRow(vararg stats: Triple<String, String, String?>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.forEach { (label, value, caption) ->
            HubSurface(Modifier.weight(1f), radius = 14.dp, shadow = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = Hub.colors.inkSoft, maxLines = 1)
                    Text(value, style = MaterialTheme.typography.headlineSmall, color = Hub.colors.ink, maxLines = 1, softWrap = false)
                    caption?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Hub.colors.inkSoft, maxLines = 1, textAlign = TextAlign.Center) }
                }
            }
        }
    }
}

@Composable fun HubDivider(modifier: Modifier = Modifier) { Box(modifier.fillMaxWidth().height(Hub.Stroke).background(Hub.colors.outline)) }

/** [committed]: a saved value shows greyed out until the field is tapped again. */
@Composable fun HubField(value: String, onValue: (String) -> Unit, label: String, modifier: Modifier = Modifier, placeholder: String = "",
    singleLine: Boolean = true, number: Boolean = false, committed: Boolean = false, trailing: (@Composable () -> Unit)? = null) {
    val c = Hub.colors
    var focused by remember { mutableStateOf(false) }
    val dim = committed && !focused
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelLarge, color = c.ink)
        HubSurface(Modifier.fillMaxWidth(), radius = 12.dp, shadow = if (dim) 0.dp else 3.dp, color = if (dim) c.raised else c.surface) {
            Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = c.inkSoft)
                    BasicTextField(value, onValue, singleLine = singleLine, textStyle = MaterialTheme.typography.bodyLarge.copy(color = if (dim) c.inkSoft else c.ink), cursorBrush = SolidColor(c.ink),
                        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
                        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused })
                }
                trailing?.invoke()
            }
        }
    }
}

@Composable fun HubCheck(checked: Boolean, onChange: (Boolean) -> Unit, contentDescription: String, color: Color = Hub.colors.mint, check: ImageVector? = null) {
    val c = Hub.colors
    val shape = RoundedCornerShape(7.dp)
    val box by animateColorAsState(if (checked) color else c.surface, tween(180), label = "check")
    val pop by animateFloatAsState(if (checked) 1f else 0.9f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium), label = "checkScale")
    Box(Modifier.size(48.dp).clickable(role = Role.Checkbox, onClickLabel = contentDescription) { onChange(!checked) }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(26.dp).scale(pop).clip(shape).background(box).border(Hub.Stroke, c.outline, shape), contentAlignment = Alignment.Center) {
            if (checked && check != null) Icon(check, contentDescription, tint = c.onAccent, modifier = Modifier.size(18.dp))
        }
    }
}

/** Progress bar drawn as an outlined capsule; value is clamped to [0,1]. */
@Composable fun HubBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Hub.colors.lime) {
    val c = Hub.colors
    val shape = RoundedCornerShape(50)
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(450), label = "bar")
    Box(modifier.fillMaxWidth().height(14.dp).clip(shape).background(c.surface).border(1.5.dp, c.outline, shape)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(shape).background(color).border(1.5.dp, c.outline, shape))
    }
}

data class NavItem(val id: String, val label: String, val icon: ImageVector)
/** Bottom bar from REF-04: flat surface, top rule, selected icon inside an outlined lime capsule. */
@Composable fun HubBottomBar(items: List<NavItem>, selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Hub.colors
    Column(modifier.fillMaxWidth().background(c.surface)) {
        HubDivider()
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
            items.forEach { item ->
                val on = item.id == selected
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Tab, onClickLabel = item.label) { onSelect(item.id) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val shape = RoundedCornerShape(50)
                    val capsule by animateColorAsState(if (on) c.lime else c.surface, tween(220), label = "nav")
                    val rim by animateColorAsState(if (on) c.outline else c.surface, tween(220), label = "navRim")
                    val pad by animateDpAsState(if (on) 18.dp else 12.dp, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "navPad")
                    val iconScale by animateFloatAsState(if (on) 1.08f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "navIcon")
                    Box(Modifier.clip(shape).background(capsule).border(1.5.dp, rim, shape)
                        .padding(horizontal = pad, vertical = 5.dp)) {
                        Icon(item.icon, null, tint = if (on) c.onAccent else c.ink, modifier = Modifier.size(22.dp).scale(iconScale))
                    }
                    Text(item.label, style = MaterialTheme.typography.labelMedium, color = c.ink, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

@Composable fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    HubPanel(modifier, color = Hub.colors.surface, padding = 22.dp) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Hub.colors.ink)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = Hub.colors.inkSoft)
        action?.invoke()
    }
}

fun DataStatus.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }
@Composable fun DataStatus.color(): Color = when (this) {
    DataStatus.CONTENT -> Hub.colors.mint; DataStatus.LOADING -> Hub.colors.cyan; DataStatus.EMPTY -> Hub.colors.raised
    DataStatus.PARTIAL, DataStatus.STALE -> Hub.colors.apricot; DataStatus.ERROR -> Hub.colors.peach; DataStatus.UNAVAILABLE -> Hub.colors.lavender
}
@Composable fun StatusCard(status: DataStatus, title: String, detail: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    HubCard(title, modifier, trailing = { Pill(status.label(), status.color()) }) {
        Text(detail, style = MaterialTheme.typography.bodyLarge, color = Hub.colors.inkSoft)
        if (status == DataStatus.LOADING) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Hub.colors.ink, trackColor = Hub.colors.raised)
        action?.invoke()
    }
}

@Composable fun Dot(color: Color, size: Dp = 10.dp) { Box(Modifier.size(size).clip(CircleShape).background(color).border(1.dp, Hub.colors.outline, CircleShape)) }
