package dev.personalhub.app.backup

import dev.personalhub.college.api.*
import dev.personalhub.focus.api.*
import dev.personalhub.training.api.*
import dev.personalhub.usage.api.*
import dev.personalhub.usage.api.Target
import org.json.JSONArray
import org.json.JSONObject
import java.time.*

/*
 * Plain JSON full backup (I13, data_export.md §3). Versioned envelope; stable ids, timestamps, zones and units
 * preserved; enums serialised by name/code; never passwords, cookies, tokens or OS grants.
 */
const val BACKUP_FORMAT = "personalhub-json"
const val BACKUP_SCHEMA = 1
const val MAX_BACKUP_BYTES = 64L * 1024 * 1024
private const val MAX_RECORDS = 500_000

data class AppSettingsBackup(val theme: String, val focusLock: Boolean, val taskReminders: Boolean, val habitNudge: Boolean, val habitNudgeTime: String)

data class FullBackup(val createdAt: Instant, val zone: ZoneId, val focus: FocusBackup, val training: TrainingBackup, val usage: UsageBackup,
    val college: CollegeSnapshot, val settings: AppSettingsBackup) {
    val counts: Map<String, Int> get() = linkedMapOf(
        "Tasks" to focus.tasks.size, "Habits" to focus.habits.size, "Habit entries" to focus.entries.size, "Mood entries" to focus.moods.size,
        "Routines" to training.routines.size, "Workouts" to training.workouts.size, "Custom exercises" to training.customExercises.size,
        "Usage intervals" to usage.intervals.size, "Limits" to usage.rules.size, "Courses" to college.courses.size, "Graded semesters" to college.results.size)
}

class BackupFormatException(message: String) : Exception(message)

object BackupCodec {
    // ------------------------------------------------------------ encode
    fun encode(b: FullBackup): JSONObject = JSONObject()
        .put("format", BACKUP_FORMAT).put("schemaVersion", BACKUP_SCHEMA).put("exportKind", "full_backup").put("restorable", true)
        .put("createdAt", b.createdAt.toString()).put("reportingZone", b.zone.id)
        .put("moduleVersions", JSONObject().put("college", 1).put("training", 1).put("focus", 1).put("usage", 1))
        .put("selection", JSONObject().put("modules", JSONArray(listOf("college", "training", "focus", "usage", "settings"))).put("allRetainedRecords", true)
            .put("includeSensitive", true).put("includeMedia", false))
        .put("coverage", JSONObject().put("gaps", JSONArray(b.usage.gaps.map { g -> JSONObject().put("start", g.start.toString()).put("end", g.end?.toString() ?: JSONObject.NULL).put("reason", g.reason.name) }))
            .put("omissions", JSONArray(listOf("exercise photos are bundled with the app, not copied into backups", "no credentials or OS permissions"))))
        .put("data", JSONObject().put("focus", focus(b.focus)).put("training", training(b.training)).put("usage", usage(b.usage))
            .put("college", college(b.college)).put("settings", JSONObject().put("theme", b.settings.theme).put("focusLock", b.settings.focusLock)
                .put("taskReminders", b.settings.taskReminders).put("habitNudge", b.settings.habitNudge).put("habitNudgeTime", b.settings.habitNudgeTime)))
        .put("assets", JSONArray())

    private fun <T> arr(items: List<T>, f: (T) -> JSONObject) = JSONArray().apply { items.forEach { put(f(it)) } }
    private fun JSONObject.opt(k: String, v: Any?) = put(k, v ?: JSONObject.NULL)

    private fun focus(f: FocusBackup) = JSONObject()
        .put("tasks", arr(f.tasks) { t -> JSONObject().put("id", t.id).put("title", t.title).put("firstDay", t.firstDay.toString()).put("priority", t.priority.name)
            .opt("dueTime", t.dueTime?.toString()).put("recurrence", t.recurrence.name).put("carryForward", t.carryForward).put("undated", t.undated)
            .put("createdAt", t.createdAt.toString()).put("updatedAt", t.updatedAt.toString())
            .opt("googleId", f.googleLinks[t.id]?.first).opt("googleList", f.googleLinks[t.id]?.second) })
        .put("occurrences", arr(f.occurrences) { JSONObject().put("taskId", it.taskId).put("day", it.day.toString()).put("completed", it.completed) })
        .put("habits", arr(f.habits) { h -> JSONObject().put("id", h.id).put("label", h.label).put("unit", h.unit.name).put("direction", h.direction.name)
            .put("target", h.target).put("sensitive", h.sensitive).put("createdAt", h.createdAt.toString()).put("updatedAt", h.updatedAt.toString()) })
        .put("habitEntries", arr(f.entries) { e -> JSONObject().put("habitId", e.habitId).put("day", e.day.toString()).put("value", e.value)
            .put("explicitZero", e.explicitZero).opt("note", e.note).put("updatedAt", e.updatedAt.toString()) })
        .put("moods", arr(f.moods) { m -> JSONObject().put("id", m.id).put("moodCode", m.mood.code).put("occurredAt", m.occurredAt.toString())
            .put("originalZone", m.originalZone.id).put("createdAt", m.createdAt.toString()).put("updatedAt", m.updatedAt.toString()).opt("note", m.note).put("revision", m.revision) })

