package dev.personalhub.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

// Explicit opt-in live PUBLIC-page test. Never authenticates, reads forms or extracts records.
// Not part of the offline suite; include with -PportalProbe and select this class.
class PublicPortalProbeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun officialPublicPortalLoadsInVisibleProbe() {
        rule.onNodeWithText("College", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Open device probes").performClick()
        rule.onNodeWithText("VTOP").performClick()
        rule.waitUntil(45000) {
            rule.onAllNodes(hasText("Page loaded.", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Inspect page shape").performClick()
        rule.waitUntil(5000) {
            rule.onAllNodes(hasText("Dataset validation still pending.", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
