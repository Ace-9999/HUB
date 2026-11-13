package dev.personalhub.monitoring

import android.os.SystemClock
import dev.personalhub.usage.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.*

/**
 * Bridges the accessibility service and the usage repository: measures sessions, persists intervals, keeps today's
 * totals in memory and evaluates limits/curfew on every context change and periodic tick (FOC-02, FOC-06..FOC-08).
 * All calls arrive on the main thread from the service.
 */
class UsageEngine(private val repo: UsageRepository, private val zone: ZoneId, private val scope: CoroutineScope) : TrackerStatus {
    private val mutable = MutableStateFlow(TrackerHealth(classifierVersion = SurfaceClassifier.VERSION))
    override val health: StateFlow<TrackerHealth> = mutable.asStateFlow()

    @Volatile var settings: FocusSettings = FocusSettings(); private set
    @Volatile var rules: List<LimitRule> = emptyList(); private set
    private var todayDate: LocalDate? = null
    private val today = mutableListOf<UsageInterval>()
    private var override: Override? = null
    private val tracker = SessionTracker(zone) { interval ->
        if (interval.start.atZone(zone).toLocalDate() == todayDate) today += interval
        scope.launch { repo.add(interval) }
    }

    init {
        scope.launch { repo.settings.collect { settings = it; if (!it.tracking) stopSession() } }
        scope.launch { repo.rules.collect { rules = it } }
    }

    private fun now() = SystemClock.elapsedRealtime() to Instant.now()

    suspend fun refreshDay() {
        val d = LocalDate.now(zone)
        if (d == todayDate) return
        val start = d.atStartOfDay(zone).toInstant()
        val rows = repo.intervalsBetween(start, start.plus(Duration.ofDays(1)))
        today.clear(); today += rows.filter { it.start.atZone(zone).toLocalDate() == d }
        todayDate = d
        override = repo.activeOverride(Instant.now())
    }

    fun connected(value: Boolean) {
        mutable.value = mutable.value.copy(connected = value)
        scope.launch { if (value) { repo.closeGap(Instant.now()); refreshDay() } else repo.openGap(Instant.now(), GapReason.SERVICE_STOPPED) }
    }

    /** Foreground context changed (null = not on a target). Returns a block reason when the owner must be redirected. */
    fun onContext(ctx: UsageContext?): BlockReason? {
        if (!settings.tracking) return null
        val (e, w) = now()
        tracker.onContext(ctx, e, w)
        publish(e)
        return ctx?.let { check(it) }
    }

    fun onScroll(deltaY: Int?, viewport: Int?) { if (settings.tracking) tracker.onScroll(SystemClock.elapsedRealtime(), deltaY, viewport) }

    /** Periodic while a target is open: checkpoint once a minute and re-check budgets as time passes. */
    fun tick(checkpoint: Boolean): BlockReason? {
        val ctx = tracker.current ?: return null
        val (e, w) = now()
        if (checkpoint || w.atZone(zone).toLocalDate() != todayDate) tracker.checkpoint(e, w)
        if (w.atZone(zone).toLocalDate() != todayDate) scope.launch { refreshDay() }
        publish(e)
        return check(ctx)
    }

    fun stopSession() { val (e, w) = now(); tracker.stop(e, w); publish(e) }

    fun noteOverride(o: Override) { override = o }

    private fun check(ctx: UsageContext): BlockReason? {
        val (e, w) = now()
        val open = tracker.openInterval(e, w)
        val all = if (open != null) today + open else today
        if (override?.until?.isAfter(w) == false) override = null
        return evaluate(ctx, settings, rules, { usedFor(it, all) }, w.atZone(zone), override)
    }

    /** Today's usage including the open segment (for the redirect explanation). */
    fun usedToday(rule: LimitRule): Duration { val (e, w) = now(); return usedFor(rule, today + listOfNotNull(tracker.openInterval(e, w))) }

    private fun publish(e: Long) {
        mutable.value = mutable.value.copy(lastEvent = Instant.now(), current = tracker.current, openSeconds = tracker.openMillis(e) / 1000)
    }
}

/** Implemented by the Application so the system-created service can reach the single engine instance. */
interface UsageHost { val usageEngine: UsageEngine; fun showBlock(reason: BlockReason, ctx: UsageContext) }
