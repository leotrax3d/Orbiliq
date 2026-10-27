package org.orbiliq.core.domain

import kotlinx.coroutines.flow.Flow
import org.orbiliq.core.model.*

/** Local, reversible associations; no remote contacts are mutated or uploaded.
 * Implementations transact link reassignment, permitting at most one person per identity.
 */
interface PeopleRepository {
    fun observePeople(): Flow<List<Person>>
    fun observeIdentities(personId: PersonId): Flow<List<MessengerIdentity>>
    fun observeContactConflicts(): Flow<List<ContactConflict>>
    /** Compare-and-set correction, including assignment to a newly created person via split. */
    suspend fun reassign(identityId: IdentityId, expectedPerson: PersonId?, target: PersonId)
    suspend fun split(identityId: IdentityId, expectedPerson: PersonId, person: Person)
    suspend fun link(link: PersonIdentityLink)
    suspend fun unlink(identityId: IdentityId)
    suspend fun merge(source: PersonId, target: PersonId)
}
