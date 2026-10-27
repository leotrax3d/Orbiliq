package org.orbiliq.core.model

/** Small atomic in-memory alias index for adapters and tests. Not a durable sync store.
 * Production storage must provide the same conflict semantics in a DB transaction.
 * Aliases are retained across temporary -> final ID transitions, never reassigned.
 */
class StableIdIndex<Remote : Any, Local : Any> : IdMapping<Remote, Local> {
    private val bindings = mutableMapOf<Remote, Local>()
    private val lock = Any()
    override suspend fun resolve(remote: Remote): Local? = synchronized(lock) { bindings[remote] }
    override suspend fun aliases(local: Local): Set<Remote> = synchronized(lock) {
        bindings.filterValues { it == local }.keys.toSet()
    }
    override suspend fun bind(remote: Remote, local: Local) {
        synchronized(lock) {
            val previous = bindings[remote]
            require(previous == null || previous == local) { "Remote alias already bound" }
            bindings[remote] = local
        }
    }
}
