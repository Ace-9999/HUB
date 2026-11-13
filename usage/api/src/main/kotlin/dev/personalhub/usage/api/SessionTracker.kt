package dev.personalhub.usage.api

import java.time.Instant
import java.time.ZoneId

/**
 * Turns a stream of foreground-context changes and scroll events into non-overlapping intervals (SRS §7.2).
 * Monotonic elapsed time measures duration; [wall] anchors persisted instants. Single-threaded by contract.
 */
class SessionTracker(private val zone: ZoneId, private val sink: (UsageInterval) -> Unit) {
    private class Open(val ctx: UsageContext, val startElapsed: Long, val startWall: Instant) {
        var bursts = 0; var screens = 0.0; var hasDelta = false
        var burstStart = Long.MIN_VALUE; var lastScroll = Long.MIN_VALUE
    }
    private var open: Open? = null

    val current: UsageContext? get() = open?.ctx
    /** Elapsed milliseconds of the open segment (for live budget checks). */
    fun openMillis(nowElapsed: Long): Long = open?.let { nowElapsed - it.startElapsed } ?: 0
    fun openInterval(nowElapsed: Long, nowWall: Instant): UsageInterval? = open?.let { build(it, nowElapsed, nowWall).lastOrNull() }

    /** Foreground changed. Null = not on a target (other app, home, screen off, lock). Same context keeps the segment. */
    fun onContext(ctx: UsageContext?, nowElapsed: Long, nowWall: Instant) {
        val o = open
        if (o != null && o.ctx == ctx) return
        if (o != null) close(o, nowElapsed, nowWall)
        open = ctx?.let { Open(it, nowElapsed, nowWall) }
    }

    /**
     * Scroll event on the current target. A burst groups events until a gap > 300 ms, capped at a 1,000 ms window.
     * Screen lengths accumulate only when a vertical delta and viewport height are both known.
     */
    fun onScroll(nowElapsed: Long, deltaY: Int?, viewport: Int?) {
        val o = open ?: return
        val newBurst = o.lastScroll == Long.MIN_VALUE || nowElapsed - o.lastScroll > GAP_MS || nowElapsed - o.burstStart > WINDOW_MS
        if (newBurst) { o.bursts++; o.burstStart = nowElapsed }
        o.lastScroll = nowElapsed
        if (deltaY != null && viewport != null && viewport > 0 && kotlin.math.abs(deltaY) < viewport * 4) {
            o.screens += kotlin.math.abs(deltaY).toDouble() / viewport; o.hasDelta = true
        }
    }

    /** Persists the open segment so far and continues measuring (crash safety / midnight split). */
    fun checkpoint(nowElapsed: Long, nowWall: Instant) {
        val o = open ?: return
        close(o, nowElapsed, nowWall)
        open = Open(o.ctx, nowElapsed, nowWall)
    }

    fun stop(nowElapsed: Long, nowWall: Instant) = onContext(null, nowElapsed, nowWall)

    private fun close(o: Open, nowElapsed: Long, nowWall: Instant) { build(o, nowElapsed, nowWall).forEach(sink) }

    private fun build(o: Open, nowElapsed: Long, nowWall: Instant): List<UsageInterval> {
        val millis = (nowElapsed - o.startElapsed).coerceAtLeast(0)
        if (millis < MIN_MS) return emptyList()
        // Wall clock can jump; duration always comes from the monotonic clock.
        val end = o.startWall.plusMillis(millis)
        val pieces = splitAtMidnight(o.startWall, end, zone)
        return pieces.mapIndexed { i, (s, e) ->
            val last = i == pieces.lastIndex
            UsageInterval(o.ctx.target, o.ctx.surface, o.ctx.source, o.ctx.pkg, s, e,
                if (last) o.bursts else 0, if (last && o.hasDelta) o.screens else null, o.ctx.classifierVersion)
        }
    }

    companion object { const val GAP_MS = 300L; const val WINDOW_MS = 1_000L; const val MIN_MS = 1_000L }
}
