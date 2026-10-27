package org.orbiliq.core.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class UnconfiguredConversationRepositoryTest {
    @Test fun unconfiguredInstallationContainsNoInventedMessages() = runTest {
        assertTrue(UnconfiguredConversationRepository().observeInbox().first().isEmpty())
    }
}
