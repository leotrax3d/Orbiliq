package org.orbiliq.core.provider.api

import java.time.Instant

enum class AuthInputKind { Code, Password, Confirmation, Qr, External }
data class AuthChallenge(
    val id: String, val kind: AuthInputKind, val expiresAt: Instant,
    val schemaVersion: Int = 1, val instructionCode: String,
) { init { require(id.isNotBlank()); require(schemaVersion > 0); require(instructionCode.isNotBlank()) } }
/** Secret values intentionally do not participate in generated logging or equality. */
class AuthResponse(val challengeId: String, val kind: AuthInputKind, secret: CharArray) : AutoCloseable {
    private val value = secret.copyOf()
    init { require(challengeId.isNotBlank()) }
    fun secret(): CharArray = value.copyOf()
    override fun close() { value.fill('\u0000') }
    override fun toString() = "AuthResponse(redacted)"
}
sealed interface AuthResult {
    data object Ready : AuthResult
    data class NeedsInput(val challenge: AuthChallenge) : AuthResult
    data object UpdateRequired : AuthResult
    data class Rejected(val failure: ProviderFailure) : AuthResult
}
