package dev.personalhub.vtop

import dev.personalhub.college.api.*
import java.time.DayOfWeek
import java.time.LocalTime

/*
 * Pure parsers over bounded portal table structures produced by the in-page script.
 * Columns are located by header labels, never by fixed positions, and unknown values stay null.
 * Raw text is never logged; diagnostics carry counts and matched-label booleans only.
 */
data class PortalCell(val text: String, val colspan: Int = 1)
data class PortalTable(val id: String, val rows: List<List<PortalCell>>, val context: List<String> = emptyList()) {
    fun texts(row: Int) = rows.getOrNull(row)?.map { it.text }.orEmpty()
}
data class Parsed<T>(val rows: List<T>, val status: DatasetStatus)

private val CODE = Regex("""\b([A-Z]{3,5}\d{3,4}[A-Z]?)\b""")
private val SPACES = Regex("""\s+""")
private fun norm(s: String) = s.lowercase().replace(SPACES, " ").trim()
private fun num(s: String?): Double? = s?.replace(",", "")?.let { Regex("""-?\d+(\.\d+)?""").find(it)?.value?.toDoubleOrNull() }
private fun int(s: String?): Int? = num(s)?.toInt()
private fun blank(s: String?) = s?.trim()?.takeUnless { it.isEmpty() || it == "-" || it.equals("NIL", true) }

/** Locates the header row (within the first rows) that contains every required label fragment. */
internal class Header(val row: Int, private val labels: List<String>) {
    fun col(vararg fragments: String): Int? = fragments.firstNotNullOfOrNull { f -> labels.indexOfFirst { it.contains(f) }.takeIf { it >= 0 } }
    companion object {
        fun find(table: PortalTable, vararg required: String): Header? = (0 until minOf(4, table.rows.size)).firstNotNullOfOrNull { r ->
            val labels = expand(table.rows[r]).map(::norm)
            if (required.all { req -> labels.any { it.contains(req) } }) Header(r, labels) else null
        }
        fun expand(row: List<PortalCell>): List<String> = row.flatMap { c -> List(c.colspan.coerceIn(1, 20)) { c.text } }
    }
}

object VtopParser {
    fun semesters(options: List<Pair<String, String>>): List<Semester> =
        options.filter { it.first.isNotBlank() && !norm(it.second).startsWith("choose") && !norm(it.second).startsWith("select") }
            .map { Semester(it.first.trim(), it.second.replace(SPACES, " ").trim()) }.distinctBy { it.id }

    fun courses(tables: List<PortalTable>): Parsed<Course> {
        for (t in tables) {
            val h = Header.find(t, "course", "slot") ?: continue
            val courseCol = h.col("course title", "course") ?: continue
            val codeCol = h.col("course code")
            val ltpjc = h.col("l t p j c", "ltpjc", "credit")
            val slotCol = h.col("slot - venue", "slot/venue", "slot") ?: continue
            val venueCol = h.col("venue").takeIf { it != slotCol }
            val faculty = h.col("faculty")
            val classId = h.col("class nbr", "class id", "class number")
            val typeCol = h.col("course type")
            val out = t.rows.drop(h.row + 1).mapNotNull { r ->
                val cells = Header.expand(r)
                val courseText = cells.getOrNull(courseCol) ?: return@mapNotNull null
                val code = (codeCol?.let { cells.getOrNull(it) } ?: courseText).let { CODE.find(it)?.value } ?: return@mapNotNull null
                val afterCode = courseText.substringAfter(code).trimStart(' ', '-', '–', ':').trim()
                val type = typeCol?.let { blank(cells.getOrNull(it)) } ?: Regex("""\(([^()]+)\)\s*$""").find(afterCode)?.groupValues?.get(1)?.trim() ?: ""
                val title = afterCode.replace(Regex("""\([^()]*\)\s*$"""), "").trim().trimEnd('-', ' ').ifEmpty { code }
                val slotVenue = cells.getOrNull(slotCol).orEmpty()
                val (slot, venue) = if (venueCol != null) blank(slotVenue) to blank(cells.getOrNull(venueCol)) else splitSlotVenue(slotVenue)
                val credits = ltpjc?.let { cells.getOrNull(it) }?.let { s -> Regex("""\d+(\.\d+)?""").findAll(s).lastOrNull()?.value?.toDoubleOrNull() }
                Course(code, title, type, credits, slot, venue, faculty?.let { blank(cells.getOrNull(it))?.substringBefore(" - ")?.trim() }, classId?.let { blank(cells.getOrNull(it)) })
            }.distinctBy { it.code + "|" + it.type + "|" + it.classId }
            if (out.isNotEmpty()) return Parsed(out, DatasetStatus(Dataset.TIMETABLE, ImportState.OK, out.size, "${out.size} registered courses"))
        }
        return Parsed(emptyList(), DatasetStatus(Dataset.TIMETABLE, ImportState.EMPTY, 0, "No course list table recognised"))
    }

