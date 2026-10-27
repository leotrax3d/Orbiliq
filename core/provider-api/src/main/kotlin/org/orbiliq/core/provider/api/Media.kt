package org.orbiliq.core.provider.api

import org.orbiliq.core.model.*

/** Bounded pull-based streams, no Android URIs or unbounded whole-file byte arrays.
 * read requires maxBytes > 0 and returns at most maxBytes; empty means EOF.
 * The caller owns close, including on failure/cancellation. Sinks apply backpressure.
 */
interface MediaSource {
    val sizeBytes: Long?
    suspend fun read(maxBytes: Int): ByteArray
    suspend fun close()
}
interface MediaSink {
    suspend fun write(chunk: ByteArray)
    suspend fun close()
}
interface ProviderMedia {
    val accountId: AccountId
    suspend fun upload(source: MediaSource, mimeType: String): OperationResult<AttachmentRef>
    suspend fun download(ref: AttachmentRef, sink: MediaSink): OperationResult<Unit>
}
