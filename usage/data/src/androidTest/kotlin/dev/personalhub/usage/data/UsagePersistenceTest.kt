package dev.personalhub.usage.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.personalhub.usage.api.*
import dev.personalhub.usage.api.Target
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.UUID

// Real SQLite: intervals by reporting day, rules/settings/overrides survive reopen, gaps open/close.
class UsagePersistenceTest {
    @get:org.junit.Rule val helper = androidx.room.testing.MigrationTestHelper(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(), UsageDatabase::class.java)

    @Test fun versionOneSettingsMigrateToGlobalMode() = runBlocking<Unit> {
        val name = "test-${UUID.randomUUID()}.db"
        try {
            helper.createDatabase(name, 1).apply { execSQL("INSERT INTO settings VALUES (1, 1, 0, 1, '23:00', '06:00')"); close() }
            helper.runMigrationsAndValidate(name, 2, true, UsageDatabase.MIGRATION_1_2).close()
            val repo = RoomUsageRepository.open(context, name)
            try {
                val s = repo.settings.first(); assertTrue(s.tracking && s.curfew.enabled); assertFalse(s.perApp)
                repo.saveSettings(s.copy(perApp = true)); assertTrue(repo.settings.first().perApp)
            } finally { repo.close() }
        } finally { context.deleteDatabase(name) }
    }
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test fun intervalsRulesAndSettingsSurviveReopen() = runBlocking<Unit> {
        val name = "test-${UUID.randomUUID()}.db"
        try {
            var repo = RoomUsageRepository.open(context, name)
            val day = LocalDate.of(2026, 11, 13)
            val s = day.atTime(10, 0).atZone(zone).toInstant()
            repo.add(UsageInterval(Target.INSTAGRAM, Surface.VIDEO, Source.APP, "com.instagram.android", s, s.plusSeconds(600), 12, 8.5))
            repo.add(UsageInterval(Target.REDDIT, Surface.TEXT_FEED, Source.BROWSER, "com.android.chrome", s.plusSeconds(700), s.plusSeconds(1000), 3, null))
            repo.add(UsageInterval(Target.REDDIT, Surface.TEXT_FEED, Source.APP, "com.reddit.frontpage", s.plus(Duration.ofDays(1)), s.plus(Duration.ofDays(1)).plusSeconds(60), 0, null))
            repo.saveRule(LimitRule("r1", null, Surface.VIDEO, 30))
            repo.saveSettings(FocusSettings(tracking = true, strict = true, curfew = Curfew(true, LocalTime.of(23, 0), LocalTime.of(6, 0))))
            repo.addOverride(Override(s.plusSeconds(300), s, "synthetic"))
            repo.openGap(s, GapReason.PAUSED); repo.closeGap(s.plusSeconds(60))
            repo.close(); repo = RoomUsageRepository.open(context, name)
            try {
                val today = repo.day(day, zone).first()
                assertEquals(Duration.ofMinutes(10), today.time(Surface.VIDEO)); assertEquals(Duration.ofSeconds(300), today.time(Surface.TEXT_FEED))
                assertEquals(15, today.bursts); assertEquals(8.5, today.screens!!, 0.0)
                assertEquals(1, repo.days(day, day.plusDays(1), zone).first()[1].intervals.size)
                assertEquals(30, repo.rules.first().single().dailyMinutes)
                val st = repo.settings.first(); assertTrue(st.tracking && st.strict && st.curfew.enabled)
                assertNotNull(repo.activeOverride(s.plusSeconds(10))); assertNull(repo.activeOverride(s.plusSeconds(400)))
                assertNotNull(repo.gaps.first().single().end)
            } finally { repo.close() }
        } finally { context.deleteDatabase(name) }
    }
}
