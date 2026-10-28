package org.orbiliq.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessengerNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun awaitInbox() {
        assumeTrue(BuildConfig.MOCK_MESSENGER)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Chats und letzte Nachricht suchen").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun openAnnaWithoutChangingArchive() {
        if (compose.onAllNodesWithText("Anna Beispiel").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithText("Archiv").performClick()
        }
        compose.onNodeWithText("Anna Beispiel").performClick()
    }
    @Test fun composeDraftSurvivesNavigationAndActivityRecreation() {
        awaitInbox()
        val text = "synthetic draft ${System.nanoTime()}"
        openAnnaWithoutChangingArchive()
        compose.onNodeWithText("Nachricht über Mock").performTextInput(text)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(text).assertIsDisplayed()
        compose.onNodeWithText("‹ Chats").performClick()
        compose.onNodeWithText("Anna Beispiel").performClick()
        compose.onNodeWithText(text).assertIsDisplayed()
        compose.onNodeWithContentDescription("Nachricht senden").performClick()
        compose.waitUntil(10_000) {
            compose.onNode(hasSetTextAction()).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.isEmpty()
        }
        compose.onNodeWithText(text).assertIsDisplayed()
    }
    @Test fun filtersPeopleAccountsAndSettingsAreReachable() {
        awaitInbox()
        compose.onNodeWithText("Gruppen").performClick()
        compose.onNodeWithText("Orbiliq Testgruppe").assertIsDisplayed()
        compose.onNodeWithText("Anna Beispiel").assertDoesNotExist()
        compose.onNodeWithText("Menschen").performClick()
        compose.onAllNodesWithText("Anna Beispiel").onFirst().performClick()
        compose.onNodeWithText("Person & Messenger-Identitäten").assertIsDisplayed()
        compose.onNodeWithText("‹ Zurück").performClick()
        compose.onNodeWithText("Konten").performClick()
        compose.onNodeWithText("Darstellung & Einstellungen").performClick()
        compose.onNodeWithText("Dunkel").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Dunkel").assertIsSelected()
    }
}
