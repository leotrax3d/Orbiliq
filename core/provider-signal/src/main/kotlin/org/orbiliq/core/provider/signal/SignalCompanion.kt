// SPDX-License-Identifier: MIT
package org.orbiliq.core.provider.signal

import kotlinx.coroutines.flow.Flow

/** Version 1 deliberately exposes only local linking state, never Signal keys or messages. */
object SignalBridgeContract {
    const val VERSION = 1
    const val PACKAGE = "org.orbiliq.signal.companion"
    const val CLIENT_PACKAGE = "org.orbiliq.app"
    const val AUTHORITY = "$PACKAGE.bridge"
    const val PERMISSION = "$PACKAGE.BIND"
    const val CONSENT_ACTIVITY = "org.orbiliq.signal.bridge.BridgeConsentActivity"
    fun decodeStatus(version: Int, state: String?, capabilities: List<String>?): SignalCompanionStatus {
        if (version != VERSION || capabilities == null || capabilities.isNotEmpty()) {
            return SignalCompanionStatus(SignalLinkState.UpdateRequired)
        }
        return SignalCompanionStatus(SignalLinkState.entries.firstOrNull { it.name == state } ?: SignalLinkState.UpdateRequired)
    }
}

enum class SignalLinkState {
    Missing, Untrusted, ConsentRequired, NeedsLink, Linked, Revoked, Locked,
    PrimaryDeviceNotSupported, UpdateRequired, Unavailable,
}

/** Local credential state is not evidence of a working Signal network connection. */
data class SignalCompanionStatus(val link: SignalLinkState)

interface SignalCompanionTransport {
    fun statuses(): Flow<SignalCompanionStatus>
    suspend fun refresh(): SignalCompanionStatus
    /** Opens the companion's user consent / linking UI. No QR or keys cross this boundary. */
    suspend fun openSetup()
    /** Revokes only the local Orbiliq grant; does not unregister the Signal device. */
    suspend fun revokeAccess()
}

/** Shared by the Android endpoints. Only the current single signer is accepted.
 * No signer history, package-name-only trust or ambiguous shared UID is sufficient.
 */
object SignalBridgeTrust {
    fun permits(expectedPackage: String, uidPackages: Set<String>, ownSigners: Set<String>, peerSigners: Set<String>): Boolean =
        uidPackages == setOf(expectedPackage) && ownSigners.size == 1 &&
            peerSigners == ownSigners && ownSigners.all { it.matches(Regex("[0-9a-f]{64}")) }
}