    private fun set(s: WorkoutSet) = JSONObject().put("type", s.type.name).opt("weightKg", s.weightKg).opt("reps", s.reps).opt("seconds", s.seconds)
        .opt("distanceM", s.distanceM).opt("rpe", s.rpe).put("done", s.done)
    private fun training(t: TrainingBackup) = JSONObject()
        .put("customExercises", arr(t.customExercises) { e -> JSONObject().put("id", e.id).put("name", e.name).put("aliases", JSONArray(e.aliases))
            .put("equipment", e.equipment.name).put("metric", e.metric.name).put("category", e.category.name).put("primary", JSONArray(e.primary.map { it.name }))
            .put("secondary", JSONArray(e.secondary.map { it.name })).put("instructions", JSONArray(e.instructions)).put("archived", e.archived) })
        .put("routines", arr(t.routines) { r -> JSONObject().put("id", r.id).put("name", r.name).opt("notes", r.notes).put("archived", r.archived).put("updatedAt", r.updatedAt.toString())
            .put("items", arr(r.items) { i -> JSONObject().put("exerciseId", i.exerciseId).put("sets", i.sets).opt("reps", i.reps).opt("weightKg", i.weightKg).put("restSeconds", i.restSeconds).opt("note", i.note) }) })
        .put("workouts", arr(t.workouts) { w -> JSONObject().put("id", w.id).put("name", w.name).opt("routineId", w.routineId).put("startedAt", w.startedAt.toString())
            .opt("finishedAt", w.finishedAt?.toString()).opt("notes", w.notes)
            .put("exercises", arr(w.exercises) { e -> JSONObject().put("exerciseId", e.exerciseId).put("restSeconds", e.restSeconds).opt("note", e.note).put("sets", arr(e.sets, ::set)) }) })

    private fun usage(u: UsageBackup) = JSONObject()
        .put("settings", JSONObject().put("tracking", u.settings.tracking).put("strict", u.settings.strict).put("perApp", u.settings.perApp)
            .put("curfew", JSONObject().put("enabled", u.settings.curfew.enabled).put("start", u.settings.curfew.start.toString()).put("end", u.settings.curfew.end.toString())))
        .put("rules", arr(u.rules) { r -> JSONObject().put("id", r.id).opt("target", r.target?.name).opt("surface", r.surface?.name).put("dailyMinutes", r.dailyMinutes).put("enabled", r.enabled) })
        .put("intervals", arr(u.intervals) { i -> JSONObject().put("target", i.target.name).put("surface", i.surface.name).put("source", i.source.name).put("pkg", i.pkg)
            .put("start", i.start.toString()).put("end", i.end.toString()).put("scrollBursts", i.scrollBursts).opt("screens", i.screens).put("classifierVersion", i.classifierVersion) })
        .put("overrides", arr(u.overrides) { o -> JSONObject().put("until", o.until.toString()).put("createdAt", o.createdAt.toString()).put("reason", o.reason) })

