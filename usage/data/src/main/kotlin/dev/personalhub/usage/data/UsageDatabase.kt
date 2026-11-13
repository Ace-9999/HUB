package dev.personalhub.usage.data

import android.content.Context
import androidx.room.*
import dev.personalhub.usage.api.*
import dev.personalhub.usage.api.Target
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.*

// Usage owns its database. Rows hold target/surface identities, times and counts only — never content text or URLs.
@Entity(tableName = "intervals", indices = [Index("startMs")])
internal data class IntervalRow(@PrimaryKey(autoGenerate = true) val id: Long = 0, val target: String, val surface: String, val source: String,
    val pkg: String, val startMs: Long, val endMs: Long, val bursts: Int, val screens: Double?, val classifier: Int)
@Entity(tableName = "rules") internal data class RuleRow(@PrimaryKey val id: String, val target: String?, val surface: String?, val minutes: Int, val enabled: Boolean)
@Entity(tableName = "settings") internal data class SettingsRow(@PrimaryKey val id: Int = 1, val tracking: Boolean, val strict: Boolean,
    val curfewOn: Boolean, val curfewStart: String, val curfewEnd: String)
@Entity(tableName = "overrides") internal data class OverrideRow(@PrimaryKey(autoGenerate = true) val id: Long = 0, val untilMs: Long, val createdMs: Long, val reason: String)
@Entity(tableName = "gaps") internal data class GapRow(@PrimaryKey(autoGenerate = true) val id: Long = 0, val startMs: Long, val endMs: Long?, val reason: String)

@Dao internal interface UsageDao {
    @Insert suspend fun insert(row: IntervalRow)
    @Query("SELECT * FROM intervals WHERE endMs > :from AND startMs < :to ORDER BY startMs") fun observe(from: Long, to: Long): Flow<List<IntervalRow>>
    @Query("SELECT * FROM intervals WHERE endMs > :from AND startMs < :to ORDER BY startMs") suspend fun between(from: Long, to: Long): List<IntervalRow>
    @Query("SELECT * FROM rules ORDER BY id") fun rules(): Flow<List<RuleRow>>
    @Upsert suspend fun rule(row: RuleRow)
    @Query("DELETE FROM rules WHERE id = :id") suspend fun deleteRule(id: String)
    @Query("SELECT * FROM settings WHERE id = 1") fun settings(): Flow<SettingsRow?>
    @Upsert suspend fun settings(row: SettingsRow)
    @Insert suspend fun override(row: OverrideRow)
    @Query("SELECT * FROM overrides WHERE untilMs > :now ORDER BY untilMs DESC LIMIT 1") suspend fun activeOverride(now: Long): OverrideRow?
    @Insert suspend fun gap(row: GapRow)
    @Query("UPDATE gaps SET endMs = :at WHERE endMs IS NULL") suspend fun closeGaps(at: Long)
    @Query("SELECT * FROM gaps ORDER BY startMs DESC LIMIT 50") fun gaps(): Flow<List<GapRow>>
}

@Database(entities = [IntervalRow::class, RuleRow::class, SettingsRow::class, OverrideRow::class, GapRow::class], version = 1, exportSchema = true)
internal abstract class UsageDatabase : RoomDatabase() { abstract fun dao(): UsageDao }

class RoomUsageRepository private constructor(private val db: UsageDatabase) : UsageRepository, java.io.Closeable {
    private val dao get() = db.dao()
    private fun IntervalRow.model() = UsageInterval(Target.valueOf(target), Surface.valueOf(surface), Source.valueOf(source), pkg,
        Instant.ofEpochMilli(startMs), Instant.ofEpochMilli(endMs), bursts, screens, classifier)

    override val settings: Flow<FocusSettings> = dao.settings().map { r ->
        if (r == null) FocusSettings() else FocusSettings(r.tracking, r.strict, Curfew(r.curfewOn, LocalTime.parse(r.curfewStart), LocalTime.parse(r.curfewEnd)))
    }
    override val rules: Flow<List<LimitRule>> = dao.rules().map { rows ->
        rows.map { LimitRule(it.id, it.target?.let(Target::valueOf), it.surface?.let(Surface::valueOf), it.minutes, it.enabled) }
    }
    override val gaps: Flow<List<CoverageGap>> = dao.gaps().map { rows ->
        rows.map { CoverageGap(Instant.ofEpochMilli(it.startMs), it.endMs?.let(Instant::ofEpochMilli), GapReason.valueOf(it.reason)) }
    }
    override fun day(date: LocalDate, zone: ZoneId): Flow<UsageDay> = days(date, date, zone).map { it.single() }
    override fun days(from: LocalDate, to: LocalDate, zone: ZoneId): Flow<List<UsageDay>> {
        val start = from.atStartOfDay(zone).toInstant().toEpochMilli(); val end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return dao.observe(start, end).map { rows ->
            val list = rows.map { it.model() }
            generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.map { d -> UsageDay(d, list.filter { it.start.atZone(zone).toLocalDate() == d }) }.toList()
        }
    }
    override suspend fun intervalsBetween(start: Instant, end: Instant) = dao.between(start.toEpochMilli(), end.toEpochMilli()).map { it.model() }
    override suspend fun add(interval: UsageInterval) = dao.insert(IntervalRow(0, interval.target.name, interval.surface.name, interval.source.name, interval.pkg,
        interval.start.toEpochMilli(), interval.end.toEpochMilli(), interval.scrollBursts, interval.screens, interval.classifierVersion))
    override suspend fun saveSettings(settings: FocusSettings) = dao.settings(SettingsRow(1, settings.tracking, settings.strict, settings.curfew.enabled,
        settings.curfew.start.toString(), settings.curfew.end.toString()))
    override suspend fun saveRule(rule: LimitRule) = dao.rule(RuleRow(rule.id, rule.target?.name, rule.surface?.name, rule.dailyMinutes, rule.enabled))
    override suspend fun deleteRule(id: String) = dao.deleteRule(id)
    override suspend fun addOverride(o: Override) = dao.override(OverrideRow(0, o.until.toEpochMilli(), o.createdAt.toEpochMilli(), o.reason.take(200)))
    override suspend fun activeOverride(now: Instant) = dao.activeOverride(now.toEpochMilli())?.let { Override(Instant.ofEpochMilli(it.untilMs), Instant.ofEpochMilli(it.createdMs), it.reason) }
    override suspend fun openGap(at: Instant, reason: GapReason) = db.withTransaction { dao.closeGaps(at.toEpochMilli()); dao.gap(GapRow(0, at.toEpochMilli(), null, reason.name)) }
    override suspend fun closeGap(at: Instant) = dao.closeGaps(at.toEpochMilli())
    override fun close() = db.close()

    companion object {
        fun open(context: Context, name: String = "usage.db") = RoomUsageRepository(Room.databaseBuilder(context, UsageDatabase::class.java, name).build())
    }
}
