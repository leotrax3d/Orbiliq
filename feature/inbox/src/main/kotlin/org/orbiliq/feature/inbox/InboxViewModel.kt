package org.orbiliq.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.orbiliq.core.domain.*
import org.orbiliq.core.model.*

sealed interface MessengerState {
    data object Loading : MessengerState
    data class Ready(val snapshot: MessengerSnapshot) : MessengerState
    data object Unconfigured : MessengerState
    data object Error : MessengerState
}
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class InboxViewModel(repository: ConversationRepository) : ViewModel() {
    val conversations = repository.observeInbox().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val messenger = repository as? MessengerRepository
    private val reload = MutableStateFlow(0)
    val state = reload.flatMapLatest {
        messenger?.observeMessenger()?.map<MessengerSnapshot, MessengerState> { MessengerState.Ready(it) }
            ?.onStart { emit(MessengerState.Loading) }?.catch { emit(MessengerState.Error) }
            ?: flowOf(MessengerState.Unconfigured)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MessengerState.Loading)
    fun retry() { reload.value++ }
    fun timeline(ref: ConversationRef): Flow<UnifiedTimeline> =
        messenger?.observeTimeline(ConversationTarget.ProviderChat(ref))?.catch { _notice.value = "Verlauf konnte nicht geladen werden."; emit(UnifiedTimeline(emptyList(), emptyList())) } ?: flowOf(UnifiedTimeline(emptyList(), emptyList()))
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()
    fun clearNotice() { _notice.value = null }
    suspend fun textConstraints(route: SendRoute): OperationConstraints? = try {
        (messenger?.sendRoutes(ConversationTarget.ProviderChat(route.conversation.ref), "x", null)
            ?.find { it.route.conversation.ref == route.conversation.ref }?.availability as? Availability.Available)?.constraints
    } catch (c: CancellationException) { throw c }
      catch (_: Exception) { null }
    suspend fun canSend(route: SendRoute, text: String, reply: MessageId?): Boolean = try {
        messenger?.sendRoutes(ConversationTarget.ProviderChat(route.conversation.ref), text.ifBlank { " " }, reply)
            ?.any { it.route.conversation.ref == route.conversation.ref && it.availability is Availability.Available } == true
    } catch (c: CancellationException) { throw c }
      catch (_: Exception) { false }
    suspend fun send(route: SendRoute, text: String, reply: MessageId?): Boolean = try {
        when (messenger?.sendText(ConversationTarget.ProviderChat(route.conversation.ref), route, text.trim(), reply)) {
            is SendTextResult.Queued -> true
            else -> { _notice.value = "Versand nicht verfügbar. Entwurf bleibt erhalten."; false }
        }
    } catch (c: CancellationException) { throw c }
      catch (_: Exception) { _notice.value = "Nachricht konnte nicht gespeichert werden. Bitte erneut versuchen."; false }
    suspend fun canEdit(message: MessageId) = try { messenger?.canEdit(message) == true } catch (c: CancellationException) { throw c } catch (_: Exception) { false }
    suspend fun canDeleteForEveryone(message: MessageId) = try { messenger?.canDeleteForEveryone(message) == true } catch (c: CancellationException) { throw c } catch (_: Exception) { false }
    suspend fun edit(message: MessageId, text: String): Boolean = try {
        (messenger?.edit(message, text.trim()) == true).also { if (!it) _notice.value = "Bearbeiten nicht mehr möglich." }
    } catch (c: CancellationException) { throw c } catch (_: Exception) { _notice.value = "Bearbeiten fehlgeschlagen."; false }
    fun deleteForEveryone(message: MessageId) { viewModelScope.launch {
        val ok = try { messenger?.deleteForEveryone(message) == true } catch (c: CancellationException) { throw c } catch (_: Exception) { false }
        if (!ok) _notice.value = "Löschen für alle nicht mehr möglich."
    } }
    fun attachmentKind(attachment: AttachmentId): AttachmentKind? = try { messenger?.attachmentKind(attachment) } catch (_: Exception) { null }
    suspend fun loadAttachment(message: MessageId, attachment: AttachmentId): LoadedAttachment? = try { messenger?.loadAttachment(message, attachment) }
        catch (c: CancellationException) { throw c }
        catch (_: Exception) { null }
    suspend fun canSendFile(route: SendRoute): Boolean = try { messenger?.canSendFile(route) == true }
        catch (c: CancellationException) { throw c }
        catch (_: Exception) { false }
    suspend fun sendFile(route: SendRoute, uri: android.net.Uri, resolver: android.content.ContentResolver, caption: String?): Boolean = try {
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(64 * 1024)
                while (true) { val n = input.read(buffer); if (n < 0) break; out.write(buffer, 0, n); if (out.size() > 64 * 1024 * 1024) return@use null }
                out.toByteArray()
            }
        }
        when {
            mime.startsWith("image/") || mime.startsWith("video/") -> { _notice.value = "Bilder bitte über „Bild“ senden; Videoversand ist nicht verfügbar."; false }
            bytes == null || bytes.isEmpty() -> { _notice.value = "Datei ist leer oder größer als 64 MB."; false }
            else -> when (messenger?.sendFile(ConversationTarget.ProviderChat(route.conversation.ref), route, bytes, mime, name, caption?.trim()?.ifEmpty { null })) {
                is SendTextResult.Queued -> true
                else -> { _notice.value = "Dateiversand nicht verfügbar."; false }
            }
        }
    } catch (c: CancellationException) { throw c }
      catch (_: Exception) { _notice.value = "Datei konnte nicht gesendet werden."; false }
    suspend fun canSendImage(route: SendRoute): Boolean = try { messenger?.canSendImage(route) == true }
        catch (c: CancellationException) { throw c }
        catch (_: Exception) { false }
    /** Re-encodes to JPEG (max 2048 px), which drops EXIF/GPS metadata before anything leaves Orbiliq. */
    suspend fun sendImage(route: SendRoute, uri: android.net.Uri, resolver: android.content.ContentResolver, caption: String?): Boolean = try {
        val jpeg = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { cleanJpeg(uri, resolver) }
        if (jpeg == null) { _notice.value = "Bild konnte nicht gelesen werden."; false }
        else when (messenger?.sendImage(ConversationTarget.ProviderChat(route.conversation.ref), route, jpeg, caption?.trim()?.ifEmpty { null })) {
            is SendTextResult.Queued -> true
            else -> { _notice.value = "Bildversand nicht verf\u00fcgbar."; false }
        }
    } catch (c: CancellationException) { throw c }
      catch (_: Exception) { _notice.value = "Bild konnte nicht gesendet werden."; false }
    suspend fun loadImage(message: MessageId, attachment: AttachmentId): ByteArray? = try { messenger?.loadImage(message, attachment) }
        catch (c: CancellationException) { throw c }
        catch (_: Exception) { null }
    suspend fun canReact(message: MessageId): Boolean = try { messenger?.canReact(message) == true }
        catch (c: CancellationException) { throw c }
        catch (_: Exception) { false }
    fun react(message: MessageId, emoji: String) { viewModelScope.launch {
        val queued = try { messenger?.react(message, emoji) == true }
            catch (c: CancellationException) { throw c }
            catch (_: Exception) { false }
        if (!queued) _notice.value = "Reaktion nicht verfügbar."
    } }
    fun archive(id: ConversationId, archived: Boolean) { viewModelScope.launch {
        try { messenger?.archiveConversation(id, archived) }
        catch (c: CancellationException) { throw c }
        catch (_: Exception) { _notice.value = "Ordner konnte nicht geändert werden." }
    } }
}

internal fun cleanJpeg(uri: android.net.Uri, resolver: android.content.ContentResolver, maxEdge: Int = 2048): ByteArray? {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val scale = minOf(1f, maxEdge.toFloat() / maxOf(decoded.width, decoded.height))
    val bitmap = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1),
        (decoded.height * scale).toInt().coerceAtLeast(1), true).also { if (it != decoded) decoded.recycle() } else decoded
    return try { java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray() }
    finally { bitmap.recycle() }
}
