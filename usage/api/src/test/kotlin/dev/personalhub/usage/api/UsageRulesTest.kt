package dev.personalhub.usage.api

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class UsageRulesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val t0 = Instant.parse("2026-11-13T10:00:00Z")
    private val reels = UsageContext(Target.INSTAGRAM, Surface.VIDEO, Source.APP, "com.instagram.android")
    private val feed = UsageContext(Target.REDDIT, Surface.TEXT_FEED, Source.APP, "com.reddit.frontpage")

    @Test fun trackerProducesNonOverlappingIntervalsFromMonotonicTime() {
        val out = mutableListOf<UsageInterval>()
        val t = SessionTracker(zone) { out += it }
        t.onContext(reels, 0, t0)
        t.onContext(reels, 5_000, t0.plusSeconds(5)) // same context: no split
        t.onContext(feed, 60_000, t0.plusSeconds(3600)) // wall clock jumped; elapsed says 60 s
        t.onContext(null, 90_000, t0.plusSeconds(3630))
        assertEquals(2, out.size)
        assertEquals(Duration.ofSeconds(60), out[0].duration); assertEquals(Surface.VIDEO, out[0].surface)
        assertEquals(Duration.ofSeconds(30), out[1].duration)
        assertFalse(out[1].start.isBefore(out[0].end))
    }
    @Test fun scrollBurstsDeduplicateFlingsAndCapWindow() {
        val out = mutableListOf<UsageInterval>()
        val t = SessionTracker(zone) { out += it }
        t.onContext(feed, 0, t0)
        listOf(100L, 150, 200, 250).forEach { t.onScroll(it, 600, 2000) }        // one fling = 1 burst
        listOf(1000L, 1250, 1500, 1750, 2000, 2250).forEach { t.onScroll(it, null, null) } // >1 s continuous = 2 bursts
        t.onScroll(5000, 2000, 2000)                                                // separate burst
        t.stop(10_000, t0.plusSeconds(10))
        assertEquals(4, out.single().scrollBursts)
        assertEquals(2.2, out.single().screens!!, 1e-9)
    }
    @Test fun screensAreNullWithoutReliableDeltas() {
        val out = mutableListOf<UsageInterval>()
        val t = SessionTracker(zone) { out += it }
        t.onContext(feed, 0, t0); t.onScroll(100, null, null); t.stop(5_000, t0.plusSeconds(5))
        assertNull(out.single().screens); assertEquals(1, out.single().scrollBursts)
    }
    @Test fun intervalsSplitAtLocalMidnight() {
        val out = mutableListOf<UsageInterval>()
        val t = SessionTracker(zone) { out += it }
        val beforeMidnight = LocalDateTime.of(2026, 11, 13, 23, 50).atZone(zone).toInstant()
        t.onContext(reels, 0, beforeMidnight); t.stop(20 * 60_000L, beforeMidnight.plusSeconds(1200))
        assertEquals(2, out.size)
        assertEquals(Duration.ofMinutes(10), out[0].duration); assertEquals(LocalDate.of(2026, 11, 14), out[1].start.atZone(zone).toLocalDate())
    }
    @Test fun hostMatchingIsExactNotSubstring() {
        assertEquals(Target.REDDIT, Target.forHost("old.reddit.com")); assertEquals(Target.X, Target.forHost("twitter.com"))
        assertNull(Target.forHost("reddit.com.example.org")); assertNull(Target.forHost("notinstagram.com"))
        assertEquals(Target.INSTAGRAM to Surface.VIDEO, classifyUrl("instagram.com/reels/abc"))
        assertEquals(Surface.TEXT_FEED, classifyUrl("https://x.com/home")!!.second)
        assertEquals(Surface.VIDEO, classifyUrl("x.com/someone/status/123")!!.second)
        assertEquals(Surface.TEXT_FEED, classifyUrl("x.com")!!.second)
        assertNull(classifyUrl("search for reddit"))
    }
    @Test fun curfewCrossesMidnightAndBudgetsBlockMostRestrictive() {
        val settings = FocusSettings(tracking = true, curfew = Curfew(true, LocalTime.of(23, 0), LocalTime.of(6, 0)))
        assertTrue(settings.curfew.active(LocalTime.of(23, 30))); assertTrue(settings.curfew.active(LocalTime.of(2, 0)))
        assertFalse(settings.curfew.active(LocalTime.of(6, 0))); assertFalse(settings.curfew.active(LocalTime.of(22, 59)))
        val night = ZonedDateTime.of(2026, 11, 13, 23, 15, 0, 0, zone)
        val c = evaluate(reels, settings, emptyList(), { Duration.ZERO }, night, null) as BlockReason.CurfewWindow
        assertEquals(LocalDateTime.of(2026, 11, 14, 6, 0), c.until)
        val day = ZonedDateTime.of(2026, 11, 13, 15, 0, 0, 0, zone)
        val video = LimitRule("v", null, Surface.VIDEO, 30); val ig = LimitRule("i", Target.INSTAGRAM, null, 60)
        val used = mapOf("v" to Duration.ofMinutes(31), "i" to Duration.ofMinutes(20))
        val b = evaluate(reels, settings, listOf(video, ig), { used.getValue(it.id) }, day, null) as BlockReason.Budget
        assertEquals("v", b.rule.id)
        assertNull(evaluate(feed, settings, listOf(video, ig), { used.getValue(it.id) }, day, null)) // video rule doesn't apply to feed
        assertNull(evaluate(reels, settings, listOf(video), { used.getValue(it.id) }, day, Override(day.toInstant().plusSeconds(60), day.toInstant(), "x")))
        assertNull(evaluate(reels, settings.copy(tracking = false), listOf(video), { used.getValue(it.id) }, day, null))
    }
    @Test fun modeSelectsGlobalOrPerAppRulesAndDurationsFormat() {
        val now = ZonedDateTime.of(2026, 11, 13, 15, 0, 0, 0, zone)
        val global = LimitRule(ruleId(null, Surface.VIDEO), null, Surface.VIDEO, 10)
        val perApp = LimitRule(ruleId(Target.INSTAGRAM, Surface.VIDEO), Target.INSTAGRAM, Surface.VIDEO, 60)
        val used = { _: LimitRule -> Duration.ofMinutes(20) }
        assertNotNull(evaluate(reels, FocusSettings(tracking = true), listOf(global, perApp), used, now, null))
        assertNull(evaluate(reels, FocusSettings(tracking = true, perApp = true), listOf(global, perApp), used, now, null))
        assertEquals("20s", formatDuration(Duration.ofSeconds(20))); assertEquals("3m 28s", formatDuration(Duration.ofSeconds(208)))
        assertEquals("1h 28m", formatDuration(Duration.ofSeconds(5280 + 59)))
    }
    @Test fun zeroBudgetBlocksImmediatelyAndUnknownCountsOnlyServiceWide() {
        val s = FocusSettings(tracking = true, perApp = true)
        val now = ZonedDateTime.of(2026, 11, 13, 15, 0, 0, 0, zone)
        assertNotNull(evaluate(reels, s, listOf(LimitRule("z", Target.INSTAGRAM, null, 0)), { Duration.ZERO }, now, null))
        val unknown = UsageInterval(Target.INSTAGRAM, Surface.UNKNOWN, Source.BROWSER, "com.android.chrome", t0, t0.plusSeconds(600), 0, null)
        assertEquals(Duration.ofMinutes(10), usedFor(LimitRule("all", Target.INSTAGRAM, null, 60), listOf(unknown)))
        assertEquals(Duration.ZERO, usedFor(LimitRule("v", Target.INSTAGRAM, Surface.VIDEO, 60), listOf(unknown)))
    }
}
