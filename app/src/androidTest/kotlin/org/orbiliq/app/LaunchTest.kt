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
    @Test fun survivesActivityRecreation() {
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Noch keine Unterhaltungen").assertIsDisplayed()
    }
    @Test fun launchesAndExplainsMissingAccounts() {
        compose.onNodeWithText("Orbiliq").assertIsDisplayed()
        compose.onNodeWithText("Noch keine Unterhaltungen").assertIsDisplayed()
    }
}