    private fun college(c: CollegeSnapshot) = JSONObject()
        .opt("semester", c.semester?.let { JSONObject().put("id", it.id).put("name", it.name) })
        .put("semesters", arr(c.semesters) { JSONObject().put("id", it.id).put("name", it.name) }).opt("syncedAt", c.syncedAt?.toString())
        .put("courses", arr(c.courses) { x -> JSONObject().put("code", x.code).put("title", x.title).put("type", x.type).opt("credits", x.credits).opt("slot", x.slot)
            .opt("venue", x.venue).opt("faculty", x.faculty).opt("classId", x.classId) })
        .put("meetings", arr(c.meetings) { m -> JSONObject().put("courseCode", m.courseCode).put("component", m.component).put("day", m.day.name).put("start", m.start.toString())
            .put("end", m.end.toString()).put("slot", m.slot).opt("venue", m.venue) })
        .put("attendance", arr(c.attendance) { a -> JSONObject().put("courseCode", a.courseCode).opt("courseTitle", a.courseTitle).put("type", a.type).opt("slot", a.slot)
            .put("attended", a.attended).put("total", a.total).opt("portalPercent", a.portalPercent) })
        .put("marks", arr(c.marks, ::mark))
        .put("grades", arr(c.grades) { g -> JSONObject().put("courseCode", g.courseCode).put("title", g.title).opt("type", g.type).opt("credits", g.credits).put("grade", g.grade).opt("examMonth", g.examMonth) })
        .opt("summary", c.summary?.let { s -> JSONObject().opt("creditsRegistered", s.creditsRegistered).opt("creditsEarned", s.creditsEarned).opt("cgpa", s.cgpa) })
        .put("exams", arr(c.exams) { e -> JSONObject().put("examType", e.examType).put("courseCode", e.courseCode).opt("courseTitle", e.courseTitle).opt("courseType", e.courseType)
            .opt("date", e.date?.toString()).opt("session", e.session).opt("reportingTime", e.reportingTime).opt("examTime", e.examTime).opt("venue", e.venue)
            .opt("seatLocation", e.seatLocation).opt("seatNumber", e.seatNumber) })
        .put("calendar", arr(c.calendar) { d -> JSONObject().put("date", d.date.toString()).put("label", d.label).put("kind", d.kind.name) })
        .put("results", arr(c.results) { r -> JSONObject().put("semester", JSONObject().put("id", r.semester.id).put("name", r.semester.name))
            .put("courses", arr(r.courses) { g -> JSONObject().put("code", g.code).put("title", g.title).opt("type", g.type).opt("credits", g.credits).opt("gradingType", g.gradingType)
                .opt("grandTotal", g.grandTotal).opt("grade", g.grade).put("components", arr(g.components, ::mark))
                .put("ranges", arr(g.ranges) { x -> JSONObject().put("grade", x.grade).put("range", x.range) }) }) })
    private fun mark(m: MarkComponent) = JSONObject().put("courseCode", m.courseCode).opt("courseType", m.courseType).put("title", m.title).opt("maxMark", m.maxMark)
        .opt("weightage", m.weightage).opt("scored", m.scored).opt("weighted", m.weighted).opt("status", m.status)

    // ------------------------------------------------------------ decode (strict)
    private var budget = 0
    private fun objs(a: JSONArray?): List<JSONObject> {
        val n = a?.length() ?: 0
        budget += n; if (budget > MAX_RECORDS) throw BackupFormatException("Backup has too many records.")
        return (0 until n).map { a!!.getJSONObject(it) }
    }
    private fun strs(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.getString(it) }
    private fun JSONObject.s(k: String): String? = if (isNull(k)) null else optString(k).takeIf { has(k) }
    private fun JSONObject.d(k: String): Double? = if (!has(k) || isNull(k)) null else getDouble(k).also { require(it.isFinite()) }
    private fun JSONObject.i(k: String): Int? = if (!has(k) || isNull(k)) null else getInt(k)
    private inline fun <reified E : Enum<E>> en(v: String): E = runCatching { enumValueOf<E>(v) }.getOrElse { throw BackupFormatException("Unknown value \"$v\".") }
    private fun inst(v: String) = runCatching { Instant.parse(v) }.getOrElse { throw BackupFormatException("Invalid timestamp.") }
    private fun <T> unique(items: List<T>, what: String, key: (T) -> Any) { if (items.map(key).toSet().size != items.size) throw BackupFormatException("Duplicate $what ids.") }

    fun decode(text: String): FullBackup {
        if (text.length > MAX_BACKUP_BYTES) throw BackupFormatException("Backup file is too large.")
        budget = 0
        val root = runCatching { JSONObject(text) }.getOrElse { throw BackupFormatException("Not a JSON backup file.") }
        if (root.optString("format") != BACKUP_FORMAT) throw BackupFormatException("Not a PersonalHub backup.")
        if (root.optInt("schemaVersion") != BACKUP_SCHEMA) throw BackupFormatException("Unsupported backup version ${root.optInt("schemaVersion")}.")
        if (root.optString("exportKind") != "full_backup" || !root.optBoolean("restorable")) throw BackupFormatException("This file is a review export and can't be restored.")
        return try {
            val data = root.getJSONObject("data")
            FullBackup(inst(root.getString("createdAt")), ZoneId.of(root.getString("reportingZone")),
                focus(data.getJSONObject("focus")), training(data.getJSONObject("training")), usage(data.getJSONObject("usage")),
                college(data.getJSONObject("college")), data.getJSONObject("settings").let { s ->
                    AppSettingsBackup(s.optString("theme", "light").takeIf { it in setOf("light", "dark", "system") } ?: "light", s.optBoolean("focusLock"),
                        s.optBoolean("taskReminders"), s.optBoolean("habitNudge"), s.optString("habitNudgeTime", "21:00").also { LocalTime.parse(it) })
                })
        } catch (e: BackupFormatException) { throw e } catch (e: Exception) { throw BackupFormatException("Backup is damaged or incomplete (${e.javaClass.simpleName}).") }
    }

