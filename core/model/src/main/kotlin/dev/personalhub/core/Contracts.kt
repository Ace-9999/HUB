package dev.personalhub.core

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

// Narrow shared status, route and metric contracts; no domain persistence.
enum class DataStatus { LOADING, EMPTY, CONTENT, PARTIAL, STALE, ERROR, UNAVAILABLE }
data class Section(val id: String, val title: String)
class SectionRegistry(val sections: List<Section>) {
    init { require(sections.isNotEmpty()); require(sections.map { it.id }.distinct().size == sections.size) }
    fun resolve(id: String): Section = sections.firstOrNull { it.id == id } ?: sections.first()
}
enum class Completeness { COMPLETE, PARTIAL, UNKNOWN }
data class DateRange(val start: LocalDate, val endInclusive: LocalDate, val zone: ZoneId) {
    init { require(!endInclusive.isBefore(start)) }
    operator fun contains(date: LocalDate) = date >= start && date <= endInclusive
}
data class MetricPoint(val day: LocalDate, val value: Double?, val completeness: Completeness)
data class MetricSeries(val metricId: String, val unit: String, val points: List<MetricPoint>, val methodVersion: Int = 1)
interface MetricSeriesProvider { fun series(metricId: String, range: DateRange): MetricSeries }
interface IdSource { fun next(): String }
object UuidSource : IdSource { override fun next() = java.util.UUID.randomUUID().toString() }
data class AppTime(val clock: Clock = Clock.systemUTC(), val zone: ZoneId = ZoneId.of("Asia/Kolkata")) {
    fun today(): LocalDate = clock.instant().atZone(zone).toLocalDate()
}
