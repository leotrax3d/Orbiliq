package org.orbiliq.core.model

/** Namespaced, versioned opaque data. Adapters own decoding; never store credentials here.
 * The private payload is deliberately absent from toString and defensively copied.
 */
class ProviderData(val providerId: ProviderId, val namespace: String, val schemaVersion: Int, payload: ByteArray) {
    private val bytes = payload.copyOf()
    init { require(namespace.isNotBlank()); require(schemaVersion > 0) }
    fun payload(): ByteArray = bytes.copyOf()
    override fun toString() = "ProviderData(providerId=$providerId, namespace=$namespace, schemaVersion=$schemaVersion)"
}

data class Person(val id: PersonId, val displayName: String)
enum class LinkOrigin { Manual, LocalContact, ProviderSuggestion, VerifiedPhone, AutomaticSeparate }
/** Linking/unlinking people never rewrites an identity, route or message. */
data class PersonIdentityLink(val personId: PersonId, val identityId: IdentityId, val origin: LinkOrigin)
data class MessengerIdentity(
    val id: IdentityId, val ref: IdentityRef, val displayName: String,
    val extensions: List<ProviderData> = emptyList(),
    val phone: PhoneEvidence? = null,
)
data class Account(
    val id: AccountId, val providerId: ProviderId, val displayName: String,
    val ownIdentityId: IdentityId? = null,
)
data class GroupMember(val identityId: IdentityId, val role: GroupRole)
enum class GroupRole { Owner, Administrator, Member, Restricted, Unknown }
data class Group(
    val id: GroupId, val ref: GroupRef, val title: String,
    val members: Set<GroupMember> = emptySet(),
    val extensions: List<ProviderData> = emptyList(),
)
enum class Encryption { EndToEnd, Transport, Preview, Unknown }
enum class Verification { Verified, Unverified, Changed, Unknown }
data class SecurityContext(
    val encryption: Encryption = Encryption.Unknown,
    val verification: Verification = Verification.Unknown,
)
enum class DataQuality { PreviewOnly, SinceCheckpoint, PartialHistory, ProviderHistory }
