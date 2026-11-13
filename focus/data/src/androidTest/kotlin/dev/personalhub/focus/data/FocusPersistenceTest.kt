package dev.personalhub.focus.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.personalhub.core.*
import dev.personalhub.focus.api.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.*
import java.util.UUID

// Actual SQLite persistence, migration, privacy-record and invalid-command atomicity tests.
class FocusPersistenceTest {
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FocusDatabase::class.java)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-11-12T12:00:00Z")
    private val time = AppTime(Clock.fixed(now, ZoneOffset.UTC))
    @Test fun schemaOneRevisionSurvivesAdditiveMigration() {
        val name = "test-${UUID.randomUUID()}.db"
        try {
            migration.createDatabase(name, 1).apply { execSQL("INSERT INTO focus_revision VALUES (1, 7)"); close() }
            migration.runMigrationsAndValidate(name, 2, true, FocusDatabase.MIGRATION_1_2).apply {
                query("SELECT revision FROM focus_revision WHERE id = 1").use { assertTrue(it.moveToFirst()); assertEquals(7L, it.getLong(0)) }; close()
            }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun schemaTwoMigratesAndGoogleSyncAppliesLastWriterWins() = runBlocking<Unit> {
        val name = "test-${UUID.randomUUID()}.db"
        try {
            migration.createDatabase(name, 2).apply {
                execSQL("INSERT INTO tasks VALUES ('t1','Synthetic','2026-11-12','NORMAL',NULL,'NONE',0,1,1,0)"); close()
            }
            migration.runMigrationsAndValidate(name, 3, true, FocusDatabase.MIGRATION_1_2, FocusDatabase.MIGRATION_2_3).close()
            val repo = RoomFocusRepository.open(context, time, name = name)
            try {
                assertEquals("Synthetic", repo.snapshots.first().tasks.single().title)
                // remote import creates a linked task; completion maps to its occurrence
                repo.applyRemote(RemoteTask("g1", "L", "From Google", LocalDate.parse("2026-11-12"), true, false, now))
                val imported = repo.snapshots.first().tasks.single { it.google }
                assertTrue(repo.snapshots.first().tasksOn(LocalDate.parse("2026-11-12")).single { it.task.id == imported.id }.completed)
                // local edit newer than remote is kept; local candidates are dirty until pushed
                repo.saveTask(TaskDraft(imported.id, "Edited locally", LocalDate.parse("2026-11-12")))
                repo.applyRemote(RemoteTask("g1", "L", "Older remote", LocalDate.parse("2026-11-12"), true, false, now.minusSeconds(3600)))
                assertEquals("Edited locally", repo.snapshots.first().tasks.single { it.id == imported.id }.title)
                assertTrue(repo.syncCandidates().single { it.localId == imported.id }.dirty)
                repo.linkPushed(imported.id, "g1", "L", now); assertFalse(repo.syncCandidates().single { it.localId == imported.id }.dirty)
                repo.applyRemote(RemoteTask("g1", "L", "Deleted remotely", null, false, true, now.plusSeconds(60)))
                assertTrue(repo.snapshots.first().tasks.none { it.id == imported.id })
                repo.setLastSync(now); assertEquals(now, repo.lastSync())
            } finally { repo.close() }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun taskHabitAndTenSameMillisecondMoodsSurviveReopenWithUndo() = runBlocking<Unit> {
        val name = "test-${UUID.randomUUID()}.db"
        try {
            var repository = RoomFocusRepository.open(context, time, name = name)
            val task = repository.saveTask(TaskDraft(title = "Synthetic task", firstDay = time.today(), recurrence = Recurrence.DAILY))
            repository.completeTask(task, time.today(), true)
            val habit = repository.saveHabit(HabitDraft(label = "Synthetic private", sensitive = true, direction = GoalDirection.REDUCE, target = 0.0))
            repository.putHabitEntry(habit, time.today(), 0.0, "Synthetic private note")
            val ids = (1..10).map { repository.saveMood(MoodDraft(mood = Mood.BORED, occurredAt = now, originalZone = time.zone, note = "Synthetic")) }
            assertEquals(10, ids.distinct().size)
            repository.trashMood(ids.first()); assertEquals(9, repository.snapshots.first().moods.size)
            repository.trashMood(ids.first(), false)
            repository.close(); repository = RoomFocusRepository.open(context, time, name = name)
            try {
                val state = repository.snapshots.first()
                assertEquals(10, state.moods.size); assertEquals(1, state.moods.map { it.occurredAt }.distinct().size)
                assertTrue(state.tasksOn(time.today()).single().completed)
                assertFalse(state.tasksOn(time.today().plusDays(1)).single().completed)
                assertTrue(state.habitEntries.single().explicitZero)
                assertEquals("Synthetic private note", state.habitEntries.single().note)
            } finally { repository.close() }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun invalidFutureMoodAndNegativeHabitLeaveExistingRecordsUntouched() = runBlocking<Unit> {
        val name = "test-${UUID.randomUUID()}.db"
        val repository = RoomFocusRepository.open(context, time, name = name)
        try {
            val h = repository.saveHabit(HabitDraft(label = "Synthetic"))
            repository.putHabitEntry(h, time.today(), 2.0)
            try { repository.putHabitEntry(h, time.today(), -1.0); fail("Negative value accepted") } catch (_: IllegalArgumentException) { }
            try { repository.saveMood(MoodDraft(mood = Mood.CALM_CONTENT, occurredAt = now.plusSeconds(1), originalZone = time.zone)); fail("Future mood accepted") } catch (_: IllegalArgumentException) { }
            assertEquals(2.0, repository.snapshots.first().habitEntries.single().value, 0.0)
            assertTrue(repository.snapshots.first().moods.isEmpty())
        } finally { repository.close(); context.deleteDatabase(name) }
    }
}
