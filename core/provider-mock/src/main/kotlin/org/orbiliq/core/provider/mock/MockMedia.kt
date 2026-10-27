package org.orbiliq.core.provider.mock

import java.io.ByteArrayOutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.orbiliq.core.model.AttachmentRef
import org.orbiliq.core.provider.api.*

/** Bounded synthetic bytes, no URI resolution or remote fetching. Caller owns streams. */
internal class MockMedia(private val session: MockProviderSession, private val limit: Int) : ProviderMedia {
    override val accountId = session.accountId
    override suspend fun upload(source: MediaSource, mimeType: String): OperationResult<AttachmentRef> {
        require(mimeType.isNotBlank())
        session.mediaFailure(upload = true)?.let { return OperationResult.Rejected(it) }
        if (source.sizeBytes?.let { it < 0 || it > limit } == true) return tooLarge()
        val buffer = ByteArrayOutputStream()
        while (true) {
            currentCoroutineContext().ensureActive()
            session.mediaFailure(upload = true)?.let { return OperationResult.FailedNoEffect(it) }
            val chunk = source.read(minOf(8192L, limit.toLong() - buffer.size() + 1).toInt())
            if (chunk.isEmpty()) break
            if (chunk.size > 8192 || chunk.size > limit - buffer.size()) return tooLarge()
            buffer.write(chunk)
        }
        currentCoroutineContext().ensureActive()
        return session.storeMedia(buffer.toByteArray(), mimeType)
    }
    override suspend fun download(ref: AttachmentRef, sink: MediaSink): OperationResult<Unit> {
        session.mediaFailure()?.let { return OperationResult.Rejected(it) }
        if (ref.accountId != accountId) return OperationResult.Rejected(ProviderFailure(FailureCategory.PermissionDenied))
        val bytes = session.loadMedia(ref) ?: return OperationResult.Rejected(ProviderFailure(FailureCategory.MediaUnavailable))
        try {
            var offset = 0
            while (offset < bytes.size) {
                currentCoroutineContext().ensureActive()
                session.mediaFailure()?.let {
                    return if (offset == 0) OperationResult.FailedNoEffect(it)
                    else OperationResult.OutcomeUnknown("mock.partial-download")
                }
                val end = minOf(offset + 8192, bytes.size)
                sink.write(bytes.copyOfRange(offset, end)); offset = end
            }
            return OperationResult.Success(Unit)
        } finally { bytes.fill(0) }
    }
    private fun tooLarge() = OperationResult.Rejected(ProviderFailure(FailureCategory.MediaUnavailable, diagnosticCode = "mock.media-limit"))
}
