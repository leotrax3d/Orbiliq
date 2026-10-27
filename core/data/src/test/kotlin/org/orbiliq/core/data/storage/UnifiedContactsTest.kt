package org.orbiliq.core.data.storage

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.orbiliq.core.model.*
import org.orbiliq.core.provider.api.*
import java.time.Instant

class UnifiedContactsTest {
    private class Disk : SnapshotStorage {
        var bytes: ByteArray? = null
        var fail = false
        override fun read() = bytes?.copyOf()
        override fun write(bytes: ByteArray) { check(!fail); this.bytes = bytes.copyOf() }
        override fun close() {}
    }
    private val disk = Disk()
    private var r = PersistentLocalRepository(disk)
    private var sequence = 0
    private suspend fun identity(id: String, phone: String? = "+49 (151) 23456789", confirmed: Boolean = true, provider: String = id) {
        val account = AccountId(id)
        r.saveAccount(Account(account, ProviderId(provider), "Synthetic"))
        val now = Instant.EPOCH
        r.apply(EventEnvelope("${++sequence}", ReplayCursor(account, "contacts", "1", "$sequence"), now, now,
            ProviderEvent.IdentityChanged(MessengerIdentity(IdentityId(id), IdentityRef(account, id), "Same name",
                phone = phone?.let { PhoneEvidence(it, confirmed) }))))
    }
    private suspend fun person(id: String) = r.snapshot().links.single { it.identityId == IdentityId(id) }.personId

    @Test fun `three authoritative international numbers match across providers and reopen`() = runTest {
        identity("signal"); identity("whatsapp", "004915123456789"); identity("telegram", "+4915123456789")
        assertEquals(1, r.snapshot().links.map { it.personId }.distinct().size)
        r = PersistentLocalRepository(disk)
        assertEquals(3, r.observeIdentities(person("signal")).first().size)
    }
    @Test fun `names national suffix invalid and unconfirmed numbers never match`() = runTest {
        identity("a"); identity("b", "015123456789"); identity("c", confirmed = false)
        identity("d", "+4915123456789 ext 2"); identity("e", null)
        assertEquals(5, r.snapshot().links.map { it.personId }.distinct().size)
        assertNull(normalizeContactPhone("+49١5123456789"))
    }
    @Test fun `same provider collision stays separate with conflict`() = runTest {
        identity("a", provider = "signal"); identity("b", provider = "signal")
        assertNotEquals(person("a"), person("b"))
        assertEquals(ContactConflictReason.DuplicateProvider, r.observeContactConflicts().first().single().reason)
    }
    @Test fun `split persists and replay cannot undo correction`() = runTest {
        identity("a"); identity("b")
        val target = Person(PersonId("corrected"), "Corrected")
        r.split(IdentityId("b"), person("b"), target)
        r = PersistentLocalRepository(disk); identity("b"); identity("c")
        assertEquals(target.id, person("b")); assertNotEquals(person("a"), person("b"))
        assertTrue(r.observeContactConflicts().first().isNotEmpty())
    }
    @Test fun `unlink persists and stale correction fails atomically`() = runTest {
        identity("a"); identity("b"); val original = person("b")
        r.unlink(IdentityId("b")); r = PersistentLocalRepository(disk); identity("b")
        assertFalse(r.snapshot().links.any { it.identityId == IdentityId("b") })
        try { r.reassign(IdentityId("b"), original, person("a")); fail() } catch (_: ContactAssignmentConflict) {}
        assertFalse(r.snapshot().links.any { it.identityId == IdentityId("b") })
    }
    @Test fun `changed evidence separates automatic links but never overrides manual correction`() = runTest {
        identity("a"); identity("b"); identity("b", "+442012345678")
        assertNotEquals(person("a"), person("b"))
        r.merge(person("b"), person("a")); identity("b", "+33123456789")
        assertEquals(person("a"), person("b"))
    }
    @Test fun `ambiguous manual people cannot be joined by a bridge identity`() = runTest {
        identity("a"); identity("b")
        r.split(IdentityId("b"), person("b"), Person(PersonId("other"), "Other"))
        identity("c")
        assertEquals(3, r.snapshot().links.map { it.personId }.distinct().size)
    }
    @Test fun `failed split commit leaves original membership intact`() = runTest {
        identity("a"); identity("b"); val original = person("b"); disk.fail = true
        try { r.split(IdentityId("b"), original, Person(PersonId("new"), "New")); fail() } catch (_: IllegalStateException) {}
        assertEquals(original, person("b")); assertFalse(r.snapshot().people.any { it.id == PersonId("new") })
    }
    @Test fun `own identity never matches contact automatically`() = runTest {
        identity("a")
        r.saveAccount(Account(AccountId("a"), ProviderId("a"), "Self", IdentityId("a")))
        identity("b")
        assertNotEquals(person("a"), person("b"))
        assertEquals(ContactConflictReason.OwnIdentity, r.observeContactConflicts().first().single().reason)
    }
    @Test fun `correction compares membership and remains durable`() = runTest {
        identity("a"); identity("b", "+442012345678")
        val target = person("a"); val source = person("b")
        r.reassign(IdentityId("b"), source, target)
        r = PersistentLocalRepository(disk); identity("b")
        assertEquals(target, person("b"))
        try { r.reassign(IdentityId("b"), source, source); fail() } catch (_: ContactAssignmentConflict) {}
        assertEquals(target, person("b"))
    }
    @Test fun `v4 empty snapshot migrates without phone evidence or exclusions`() {
        val bytes = StoreCodec.encode(LocalState()).dropLast(4).toByteArray()
        bytes[3] = 4
        val migrated = StoreCodec.decode(bytes)
        assertTrue(migrated.manualIdentities.isEmpty())
        assertTrue(migrated.identities.isEmpty())
    }

}
