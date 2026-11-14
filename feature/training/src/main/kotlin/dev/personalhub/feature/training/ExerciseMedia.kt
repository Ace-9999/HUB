package dev.personalhub.feature.training

import android.content.Context
import android.graphics.BitmapFactory
import android.provider.Settings
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.personalhub.design.*
import dev.personalhub.training.api.Exercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/*
 * Exercise demonstration media (GYM-02/03). Two-frame photos from free-exercise-db / wrkout exercises.json, dedicated to
 * the public domain under the Unlicense; per-file source and sha256 in docs/exercise_media_manifest.json.
 * Assets: assets/exercises/<seed-id>_<frame>.jpg. Custom exercises fall back to an initial.
 */
object ExerciseMedia {
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    fun assetName(e: Exercise, frame: Int) = if (e.id.startsWith("seed:")) "exercises/${e.id.removePrefix("seed:")}_$frame.jpg" else null

    /** Decodes off the main thread, downsampled to roughly [targetPx] wide. */
    suspend fun load(context: Context, path: String, targetPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = "$path@$targetPx"
        cache.get(key) ?: runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1; while (bounds.outWidth / (sample * 2) >= targetPx) sample *= 2
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }?.asImageBitmap()
        }.getOrNull()?.also { cache.put(key, it) }
    }
}

@Composable private fun rememberFrame(e: Exercise, frame: Int, targetPx: Int): ImageBitmap? {
    val context = LocalContext.current
    val path = ExerciseMedia.assetName(e, frame) ?: return null
    val state by produceState<ImageBitmap?>(null, path, targetPx) { value = ExerciseMedia.load(context, path, targetPx) }
    return state
}

/** Round thumbnail (REF-12 style). */
@Composable fun ExerciseThumb(e: Exercise?, size: Dp = 48.dp, square: Boolean = false) {
    val img = e?.let { rememberFrame(it, 0, 160) }
    val shape = if (square) RoundedCornerShape(6.dp) else CircleShape
    Box(Modifier.size(size).clip(shape).background(Hub.colors.surface).border(1.5.dp, Hub.colors.outline, shape), contentAlignment = Alignment.Center) {
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(e?.name?.take(1) ?: "?", style = MaterialTheme.typography.titleMedium, color = Hub.colors.ink)
    }
}

/** Large demonstration (REF-11 style): alternates start/end frames; pausable; static when system animations are off. */
@Composable fun ExerciseDemo(e: Exercise) {
    val context = LocalContext.current
    val reduceMotion = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    var playing by remember(e.id) { mutableStateOf(!reduceMotion) }
    var frame by remember(e.id) { mutableIntStateOf(0) }
    val a = rememberFrame(e, 0, 900); val b = rememberFrame(e, 1, 900)
    LaunchedEffect(e.id, playing) { while (playing) { delay(1_300); frame = 1 - frame } }
    if (ExerciseMedia.assetName(e, 0) == null) return
    HubSurface(Modifier.fillMaxWidth(), radius = 18.dp) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(18.dp)).background(Hub.colors.raised)) {
            Crossfade(if (frame == 0) a else b ?: a, animationSpec = tween(450), label = "demo") { img ->
                if (img != null) Image(img, "${e.name} demonstration, ${if (frame == 0) "start" else "end"} position", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Row(Modifier.align(Alignment.TopEnd).padding(10.dp)) {
                HubIconButton(if (playing) HubIcons.Pause else HubIcons.Play, if (playing) "Pause demonstration" else "Play demonstration", { playing = !playing })
            }
            Pill(if (frame == 0) "Start" else "Finish", Hub.colors.lime, Modifier.align(Alignment.BottomStart).padding(10.dp))
        }
    }
    Text("Photo: free-exercise-db (public domain, Unlicense).", style = MaterialTheme.typography.labelSmall, color = Hub.colors.inkSoft)
}