    private fun splitSlotVenue(s: String): Pair<String?, String?> {
        val parts = s.split(" - ", limit = 2).map { it.trim() }
        return blank(parts.getOrNull(0)) to blank(parts.getOrNull(1))
    }

    private val DAYS = mapOf("MON" to DayOfWeek.MONDAY, "TUE" to DayOfWeek.TUESDAY, "WED" to DayOfWeek.WEDNESDAY, "THU" to DayOfWeek.THURSDAY,
        "FRI" to DayOfWeek.FRIDAY, "SAT" to DayOfWeek.SATURDAY, "SUN" to DayOfWeek.SUNDAY)
    private val LABELS = setOf("THEORY", "LAB", "START", "END", "HOURS", "DAY", "LUNCH")
    private val TIME = Regex("""(\d{1,2}):(\d{2})\s*([AaPp][Mm])?""")
    private val ENTRY = Regex("""^([A-Z0-9]+(?:\+[A-Z0-9]+)*)-([A-Z]{3,5}\d{3,4}[A-Z]?)-([A-Z]{2,4})-(.+?)(?:-([A-Z]{2,4}|ALL))?$""")

    /**
     * Timetable grid: header rows give THEORY/LAB start and end times per column; each day has THEORY and LAB rows
     * whose occupied cells read SLOT-CODE-TYPE-VENUE[-GROUP]. Free cells contain only a slot name.
     */
    fun meetings(tables: List<PortalTable>): Parsed<ClassMeeting> {
        for (t in tables) {
            val starts = mutableMapOf<String, List<LocalTime?>>(); val ends = mutableMapOf<String, List<LocalTime?>>()
            var kind = "THEORY"; var day: DayOfWeek? = null
            val out = mutableListOf<ClassMeeting>()
            for (row in t.rows) {
                val cells = Header.expand(row).map { it.trim() }
                if (cells.isEmpty()) continue
                val upper = cells.map { it.uppercase() }
                // Leading label cells: optional day, THEORY/LAB, Start/End.
                var i = 0
                DAYS[upper[0].take(3)]?.let { if (upper[0].length <= 9) { day = it; i = 1 } }
                if (upper.getOrNull(i) == "THEORY" || upper.getOrNull(i) == "LAB") { kind = upper[i]; i++ }
                val marker = upper.getOrNull(i)
                if (marker == "START" || marker == "END") {
                    val times = cells.drop(i + 1).map(::time)
                    if (marker == "START") starts[kind] = times else ends[kind] = times
                    continue
                }
                val today = day ?: continue
                if (i == 0) continue
                cells.drop(i).forEachIndexed { col, text ->
                    val m = ENTRY.find(text.replace(SPACES, "")) ?: return@forEachIndexed
                    val start = starts[kind]?.getOrNull(col) ?: starts["THEORY"]?.getOrNull(col)
                    val end = ends[kind]?.getOrNull(col) ?: ends["THEORY"]?.getOrNull(col)
                    if (start != null && end != null && end > start) {
                        val venue = m.groupValues[4].takeUnless { it.equals("NIL", true) }
                        out += ClassMeeting(m.groupValues[2], m.groupValues[3], today, start, end, m.groupValues[1], venue)
                    }
                }
            }
            if (starts.isNotEmpty() && out.isNotEmpty()) return Parsed(merge(out), DatasetStatus(Dataset.TIMETABLE, ImportState.OK, out.size, "${out.size} weekly class slots"))
        }
        return Parsed(emptyList(), DatasetStatus(Dataset.TIMETABLE, ImportState.EMPTY, 0, "No timetable grid recognised"))
    }

