package org.orbiliq.core.provider.api

import org.orbiliq.core.model.*

data class ContractVersion(val major: Int, val minor: Int) {
    init { require(major > 0); require(minor >= 0) }
    fun supports(required: ContractVersion): Boolean = major == required.major && minor >= required.minor
}
data class ProviderDescriptor(
    val id: ProviderId, val displayName: String,
    val runtimeVersion: String, val contractVersion: ContractVersion,
    val potential: Set<OperationKey>, val replay: ReplayClass,
) { init { require(displayName.isNotBlank()); require(runtimeVersion.isNotBlank()) } }

/** Explicit registration, no protocol-name branches or reflection. */
class ProviderRegistry(factories: Collection<ProviderFactory>, required: ContractVersion = ContractVersion(1, 0)) {
    private val entries = factories.associateBy { it.descriptor.id }
    init {
        require(entries.size == factories.size) { "Duplicate provider registration" }
        require(entries.values.all { it.descriptor.contractVersion.supports(required) }) { "Incompatible provider contract" }
    }
    fun find(id: ProviderId): ProviderFactory? = entries[id]
    val descriptors: List<ProviderDescriptor> get() = entries.values.map { it.descriptor }
}
