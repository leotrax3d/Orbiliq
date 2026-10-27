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
        compose.onNodeWithText(if (BuildConfig.MOCK_MESSENGER) "[Mock] Anna Beispiel" else "Noch keine Unterhaltungen").assertIsDisplayed()
        if (BuildConfig.MOCK_MESSENGER) compose.onNodeWithText("[Mock] Orbiliq Testgruppe").assertIsDisplayed()
    }
    @Test fun survivesActivityRecreation() {
        compose.activityRule.scenario.recreate()
        assertInboxState()
    }
    @Test fun launchesAndExplainsMissingAccounts() {
        compose.onNodeWithText("Orbiliq").assertIsDisplayed()
        assertInboxState()
    }
}
