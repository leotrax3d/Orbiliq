package org.orbiliq.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Does not grant companion access or register/link a real account. */
@RunWith(AndroidJUnit4::class)
class SignalSetupNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun accountsCanOpenAndLeaveHonestSignalSetup() {
        compose.onNodeWithText("Konten").performClick()
        compose.onNodeWithText("Signal Companion · Einrichtung (experimentell)").performClick()
        compose.onNodeWithText("Signal einrichten").assertIsDisplayed()
        compose.onNodeWithText("Nachrichten sind in Orbiliq noch nicht verfügbar.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Zurück").performClick()
        compose.onNodeWithText("Signal Companion · Einrichtung (experimentell)").assertIsDisplayed()
    }
}
