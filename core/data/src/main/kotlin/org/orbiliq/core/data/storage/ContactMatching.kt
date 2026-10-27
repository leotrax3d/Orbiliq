package org.orbiliq.core.data.storage

import org.orbiliq.core.model.*

internal fun LinkOrigin.isAutomatic() = this == LinkOrigin.VerifiedPhone || this == LinkOrigin.AutomaticSeparate

private fun MessengerIdentity.key() = phone?.takeIf { it.ownershipConfirmed }?.number?.let(::normalizeContactPhone)

internal fun contactConflicts(s: LocalState): List<ContactConflict> = buildList {
    s.identities.mapNotNull { it.key() }.distinct().forEach { number ->
        val group = s.identities.filter { it.key() == number }
        if (group.size < 2) return@forEach
        val ids = group.map { it.id }.toSet()
        val persons = s.links.filter { it.identityId in ids && !it.origin.isAutomatic() }.map { it.personId }.toSet()
        val members = s.links.filter { it.personId in persons }.map { it.identityId }.toSet()
        val reason = when {
            s.accounts.any { it.ownIdentityId in ids } -> ContactConflictReason.OwnIdentity
            group.map { identity -> s.accounts.singleOrNull { it.id == identity.ref.accountId }?.providerId }.let { null in it || it.distinct().size != it.size } -> ContactConflictReason.DuplicateProvider
            persons.size > 1 -> ContactConflictReason.AmbiguousPhone
            s.identities.any { it.id in members && it.key() != number } -> ContactConflictReason.InconsistentPerson
            else -> null
        }
        reason?.let { add(ContactConflict(ids, it)) }
    }
}

/** Re-evaluate automatic links from current evidence; never merge established manual people.
 * Manual exclusions survive replay, process restart and subsequent phone changes.
 */
internal fun MutableLocalState.reconcileContacts() {
    val previous = links.toList()
    links.removeAll { it.origin.isAutomatic() && it.identityId !in manualIdentities }
    val conflicts = contactConflicts(freeze()).flatMap { it.identities }.toSet()
    val used = links.map { it.personId }.toMutableSet()
    identities.forEach { identity ->
        if (identity.id in manualIdentities || links.any { it.identityId == identity.id }) return@forEach
        val number = identity.key()
        val peers = if (number != null && identity.id !in conflicts) identities.filter {
            it.key() == number && it.id !in conflicts && it.id !in manualIdentities
        } else emptyList()
        // A manual person is a valid anchor only if its entire membership agrees.
        val anchors = if (number != null && identity.id !in conflicts) links.filter { link ->
            identities.any { it.id == link.identityId && it.key() == number }
        }.map { it.personId }.distinct() else emptyList()
        val target = anchors.singleOrNull() ?: peers.asSequence().mapNotNull { peer ->
            links.find { it.identityId == peer.id }?.personId
        }.firstOrNull() ?: previous.find { it.identityId == identity.id && it.personId !in used }?.personId
            ?: PersonId.random()
        if (people.none { it.id == target }) people.add(Person(target, identity.displayName))
        links.add(PersonIdentityLink(target, identity.id, if (number != null && identity.id !in conflicts && (peers.size > 1 || anchors.isNotEmpty())) LinkOrigin.VerifiedPhone else LinkOrigin.AutomaticSeparate))
        used.add(target)
    }
    // Only discard empty automatically generated people, never manually created records.
    val automaticPeople = previous.filter { it.origin.isAutomatic() }.map { it.personId }.toSet()
    people.removeAll { p -> p.id in automaticPeople && links.none { it.personId == p.id } }
}
