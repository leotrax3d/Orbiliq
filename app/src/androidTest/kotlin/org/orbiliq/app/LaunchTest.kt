package org.orbiliq.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun assertInboxState() {
        // Existing device data may have archived every seeded chat. Verify a loaded inbox,
        // without resetting data or requiring a particular unarchived fixture.
        val expected = "Chats und letzte Nachricht suchen"
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText(expected)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(expected).assertIsDisplayed()
    }
    @Test fun survivesActivityRecreation() {
        compose.activityRule.scenario.recreate()
        assertInboxState()
    }
    @Test fun launchesAndExplainsMissingAccounts() {
        org.junit.Assert.assertTrue(compose.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
        compose.onNodeWithText("Orbiliq").assertIsDisplayed()
        assertInboxState()
    }
}