    private fun focus(o: JSONObject): FocusBackup {
        val tasks = objs(o.getJSONArray("tasks")).map { t ->
            Task(t.getString("id"), t.getString("title").also { require(it.isNotBlank() && it.length <= 160) }, LocalDate.parse(t.getString("firstDay")), en(t.getString("priority")),
                t.s("dueTime")?.let(LocalTime::parse), en(t.getString("recurrence")), t.getBoolean("carryForward"), inst(t.getString("createdAt")), inst(t.getString("updatedAt")),
                t.s("googleId") != null, t.optBoolean("undated")) to t.s("googleId")?.let { it to t.s("googleList") }
        }
        unique(tasks, "task") { it.first.id }
        val habits = objs(o.getJSONArray("habits")).map { h ->
            Habit(h.getString("id"), h.getString("label"), en(h.getString("unit")), en(h.getString("direction")), h.getDouble("target"), h.getBoolean("sensitive"),
                inst(h.getString("createdAt")), inst(h.getString("updatedAt")))
        }
        unique(habits, "habit") { it.id }
        val entries = objs(o.getJSONArray("habitEntries")).map { e ->
            HabitEntry(e.getString("habitId"), LocalDate.parse(e.getString("day")), e.getDouble("value"), e.getBoolean("explicitZero"), e.s("note"), inst(e.getString("updatedAt")))
        }.onEach { e -> habits.firstOrNull { it.id == e.habitId }?.let { validateHabitValue(it.unit, e.value) } }
        val moods = objs(o.getJSONArray("moods")).map { m ->
            MoodEntry(m.getString("id"), runCatching { Mood.fromCode(m.getString("moodCode")) }.getOrElse { throw BackupFormatException("Unknown mood code.") },
                inst(m.getString("occurredAt")), ZoneId.of(m.getString("originalZone")), inst(m.getString("createdAt")), inst(m.getString("updatedAt")), m.s("note"), m.getLong("revision"))
        }
        unique(moods, "mood") { it.id }
        return FocusBackup(tasks.map { it.first }, tasks.mapNotNull { (t, l) -> l?.let { t.id to it } }.toMap(),
            objs(o.getJSONArray("occurrences")).map { TaskOccurrence(it.getString("taskId"), LocalDate.parse(it.getString("day")), it.getBoolean("completed")) },
            habits, entries, moods)
    }

    private fun wset(s: JSONObject) = WorkoutSet(en(s.getString("type")), s.d("weightKg"), s.i("reps"), s.i("seconds"), s.d("distanceM"), s.d("rpe"), s.getBoolean("done"))
    private fun training(o: JSONObject): TrainingBackup {
        val ex = objs(o.getJSONArray("customExercises")).map { e ->
            Exercise(e.getString("id").also { require(it.startsWith("custom:")) }, e.getString("name"), strs(e.optJSONArray("aliases")), en(e.getString("equipment")),
                en(e.getString("metric")), en(e.getString("category")), strs(e.getJSONArray("primary")).map { en<Muscle>(it) }.toSet(),
                strs(e.getJSONArray("secondary")).map { en<Muscle>(it) }.toSet(), strs(e.optJSONArray("instructions")), custom = true, archived = e.optBoolean("archived"))
        }
        unique(ex, "exercise") { it.id }
        val routines = objs(o.getJSONArray("routines")).map { r ->
            Routine(r.getString("id"), r.getString("name"), objs(r.getJSONArray("items")).map { i ->
                RoutineItem(i.getString("exerciseId"), i.getInt("sets"), i.i("reps"), i.d("weightKg"), i.getInt("restSeconds"), i.s("note")) },
                r.s("notes"), r.getBoolean("archived"), inst(r.getString("updatedAt")))
        }
        unique(routines, "routine") { it.id }
        val workouts = objs(o.getJSONArray("workouts")).map { w ->
            Workout(w.getString("id"), w.getString("name"), w.s("routineId"), inst(w.getString("startedAt")), w.s("finishedAt")?.let(::inst),
                objs(w.getJSONArray("exercises")).map { e -> WorkoutExercise(e.getString("exerciseId"), objs(e.getJSONArray("sets")).map(::wset), e.getInt("restSeconds"), e.s("note")) }, w.s("notes"))
        }
        unique(workouts, "workout") { it.id }
        if (workouts.count { it.active } > 1) throw BackupFormatException("More than one active workout.")
        return TrainingBackup(ex, routines, workouts)
    }