    /** Adjacent lab periods of the same course become one meeting. */
    private fun merge(items: List<ClassMeeting>): List<ClassMeeting> = items.groupBy { Triple(it.day, it.courseCode, it.component) }.flatMap { (_, list) ->
        list.sortedBy { it.start }.fold(mutableListOf<ClassMeeting>()) { acc, m ->
            val last = acc.lastOrNull()
            if (last != null && !m.start.isAfter(last.end.plusMinutes(1)) && m.venue == last.venue) acc[acc.lastIndex] = last.copy(end = maxOf(last.end, m.end), slot = last.slot + "+" + m.slot) else acc += m
            acc
        }
    }.sortedWith(compareBy({ it.day }, { it.start }))

    internal fun time(s: String): LocalTime? {
        val m = TIME.find(s) ?: return null
        var h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
        val ap = m.groupValues[3].uppercase()
        if (ap == "PM" && h < 12) h += 12
        if (ap == "AM" && h == 12) h = 0
        // VTOP grids often print afternoon times on a 12-hour clock without a suffix (e.g. 01:00 after 12:50).
        if (ap.isEmpty() && h in 1..7) h += 12
        return if (h in 0..23 && min in 0..59) LocalTime.of(h, min) else null
    }

    fun attendance(tables: List<PortalTable>): Parsed<AttendanceRecord> {
        for (t in tables) {
            val h = Header.find(t, "attended", "total") ?: continue
            val attendedCol = h.col("attended") ?: continue
            val totalCol = h.col("total class", "total") ?: continue
            val codeCol = h.col("course code", "course detail", "course")
            val titleCol = h.col("course title", "course name")
            val typeCol = h.col("course type", "type")
            val slotCol = h.col("slot", "class detail")
            val pctCol = h.col("percentage", "attendance %", "%")
            val out = t.rows.drop(h.row + 1).mapNotNull { r ->
                val c = Header.expand(r)
                val codeText = codeCol?.let { c.getOrNull(it) } ?: c.joinToString(" ")
                val code = CODE.find(codeText)?.value ?: return@mapNotNull null
                val attended = int(c.getOrNull(attendedCol)) ?: return@mapNotNull null
                val total = int(c.getOrNull(totalCol)) ?: return@mapNotNull null
                if (attended > total || total < 0) return@mapNotNull null
                val type = typeCol?.let { blank(c.getOrNull(it)) } ?: Regex("""\(([^()]+)\)""").find(codeText)?.groupValues?.get(1) ?: ""
                val title = titleCol?.let { blank(c.getOrNull(it)) } ?: codeText.substringAfter(code).trimStart(' ', '-', '–').replace(Regex("""\([^()]*\)"""), "").trim().ifEmpty { null }
                AttendanceRecord(code, title, type, slotCol?.let { blank(c.getOrNull(it)) }, attended, total, pctCol?.let { int(c.getOrNull(it)) })
            }
            if (out.isNotEmpty() || t.rows.size <= h.row + 1) return Parsed(out, DatasetStatus(Dataset.ATTENDANCE, if (out.isEmpty()) ImportState.EMPTY else ImportState.OK, out.size, "${out.size} course components"))
        }
        return Parsed(emptyList(), DatasetStatus(Dataset.ATTENDANCE, ImportState.EMPTY, 0, "No attendance table recognised"))
    }

