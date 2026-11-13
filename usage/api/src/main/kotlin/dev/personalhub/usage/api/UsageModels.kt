package dev.personalhub.usage.api

import kotlinx.coroutines.flow.Flow
import java.net.URI
import java.time.*

/*
 * Usage tracking domain (FOC-02..FOC-11, SRS §7.2). Pure Kotlin: no Android, accessibility nodes or content text.
 * Only target identity, surface category, timing and scroll counts are modelled.
 */

enum class Target(val label: String, val packages: Set<String>, val hosts: Set<String>) {
    INSTAGRAM("Instagram", setOf("com.instagram.android"), setOf("instagram.com")),
    X("X", setOf("com.twitter.android"), setOf("x.com", "twitter.com")),
    REDDIT("Reddit", setOf("com.reddit.frontpage"), setOf("reddit.com")),
    YOUTUBE("YouTube", setOf("com.google.android.youtube"), emptySet());

    companion object {
        fun forPackage(pkg: String?): Target? = entries.firstOrNull { pkg in it.packages }
        /** Exact host or explicit subdomain match; never a substring (reddit.com.example.org is rejected). */
        fun forHost(host: String?): Target? {
            val h = host?.lowercase()?.trimEnd('.') ?: return null
            return entries.firstOrNull { t -> t.hosts.any { h == it || h.endsWith(".$it") } }
        }
    }
}

/** Browsers whose address bar is read (host/path only) to recognise target sites. */
val BROWSERS = setOf("com.android.chrome", "com.brave.browser")

enum class Surface(val label: String) { VIDEO("Reels"), TEXT_FEED("Feed scrolling"), OTHER("Other"), UNKNOWN("Unclassified") }
enum class Source { APP, BROWSER }

data class UsageContext(val target: Target, val surface: Surface, val source: Source, val pkg: String, val classifierVersion: Int = 1)

data class UsageInterval(
    val target: Target, val surface: Surface, val source: Source, val pkg: String,
    val start: Instant, val end: Instant, val scrollBursts: Int, val screens: Double?, val classifierVersion: Int = 1,
) {
    init { require(!end.isBefore(start)) }
    val duration: Duration get() = Duration.between(start, end)
}

enum class GapReason { SERVICE_STOPPED, PAUSED, PERMISSION_LOST }
data class CoverageGap(val start: Instant, val end: Instant?, val reason: GapReason)

/** Parses a browser address-bar value into a target + surface. Path is used only when the bar shows it. */
fun classifyUrl(text: String?): Pair<Target, Surface>? {
    val raw = text?.trim()?.takeIf { it.isNotEmpty() && ' ' !in it } ?: return null
    val uri = runCatching { URI(if ("://" in raw) raw else "https://$raw") }.getOrNull() ?: return null
    val target = Target.forHost(uri.host) ?: return null
    val path = uri.path.orEmpty().lowercase()
    val surface = when {
        target == Target.INSTAGRAM && (path.startsWith("/reel") || path.startsWith("/reels")) -> Surface.VIDEO
        target == Target.INSTAGRAM && path.startsWith("/stories") -> Surface.VIDEO
        // Owner-verified X web routes: /<author>/status/<id> opens the video/post viewer; /home is the timeline.
        target == Target.X && (path.contains("/status/") || path.startsWith("/i/videos")) -> Surface.VIDEO
        target == Target.X || target == Target.REDDIT -> Surface.TEXT_FEED
        target == Target.INSTAGRAM && (path.isEmpty() || path == "/") -> Surface.TEXT_FEED
        else -> Surface.UNKNOWN
    }
    return target to surface
}

// ---------------------------------------------------------------- limits
/** Daily budget. [target] null = every target; [surface] null = all surfaces of the scope. 0 minutes = block immediately. */
data class LimitRule(val id: String, val target: Target?, val surface: Surface?, val dailyMinutes: Int, val enabled: Boolean = true) {
    init { require(dailyMinutes in 0..24 * 60) }
    fun applies(ctx: UsageContext) = enabled && (target == null || target == ctx.target) && (surface == null || surface == ctx.surface)
    fun counts(i: Target, s: Surface) = (target == null || target == i) && (surface == null || surface == s)
    val scopeLabel: String get() = "${target?.label ?: "All apps & sites"} · ${surface?.label ?: "all scrolling"}"
}

/** Scheduled block window, e.g. 23:00–06:00. Crosses midnight when end <= start. */
data class Curfew(val enabled: Boolean, val start: LocalTime, val end: LocalTime) {
    fun active(at: LocalTime): Boolean = enabled && if (end > start) at >= start && at < end else at >= start || at < end
    /** Next local time the window ends (for the explanation screen). */
    fun endsAt(now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate().atTime(end)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }
}

data class FocusSettings(
    val tracking: Boolean = false, val strict: Boolean = false,
    val curfew: Curfew = Curfew(false, LocalTime.of(23, 0), LocalTime.of(6, 0)),
    /** Off: global reels/video and feed limits across all apps. On: each app's own limits. Inactive rules are kept, not deleted. */
    val perApp: Boolean = false,
)

