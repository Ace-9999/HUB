package dev.personalhub.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import java.util.UUID

// Real Compose navigation, recreation and a Focus task journey, with no source permissions.
// Runs against the demo flavor; synthetic records it creates are deleted before it finishes.
class ShellTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun navigationSettingsAndRecreationRemainUsableWithoutPermissions() {
        rule.waitForIdle()
        rule.onNodeWithText("Your day, in one place.").assertIsDisplayed()
        rule.onAllNodesWithText("Focus").onLast().performClick()
        rule.onNodeWithText("Mood").performClick()
        rule.onNodeWithText("How are you?").assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("How are you?").assertIsDisplayed()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Appearance").assertIsDisplayed()
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithText("How are you?").assertIsDisplayed()
        rule.onAllNodesWithText("College").onLast().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Connect VTOP", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() ||
            rule.onAllNodesWithText("Portal sync").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun taskCanBeAddedCompletedUndoneAndDeleted() {
        val title = "Synthetic ${UUID.randomUUID().toString().take(8)}"
        rule.onAllNodesWithText("Focus").onLast().performClick()
        rule.onNodeWithText("Tasks").performClick()
        rule.onNodeWithText("Add").performClick()
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement(title)
        rule.onNodeWithText("SAVE").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(title).performScrollTo().assertIsDisplayed()
        rule.onNode(hasClickLabel("Complete $title"), useUnmergedTree = true).performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Completed “$title”").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Undo").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasClickLabel("Complete $title"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasClickLabel("Delete $title"), useUnmergedTree = true).performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(title).fetchSemanticsNodes().isEmpty() }
    }

    @Test fun workoutCanBeLoggedFinishedOnceAndDeleted() {
        rule.onAllNodesWithText("Train").onLast().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("START EMPTY WORKOUT").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("START EMPTY WORKOUT").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Add exercise").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Add exercise").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Bench Press").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Bench Press").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasClickLabel("Complete set 1"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasClickLabel("Complete set 1"), useUnmergedTree = true).performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("REST").fetchSemanticsNodes().isNotEmpty() } // rest countdown started
        rule.onNodeWithText("FINISH").performScrollTo().performClick()
        rule.onNodeWithText("TAP AGAIN TO FINISH").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("History").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("History").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Workout").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("Delete").onFirst().performScrollTo().performClick()
        rule.onNodeWithText("Tap again to delete").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("No workouts yet").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun hasClickLabel(label: String) = SemanticsMatcher("click label $label") {
        it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsActions.OnClick) { null }?.label == label
    }
}
