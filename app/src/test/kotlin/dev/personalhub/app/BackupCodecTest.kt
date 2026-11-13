package dev.personalhub.app

import dev.personalhub.app.backup.*
import dev.personalhub.college.api.*
import dev.personalhub.focus.api.*
import dev.personalhub.training.api.*
import dev.personalhub.usage.api.*
import dev.personalhub.usage.api.Target
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.*

// R01: synthetic full backup round trip and hostile-input rejection (no personal data).
class BackupCodecTest {
    private val t0 = Instant.parse("2026-11-13T10:00:00Z")
    private val zone = ZoneId.of("Asia/Kolkata")
    private val day = LocalDate.of(2026, 11, 13)

    private fun sample(): FullBackup {
        val task = Task("t1", "Synthetic", day, Priority.HIGH, LocalTime.of(9, 30), Recurrence.NONE, false, t0, t0, google = true, undated = false)
        val someday = Task("t2", "Someday", day, Priority.NORMAL, null, Recurrence.NONE, false, t0, t0, undated = true)
        val habit = Habit("h1", "Private", HabitUnit.COUNT, GoalDirection.REDUCE, 0.0, true, t0, t0)
        val moods = (1..10).map { MoodEntry("m$it", Mood.BORED, t0, zone, t0, t0, if (it == 1) "note" else null, 1) }
        val focus = FocusBackup(listOf(task, someday), mapOf("t1" to ("g1" to "L")), listOf(TaskOccurrence("t1", day, true)), listOf(habit),
            listOf(HabitEntry("h1", day, 0.0, true, null, t0)), moods)
        val set = WorkoutSet(SetType.NORMAL, 80.0, 5, null, null, 8.0, true)
        val training = TrainingBackup(
            listOf(Exercise("custom:x", "Synthetic Move", listOf("alias"), Equipment.OTHER, Metric.WEIGHT_REPS, Category.PUSH, setOf(Muscle.CHEST), setOf(Muscle.TRICEPS), listOf("Do it"), custom = true)),
            listOf(Routine("r1", "Push", listOf(RoutineItem("seed:bench_bb", 3, 5, 80.0, 120)), null, false, t0)),
            listOf(Workout("w1", "Push", "r1", t0, t0.plusSeconds(3600), listOf(WorkoutExercise("seed:bench_bb", listOf(set), 120))),
                Workout("w2", "Active", null, t0, null, emptyList())))
        val usage = UsageBackup(FocusSettings(true, true, Curfew(true, LocalTime.of(23, 0), LocalTime.of(6, 0)), perApp = true),
            listOf(LimitRule("INSTAGRAM:VIDEO", Target.INSTAGRAM, Surface.VIDEO, 30)),
            listOf(UsageInterval(Target.X, Surface.TEXT_FEED, Source.BROWSER, "com.android.chrome", t0, t0.plusSeconds(60), 4, 2.5, 3)),
            listOf(Override(t0.plusSeconds(300), t0, "budget")), emptyList())
        val college = CollegeSnapshot(Semester("S1", "Fall"), listOf(Semester("S1", "Fall")), t0,
            listOf(Course("ZZZ1001L", "Synthetic", "Embedded Theory", 3.0, "A1", "XB1-101", null, null)),
            listOf(ClassMeeting("ZZZ1001L", "ETH", DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(8, 50), "A1", "XB1-101")),
            listOf(AttendanceRecord("ZZZ1001L", "Synthetic", "Embedded Theory", "A1", 27, 30, 90)),
            listOf(MarkComponent("ZZZ1001L", null, "CAT1", 50.0, 15.0, 40.0, 12.0, "Present")),
            listOf(GradeRecord("ZZZ0001L", "Old", null, 3.0, "A", "Nov-2025")), CgpaSummary(40.0, 40.0, 8.5), emptyList(),
            listOf(ExamSlot("FAT", "ZZZ1001L", null, null, day, "FN", null, null, "XB1", null, "12")),
            listOf(CalendarDay(day, "Holiday - Synthetic", CalendarKind.HOLIDAY)),
            listOf(SemesterResult(Semester("W25", "Winter"), listOf(GradedCourse("ZZZ0001L", "Old", null, 3.0, "RG", 71.5, "A",
                listOf(MarkComponent("ZZZ0001L", null, "CAT1", 50.0, 15.0, 40.0, 12.0, null)), listOf(GradeRange("A", "72 - 79")))))))
        return FullBackup(t0, zone, focus, training, usage, college, AppSettingsBackup("light", true, true, false, "21:00"))
    }

    @Test fun fullBackupRoundTripsEveryDomainExactly() {
        val b = sample()
        val back = BackupCodec.decode(BackupCodec.encode(b).toString())
        assertEquals(b.focus, back.focus)                 // ten same-ms moods, explicit zero, undated + Google link
        assertEquals(b.training, back.training)           // custom exercise, routine, finished + active workout
        assertEquals(b.usage, back.usage)
        assertEquals(b.college.copy(statuses = emptyList()), back.college)
        assertEquals(b.settings, back.settings); assertEquals(t0, back.createdAt)
        assertEquals(10, back.focus.moods.size)
    }
    @Test fun envelopeDeclaresFullRestorableBackupWithoutSecrets() {
        val j = BackupCodec.encode(sample())
        assertEquals("personalhub-json", j.getString("format")); assertTrue(j.getBoolean("restorable"))
        val text = j.toString().lowercase()
        listOf("password", "cookie", "token", "csrf").forEach { assertFalse("leaked $it", text.contains("\"$it")) }
    }
    @Test fun hostileOrWrongFilesAreRejected() {
        val good = BackupCodec.encode(sample())
        fun bad(mutate: (JSONObject) -> Unit) = assertThrows(BackupFormatException::class.java) { BackupCodec.decode(JSONObject(good.toString()).also(mutate).toString()) }
        bad { it.put("format", "other") }
        bad { it.put("schemaVersion", 99) }
        bad { it.put("exportKind", "review_export").put("restorable", false) }
        bad { it.getJSONObject("data").getJSONObject("focus").getJSONArray("moods").getJSONObject(0).put("moodCode", "neutral") }
        bad { it.getJSONObject("data").getJSONObject("focus").getJSONArray("moods").getJSONObject(1).put("id", "m1") } // duplicate id
        bad { it.getJSONObject("data").getJSONObject("training").getJSONArray("workouts").getJSONObject(0).put("finishedAt", JSONObject.NULL) } // two active
        bad { it.getJSONObject("data").getJSONObject("focus").getJSONArray("habitEntries").getJSONObject(0).put("value", -1) }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("not json") }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("{\"format\":\"personalhub-json\",\"schemaVersion\":1,\"exportKind\":\"full_backup\",\"restorable\":true}") }
    }
}