    fun marks(tables: List<PortalTable>): Parsed<MarkComponent> {
        val out = mutableListOf<MarkComponent>()
        for (t in tables) {
            val h = Header.find(t, "mark title") ?: continue
            val titleCol = h.col("mark title") ?: continue
            val maxCol = h.col("max"); val weightCol = h.col("weightage %", "weightage"); val scoredCol = h.col("scored")
            val weightedCol = h.col("weightage mark", "weighted"); val statusCol = h.col("status")
            val ctx = t.context.joinToString(" ")
            val code = CODE.find(ctx)?.value ?: continue
            val type = t.context.firstOrNull { componentOf(it) in setOf("THEORY", "LAB", "PROJECT") && it.length < 30 }
            t.rows.drop(h.row + 1).forEach { r ->
                val c = Header.expand(r)
                val title = blank(c.getOrNull(titleCol)) ?: return@forEach
                out += MarkComponent(code, type, title, num(maxCol?.let { c.getOrNull(it) }), num(weightCol?.let { c.getOrNull(it) }),
                    num(scoredCol?.let { c.getOrNull(it) }), num(weightedCol?.let { c.getOrNull(it) }), statusCol?.let { blank(c.getOrNull(it)) })
            }
        }
        return Parsed(out, DatasetStatus(Dataset.MARKS, if (out.isEmpty()) ImportState.EMPTY else ImportState.OK, out.size, "${out.size} assessment rows"))
    }

    private val DATE_FORMATS = listOf("d-MMM-yyyy", "dd-MMM-yyyy", "d/M/yyyy", "dd/MM/yyyy", "d MMM yyyy", "yyyy-MM-dd")
    internal fun date(s: String?): java.time.LocalDate? {
        val text = s?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val norm = text.lowercase().replaceFirstChar { it }.let { t -> Regex("""[a-z]{3}""").replace(t) { m -> m.value.replaceFirstChar(Char::uppercase) } }
        return DATE_FORMATS.firstNotNullOfOrNull { f ->
            runCatching { java.time.LocalDate.parse(norm, java.time.format.DateTimeFormatter.ofPattern(f, java.util.Locale.ENGLISH)) }.getOrNull()
        }
    }

    /** Exam schedule: header-mapped rows; full-width rows (all cells equal) name the exam type (e.g. CAT1, FAT). */
    fun exams(tables: List<PortalTable>): Parsed<ExamSlot> {
        for (t in tables) {
            val h = Header.find(t, "course", "exam date") ?: Header.find(t, "course", "date") ?: continue
            val code = h.col("course code", "course") ?: continue
            val title = h.col("course title"); val type = h.col("course type"); val dateCol = h.col("exam date", "date")
            val session = h.col("session"); val reporting = h.col("reporting"); val time = h.col("exam time")
            val venue = h.col("venue"); val seatLoc = h.col("seat location"); val seatNo = h.col("seat no", "seat number")
            var examType = ""
            val out = mutableListOf<ExamSlot>()
            for (r in t.rows.drop(h.row + 1)) {
                val c = Header.expand(r).map { it.trim() }
                val distinct = c.filter { it.isNotEmpty() }.distinct()
                if (distinct.size == 1 && CODE.find(distinct[0]) == null) { examType = distinct[0]; continue }
                val cc = CODE.find(c.getOrNull(code).orEmpty())?.value ?: continue
                out += ExamSlot(examType, cc, title?.let { blank(c.getOrNull(it)) }, type?.let { blank(c.getOrNull(it)) }, date(dateCol?.let { c.getOrNull(it) }),
                    session?.let { blank(c.getOrNull(it)) }, reporting?.let { blank(c.getOrNull(it)) }, time?.let { blank(c.getOrNull(it)) },
                    venue?.let { blank(c.getOrNull(it)) }, seatLoc?.let { blank(c.getOrNull(it)) }, seatNo?.let { blank(c.getOrNull(it)) })
            }
            if (out.isNotEmpty() || t.rows.size <= h.row + 1) return Parsed(out, DatasetStatus(Dataset.EXAMS, if (out.isEmpty()) ImportState.EMPTY else ImportState.OK, out.size, "${out.size} exams"))
        }
        return Parsed(emptyList(), DatasetStatus(Dataset.EXAMS, ImportState.EMPTY, 0, "No exam schedule published"))
    }

