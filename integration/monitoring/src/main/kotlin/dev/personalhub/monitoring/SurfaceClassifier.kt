package dev.personalhub.monitoring

import dev.personalhub.usage.api.*
import dev.personalhub.usage.api.Target

/**
 * Versioned, adapter-specific surface rules over resource-id names (e.g. "clips_viewer_view_pager") only.
 * No text, descriptions or content are inspected. Unrecognised layouts stay UNKNOWN (FOC-03).
 * Rules are initial heuristics; on-device calibration (owner opens each surface) updates them and bumps VERSION.
 */
object SurfaceClassifier {
    const val VERSION = 1

    /** [ids] are resource-entry names without the package prefix. [url] is the browser address-bar value, if any. */
    fun classify(pkg: String, ids: Set<String>, url: String?): UsageContext? {
        if (pkg in BROWSERS) {
            val (target, surface) = classifyUrl(url) ?: return null
            return UsageContext(target, surface, Source.BROWSER, pkg, VERSION)
        }
        val target = Target.forPackage(pkg) ?: return null
        val surface = when (target) {
            Target.INSTAGRAM -> when {
                ids.any { it.startsWith("clips_") || it.contains("clips_viewer") } -> Surface.VIDEO
                ids.any { it.startsWith("reel_viewer") } -> Surface.VIDEO // stories
                ids.any { it.startsWith("row_feed") || it == "feed_tab" || it.contains("main_feed") } -> Surface.TEXT_FEED
                else -> Surface.UNKNOWN
            }
            Target.YOUTUBE -> when {
                ids.any { it.startsWith("reel_") || it.contains("shorts") } -> Surface.VIDEO
                ids.any { it == "watch_player" || it.startsWith("watch_while") || it == "player_view" || it.contains("player_fragment") } -> Surface.VIDEO
                ids.any { it == "results" || it.contains("browse") || it == "pivot_bar" } -> Surface.TEXT_FEED
                else -> Surface.UNKNOWN
            }
            Target.REDDIT -> when {
                ids.any { it.contains("video") && (it.contains("player") || it.contains("fullscreen")) } -> Surface.VIDEO
                ids.isNotEmpty() -> Surface.TEXT_FEED
                else -> Surface.UNKNOWN
            }
            Target.X -> if (ids.any { it.contains("video") && it.contains("player") }) Surface.VIDEO else if (ids.isNotEmpty()) Surface.TEXT_FEED else Surface.UNKNOWN
        }
        return UsageContext(target, surface, Source.APP, pkg, VERSION)
    }

    /** Packages whose windows never end a session (overlays): system UI, keyboards, our own block screen is NOT here. */
    fun isOverlay(pkg: String?): Boolean = pkg == null || pkg == "com.android.systemui" || pkg.contains("inputmethod") ||
        pkg.endsWith(".keyboard") || pkg == "com.google.android.inputmethod.latin" || pkg.startsWith("com.oplus.") && pkg.contains("floating")
}
