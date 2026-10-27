package org.orbiliq.core.provider.mock

import java.time.Instant
import org.orbiliq.core.model.*
import org.orbiliq.core.provider.api.*

/** Synthetic data only. No protocol, filesystem or network access. */
object MockFixtures {
    val providerId = ProviderId("org.orbiliq.mock")
    val start: Instant = Instant.parse("2026-10-27T12:00:00Z")
    val account = Account(AccountId("mock-account"), providerId, "Lokales Testkonto", IdentityId("mock-account:self"))
    fun handle(account: Account = this.account) = AccountHandle(account, "mock-no-secret")
    val operations: Set<OperationKey> = setOf(
        Operations.SendText, Operations.ReceiveText, Operations.Reply, Operations.EditText,
        Operations.DeleteSelf, Operations.DeleteAll, Operations.React, Operations.ReadReceipt,
        Operations.Typing, Operations.History, CommandPayload.Logout.operation,
    ) + AttachmentKind.entries.map(Operations::sendMedia)
}

enum class MockProfile { Full, TextOnly, ReadOnly }
enum class MockFault { NetworkNoEffect, RateLimited, UnknownAfterCommit, UnknownNoEffect }
data class MockConfiguration(
    val profile: MockProfile = MockProfile.Full,
    val requireLogin: Boolean = true,
    val initiallyOffline: Boolean = false,
    val start: Instant = MockFixtures.start,
    val maxMediaBytes: Int = 1024 * 1024,
    val maxTextLength: Int = 4096,
    val dispatchDelayMillis: Long = 0,
) {
    init { require(maxMediaBytes > 0); require(maxTextLength > 0); require(dispatchDelayMillis >= 0) }
}

class MockProviderFactory(
    private val configuration: MockConfiguration = MockConfiguration(),
    private val providerId: ProviderId = MockFixtures.providerId,
) : ProviderFactory {
    override val descriptor = ProviderDescriptor(providerId, "Lokaler Mock-Messenger", "1",
        ContractVersion(1, 0), MockFixtures.operations, ReplayClass.BestEffort)
    override suspend fun open(account: AccountHandle): MockProviderSession = createSession(account.account)
    /** Synchronous construction for Compose previews and dependency injection; no IO. */
    fun createSession(account: Account = MockFixtures.account): MockProviderSession {
        require(account.providerId == descriptor.id)
        return MockProviderSession(account, configuration)
    }
}