    /** Academic calendar month grids; context carries the month (e.g. 01-NOV-2026). Cells: "<day> <label…>". */
    fun calendar(tables: List<PortalTable>): Parsed<CalendarDay> {
        val out = mutableListOf<CalendarDay>()
        for (t in tables) {
            val month = date(t.context.firstOrNull()) ?: continue
            for (row in t.rows) for (cell in row) {
                val m = Regex("""^\s*(\d{1,2})\b\s*(.*)$""").find(cell.text) ?: continue
                val day = m.groupValues[1].toInt()
                val label = m.groupValues[2].trim().trim('-', ':', ' ')
                if (label.isEmpty() || day !in 1..month.lengthOfMonth()) continue
                out += CalendarDay(month.withDayOfMonth(day), label.take(120), calendarKind(label))
            }
        }
        val dedup = out.distinctBy { it.date to it.label }.sortedBy { it.date }
        return Parsed(dedup, DatasetStatus(Dataset.CALENDAR, if (dedup.isEmpty()) ImportState.EMPTY else ImportState.OK, dedup.size,
            "${dedup.size} labelled days · ${dedup.count { it.kind == CalendarKind.HOLIDAY }} holidays"))
    }

    fun grades(tables: List<PortalTable>): Pair<Parsed<GradeRecord>, CgpaSummary?> {
        val out = mutableListOf<GradeRecord>(); var summary: CgpaSummary? = null
        // Nested per-course attempt tables (with a context row) repeat rows of the main history table.
        val hasTopLevelHistory = tables.any { it.context.isEmpty() && Header.find(it, "course code", "grade") != null }
        for (t in tables) {
            if (hasTopLevelHistory && t.context.isNotEmpty()) continue
            Header.find(t, "cgpa")?.let { h ->
                val row = t.rows.drop(h.row + 1).map(Header::expand).firstOrNull { r -> num(h.col("cgpa")?.let { r.getOrNull(it) }) != null }
                if (row != null && summary == null) summary = CgpaSummary(num(h.col("credits registered", "registered")?.let { row.getOrNull(it) }),
                    num(h.col("credits earned", "earned")?.let { row.getOrNull(it) }), num(h.col("cgpa")?.let { row.getOrNull(it) })?.takeIf { it in 0.0..10.0 })
            }
            val h = Header.find(t, "course code", "grade") ?: continue
            val code = h.col("course code") ?: continue
            val grade = h.col("grade") ?: continue
            val title = h.col("course title"); val type = h.col("course type"); val credits = h.col("credit"); val month = h.col("exam month", "month")
            t.rows.drop(h.row + 1).forEach { r ->
                val c = Header.expand(r)
                val cc = CODE.find(c.getOrNull(code).orEmpty())?.value ?: return@forEach
                val g = blank(c.getOrNull(grade))?.uppercase() ?: return@forEach
                if (g.length > 3) return@forEach
                out += GradeRecord(cc, title?.let { blank(c.getOrNull(it)) } ?: cc, type?.let { blank(c.getOrNull(it)) }, num(credits?.let { c.getOrNull(it) }), g, month?.let { blank(c.getOrNull(it)) })
            }
        }
        val status = DatasetStatus(Dataset.GRADES, if (out.isEmpty() && summary == null) ImportState.EMPTY else ImportState.OK, out.size,
            "${out.size} graded attempts${if (summary?.cgpa != null) " · CGPA found" else ""}")
        return Parsed(out, status) to summary
    }
}

/**
 * Structure-only description of a sync payload for debugging parser mismatches: table sizes and which known
 * header labels appeared. Contains no cell values, names, numbers or identifiers.
 */
object VtopDiagnostics {
    @Volatile var last: String? = null
        internal set
    private val LABELS = listOf("course", "slot", "venue", "credit", "l t p j c", "attended", "total", "percentage", "mark title", "scored",
        "weightage", "grade", "cgpa", "earned", "theory", "lab", "start", "end", "mon", "faculty", "exam", "date", "session", "reporting", "seat", "time")
    fun describe(name: String, tables: List<PortalTable>?): String {
        if (tables == null) return "$name: none"
        val shapes = tables.mapIndexed { i, t ->
            val head = t.rows.take(3).flatten().map { it.text.lowercase() }
            val hits = LABELS.filter { l -> head.any { it == l || it.startsWith("$l ") || it.endsWith(" $l") || it.contains(" $l ") } }
            "t$i(${t.rows.size}x${t.rows.maxOfOrNull { it.size } ?: 0}${if (t.context.isNotEmpty()) ",ctx" else ""})[${hits.joinToString("/")}]"
        }
        return "$name: ${tables.size} tables ${shapes.joinToString(" ")}"
    }
}
