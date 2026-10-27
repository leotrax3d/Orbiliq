package org.orbiliq.core.model

import org.junit.Assert.*
import org.junit.Test

class ProviderIdTest {
    @Test fun acceptsNewProvidersWithoutEnumChange() {
        assertEquals("new-provider", ProviderId("new-provider").value)
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankIdentity() { ProviderId("  ") }
}
