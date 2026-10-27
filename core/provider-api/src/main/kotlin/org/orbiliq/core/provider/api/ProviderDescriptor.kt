package org.orbiliq.core.provider.api

import org.orbiliq.core.model.ProviderId

/** Initial discovery boundary; commands/events follow the normative provider contract later. */
data class ProviderDescriptor(val id: ProviderId, val displayName: String)
