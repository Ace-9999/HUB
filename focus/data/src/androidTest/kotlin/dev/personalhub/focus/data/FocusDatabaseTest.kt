package dev.personalhub.focus.data

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

// D01 exercises actual on-device SQLite and closes/reopens a synthetic test store.
class FocusDatabaseTest {
    @Test fun acknowledgedRevisionSurvivesReopen() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test-${UUID.randomUUID()}.db"
        try {
            val first = FocusDatabase.open(context, name)
            try {
                val db = first
                db.revision().committedChange()
                db.revision().committedChange()
                assertEquals(2L, db.revision().current())
            } finally { first.close() }
            val reopened = FocusDatabase.open(context, name)
            try { assertEquals(2L, reopened.revision().current()) } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }
}
