package dev.personalhub.monitoring

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.personalhub.usage.api.BROWSERS
import dev.personalhub.usage.api.BlockReason
import dev.personalhub.usage.api.Target

/*
 * Production opt-in tracker (FOC-01..FOC-08). Reads window package, resource-id names and the browser address bar
 * only — never post text, captions, messages or screenshots. Redirects use the global Home action plus a local
 * explanation screen; it never force-stops apps and never acts on system settings or PersonalHub itself.
 */
class UsageTrackerService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var engine: UsageEngine
    private lateinit var host: UsageHost
    private var lastClassify = 0L
    private var lastPkg: String? = null
    private var lastRedirect = 0L
    private var ticks = 0
    private val screenOff = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { engine.stopSession() } }
    private val tick = object : Runnable {
        override fun run() {
            ticks++
            engine.tick(checkpoint = ticks % 4 == 0)?.let { redirect(it) }
            main.postDelayed(this, TICK_MS)
        }
    }

    override fun onServiceConnected() {
        host = application as UsageHost
        Calibration.file = java.io.File(filesDir, "calibration.log")
        engine = host.usageEngine
        engine.connected(true)
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        main.postDelayed(tick, TICK_MS)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (SurfaceClassifier.isOverlay(pkg)) return
        val relevant = Target.forPackage(pkg) != null || pkg in BROWSERS
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> if (relevant) {
                val delta = if (android.os.Build.VERSION.SDK_INT >= 28) event.scrollDeltaY.takeIf { it != 0 && it != -1 } else null
                val viewport = event.source?.let { n -> Rect().also { n.getBoundsInScreen(it) }.height() }?.takeIf { it > 0 }
                engine.onScroll(delta, viewport)
                classifyThrottled(pkg, force = false)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (relevant) classifyThrottled(pkg, force = true)
                else if (pkg != lastPkg) { lastPkg = pkg; engine.onContext(null) }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> if (relevant) classifyThrottled(pkg, force = false)
        }
    }

    /** Surfaces change inside one window (feed -> reels); re-classify at most every 700 ms, immediately on window change. */
    private fun classifyThrottled(pkg: String, force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && pkg == lastPkg && now - lastClassify < 700) return
        lastClassify = now; lastPkg = pkg
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val ids = HashSet<String>(); var url: String? = null
        val classes = if (Calibration.enabled && pkg in BROWSERS) HashMap<String, Int>() else null
        walk(root, 0) { n ->
            classes?.let { m -> n.className?.toString()?.substringAfterLast('.')?.let { c -> m[c] = (m[c] ?: 0) + 1 } }
            if (n.isVisibleToUser) n.viewIdResourceName?.let { full ->
                val name = full.substringAfter(":id/", full)
                ids += name
                if (n.isSelected) ids += "selected:$name"
                if (pkg in BROWSERS && name == "url_bar") url = n.text?.toString()
            }
        }
        if (Calibration.enabled) Calibration.log(pkg, ids, url, classes)
        engine.onContext(SurfaceClassifier.classify(pkg, ids, url))?.let { redirect(it) }
    }

    private fun walk(n: AccessibilityNodeInfo, depth: Int, visit: (AccessibilityNodeInfo) -> Unit) = walk(n, depth, intArrayOf(400), visit)
    private fun walk(n: AccessibilityNodeInfo, depth: Int, budget: IntArray, visit: (AccessibilityNodeInfo) -> Unit) {
        if (budget[0]-- <= 0 || depth > 30) return
        visit(n)
        for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1, budget, visit) }
    }

    private fun redirect(reason: BlockReason) {
        val now = SystemClock.elapsedRealtime()
        val ctx = engine.health.value.current
        engine.stopSession()
        if (now - lastRedirect < 1_500) return
        lastRedirect = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        if (ctx != null) main.postDelayed({ host.showBlock(reason, ctx) }, 250)
    }

    override fun onInterrupt() {}
    override fun onUnbind(intent: Intent?): Boolean { shutdown(); return super.onUnbind(intent) }
    override fun onDestroy() { shutdown(); super.onDestroy() }
    private var stopped = false
    private fun shutdown() {
        if (stopped || !::engine.isInitialized) return
        stopped = true
        main.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenOff) }
        engine.stopSession(); engine.connected(false)
    }

    companion object { const val TICK_MS = 15_000L }
}

/** Debug-only layout calibration: logs resource-id NAMES (no text) of target windows so surface rules can be tuned. */
object Calibration {
    @Volatile var enabled = false
    /** Private app file (debug builds only read it over USB via run-as). Holds id names and masked URLs only. */
    @Volatile var file: java.io.File? = null
    private var last = ""
    private val KEYWORDS = setOf("i", "status", "video", "videos", "photo", "reel", "reels", "stories", "home", "explore", "search", "comments", "r", "shorts", "watch", "notifications", "messages")
    /** Host plus path; every non-keyword segment is replaced by a star (account names and ids never logged). */
    fun maskUrl(url: String?): String? = url?.substringBefore('?')?.split('/')?.mapIndexed { i, s ->
        if (i == 0 || s.lowercase() in KEYWORDS || s.isEmpty()) s.lowercase() else if (s.all(Char::isDigit) && s.length <= 2) s else "*"
    }?.joinToString("/")
    fun log(pkg: String, ids: Set<String>, url: String? = null, classes: Map<String, Int>? = null) {
        val sig = ids.filter { it.length < 60 }.sorted().take(120).joinToString(",") +
            (if (url != null || classes != null) " url=${maskUrl(url?.removePrefix("https://")?.removePrefix("http://"))} classes=${classes?.entries?.sortedByDescending { it.value }?.take(15)?.joinToString(",") { "${it.key}:${it.value}" }}" else "")
        if (sig == last) return
        last = sig
        val line = "${java.time.LocalTime.now().withNano(0)} $pkg ids=$sig"
        Log.i("PersonalHubCalib", line)
        file?.let { f -> runCatching { if (f.length() > 300_000) f.writeText(""); f.appendText(line + "\n") } }
    }
}