    private fun usage(o: JSONObject): UsageBackup {
        val s = o.getJSONObject("settings"); val c = s.getJSONObject("curfew")
        return UsageBackup(
            FocusSettings(s.getBoolean("tracking"), s.getBoolean("strict"), Curfew(c.getBoolean("enabled"), LocalTime.parse(c.getString("start")), LocalTime.parse(c.getString("end"))), s.optBoolean("perApp")),
            objs(o.getJSONArray("rules")).map { r -> LimitRule(r.getString("id"), r.s("target")?.let { en<Target>(it) }, r.s("surface")?.let { en<Surface>(it) }, r.getInt("dailyMinutes"), r.getBoolean("enabled")) },
            objs(o.getJSONArray("intervals")).map { i -> UsageInterval(en(i.getString("target")), en(i.getString("surface")), en(i.getString("source")), i.getString("pkg"),
                inst(i.getString("start")), inst(i.getString("end")), i.getInt("scrollBursts"), i.d("screens"), i.optInt("classifierVersion", 1)) },
            objs(o.getJSONArray("overrides")).map { x -> Override(inst(x.getString("until")), inst(x.getString("createdAt")), x.getString("reason")) },
            emptyList())
    }

    private fun mk(m: JSONObject) = MarkComponent(m.getString("courseCode"), m.s("courseType"), m.getString("title"), m.d("maxMark"), m.d("weightage"), m.d("scored"), m.d("weighted"), m.s("status"))
    private fun college(o: JSONObject) = CollegeSnapshot(
        o.optJSONObject("semester")?.let { Semester(it.getString("id"), it.getString("name")) },
        objs(o.getJSONArray("semesters")).map { Semester(it.getString("id"), it.getString("name")) }, o.s("syncedAt")?.let(::inst),
        objs(o.getJSONArray("courses")).map { x -> Course(x.getString("code"), x.getString("title"), x.getString("type"), x.d("credits"), x.s("slot"), x.s("venue"), x.s("faculty"), x.s("classId")) },
        objs(o.getJSONArray("meetings")).map { m -> ClassMeeting(m.getString("courseCode"), m.getString("component"), en(m.getString("day")), LocalTime.parse(m.getString("start")),
            LocalTime.parse(m.getString("end")), m.getString("slot"), m.s("venue")) },
        objs(o.getJSONArray("attendance")).map { a -> AttendanceRecord(a.getString("courseCode"), a.s("courseTitle"), a.getString("type"), a.s("slot"), a.getInt("attended"), a.getInt("total"), a.i("portalPercent")) },
        objs(o.getJSONArray("marks")).map(::mk),
        objs(o.getJSONArray("grades")).map { g -> GradeRecord(g.getString("courseCode"), g.getString("title"), g.s("type"), g.d("credits"), g.getString("grade"), g.s("examMonth")) },
        o.optJSONObject("summary")?.let { s -> CgpaSummary(s.d("creditsRegistered"), s.d("creditsEarned"), s.d("cgpa")) },
        emptyList(),
        objs(o.getJSONArray("exams")).map { e -> ExamSlot(e.getString("examType"), e.getString("courseCode"), e.s("courseTitle"), e.s("courseType"), e.s("date")?.let(LocalDate::parse),
            e.s("session"), e.s("reportingTime"), e.s("examTime"), e.s("venue"), e.s("seatLocation"), e.s("seatNumber")) },
        objs(o.getJSONArray("calendar")).map { d -> CalendarDay(LocalDate.parse(d.getString("date")), d.getString("label"), en(d.getString("kind"))) },
        objs(o.getJSONArray("results")).map { r -> r.getJSONObject("semester").let { s -> SemesterResult(Semester(s.getString("id"), s.getString("name")),
            objs(r.getJSONArray("courses")).map { g -> GradedCourse(g.getString("code"), g.getString("title"), g.s("type"), g.d("credits"), g.s("gradingType"), g.d("grandTotal"), g.s("grade"),
                objs(g.getJSONArray("components")).map(::mk), objs(g.getJSONArray("ranges")).map { x -> GradeRange(x.getString("grade"), x.getString("range")) }) }) } },
    )
}
