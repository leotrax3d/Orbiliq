package org.orbiliq.core.model

/** Provider adapters may mark ownershipConfirmed only for current, authoritative ownership.
 * Address-book entries, profile text and usernames are never ownership proof.
 */
class PhoneEvidence(val number: String, val ownershipConfirmed: Boolean = false) {
    override fun toString() = "PhoneEvidence(redacted)"
}

/** Conservative international syntax only. No country guessing, suffix or fuzzy matching.
 * This validates syntax, not allocation or ownership of a telephone number.
 */
fun normalizeContactPhone(raw: String): String? {
    if (raw.any { it !in "+0123456789 ()-" }) return null
    val compact = raw.filter { it !in " ()-" }
    val international = if (compact.startsWith("00")) "+" + compact.drop(2) else compact
    return international.takeIf { Regex("\\+[1-9][0-9]{7,14}").matches(it) }
}

enum class ContactConflictReason { AmbiguousPhone, InconsistentPerson, DuplicateProvider, OwnIdentity }
data class ContactConflict(val identities: Set<IdentityId>, val reason: ContactConflictReason)
class ContactAssignmentConflict : IllegalStateException("Contact assignment changed or conflicts")
