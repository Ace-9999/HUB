package dev.personalhub.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File

// Real Compose navigation and recreation smoke, with no source permissions.
class ShellTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun navigationSettingsAndRecreationRemainUsableWithoutPermissions() {
        rule.waitForIdle()
        rule.onNodeWithText("A little more intentional.").assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(instrumentation.targetContext.filesDir, "I01-shell.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        rule.onNodeWithText("College", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Not connected").assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("Not connected").assertIsDisplayed()
        rule.onNodeWithText("Settings").performClick()
        rule.onNodeWithText("Appearance").assertIsDisplayed()
        rule.onNodeWithText("Done").performClick()
        rule.onNodeWithText("Not connected").assertIsDisplayed()
    }
}