/** Rules in force for the selected mode. */
fun activeRules(rules: List<LimitRule>, settings: FocusSettings) = rules.filter { if (settings.perApp) it.target != null else it.target == null }
fun ruleId(target: Target?, surface: Surface) = "${target?.name ?: "ALL"}:${surface.name}"

/** 20s · 3m 28s · 1h 28m */
fun formatDuration(d: Duration): String {
    val s = d.seconds.coerceAtLeast(0)
    return when { s < 60 -> "${s}s"; s < 3600 -> "${s / 60}m ${s % 60}s"; else -> "${s / 3600}h ${(s % 3600) / 60}m" }
}

data class Override(val until: Instant, val createdAt: Instant, val reason: String)

sealed interface BlockReason {
    data class Budget(val rule: LimitRule, val used: Duration) : BlockReason
    data class CurfewWindow(val until: LocalDateTime) : BlockReason
}

/**
 * The most restrictive applicable rule wins: curfew first, then any exhausted budget. An active override suspends
 * blocking until it expires. Unknown time counts toward service-wide (surface-null) budgets only.
 */
fun evaluate(ctx: UsageContext, settings: FocusSettings, rules: List<LimitRule>, used: (LimitRule) -> Duration,
             now: ZonedDateTime, override: Override?): BlockReason? {
    if (!settings.tracking) return null
    if (override != null && override.until.isAfter(now.toInstant())) return null
    if (settings.curfew.active(now.toLocalTime())) return BlockReason.CurfewWindow(settings.curfew.endsAt(now.toLocalDateTime()))
    return activeRules(rules, settings).filter { it.applies(ctx) }.map { it to used(it) }
        .filter { (r, u) -> u >= Duration.ofMinutes(r.dailyMinutes.toLong()) }
        .maxByOrNull { (r, u) -> u.toMinutes() - r.dailyMinutes }?.let { (r, u) -> BlockReason.Budget(r, u) }
}

fun usedFor(rule: LimitRule, intervals: List<UsageInterval>): Duration =
    intervals.filter { rule.counts(it.target, it.surface) }.fold(Duration.ZERO) { a, i -> a + i.duration }

// ---------------------------------------------------------------- summaries
data class UsageDay(val date: LocalDate, val intervals: List<UsageInterval>) {
    fun time(surface: Surface? = null, target: Target? = null): Duration = intervals.filter { (surface == null || it.surface == surface) && (target == null || it.target == target) }
        .fold(Duration.ZERO) { a, i -> a + i.duration }
    val bursts: Int get() = intervals.sumOf { it.scrollBursts }
    fun bursts(surface: Surface) = intervals.filter { it.surface == surface }.sumOf { it.scrollBursts }
    val screens: Double? get() = intervals.mapNotNull { it.screens }.takeIf { it.isNotEmpty() }?.sum()
}

/** Splits [start, end) at local midnights so each piece belongs to one reporting day. */
fun splitAtMidnight(start: Instant, end: Instant, zone: ZoneId): List<Pair<Instant, Instant>> {
    val out = mutableListOf<Pair<Instant, Instant>>()
    var s = start
    while (s.isBefore(end)) {
        val midnight = s.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        val e = if (midnight.isBefore(end)) midnight else end
        out += s to e; s = e
    }
    return out
}

interface UsageRepository {
    val settings: Flow<FocusSettings>
    val rules: Flow<List<LimitRule>>
    fun day(date: LocalDate, zone: ZoneId): Flow<UsageDay>
    fun days(from: LocalDate, to: LocalDate, zone: ZoneId): Flow<List<UsageDay>>
    val gaps: Flow<List<CoverageGap>>
    suspend fun intervalsBetween(start: Instant, end: Instant): List<UsageInterval>
    suspend fun add(interval: UsageInterval)
    suspend fun saveSettings(settings: FocusSettings)
    suspend fun saveRule(rule: LimitRule)
    suspend fun deleteRule(id: String)
    suspend fun addOverride(o: Override)
    suspend fun activeOverride(now: Instant): Override?
    suspend fun openGap(at: Instant, reason: GapReason)
    suspend fun closeGap(at: Instant)
    suspend fun exportAll(): UsageBackup
    suspend fun replaceAll(b: UsageBackup)
}
data class UsageBackup(val settings: FocusSettings, val rules: List<LimitRule>, val intervals: List<UsageInterval>, val overrides: List<Override>, val gaps: List<CoverageGap>)

/** Live tracker state for the health card (FOC-10). Held in memory by the running service. */
data class TrackerHealth(val connected: Boolean = false, val lastEvent: Instant? = null, val current: UsageContext? = null,
    val openSeconds: Long = 0, val classifierVersion: Int = 1)
interface TrackerStatus { val health: kotlinx.coroutines.flow.StateFlow<TrackerHealth> }
