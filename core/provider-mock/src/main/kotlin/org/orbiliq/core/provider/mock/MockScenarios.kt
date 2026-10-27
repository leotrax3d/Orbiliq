package org.orbiliq.core.provider.mock

import java.time.Duration
import org.orbiliq.core.model.*
import org.orbiliq.core.provider.api.*

/** Reusable deterministic scripts for previews, integration and regression tests.
 * Start with an authenticated Full session. Returned failures are intentional evidence.
 */
object MockScenarios {
    enum class Scenario { Conversation, Media, OfflineRecovery, UnknownDelivery, RevisionConflict, Capabilities, SyncGap }
    suspend fun run(session: MockProviderSession, scenario: Scenario): List<DispatchResult> {
        require(session.state.value.auth == AuthState.Ready)
        val results = mutableListOf<DispatchResult>()
        suspend fun execute(payload: CommandPayload): DispatchResult = session.execute(session.newCommand(payload)).also { results += it }
        when (scenario) {
            Scenario.Conversation -> {
                session.incomingTyping()
                session.receiveText(text = "Hast du Zeit für einen Test?", delay = Duration.ofSeconds(2))
                session.advanceBy(Duration.ofSeconds(3))
                val sent = execute(CommandPayload.SendText("Ja, der lokale Messenger funktioniert.")) as DispatchResult.Confirmed
                val ref = sent.remoteMessage!!
                execute(CommandPayload.SendText("Antwort mit Bezug", ref))
                execute(CommandPayload.React(ref, "👍"))
                session.incomingReceipt(ref, DeliveryState.Delivered)
                session.incomingReceipt(ref, DeliveryState.Read, Duration.ofSeconds(2))
                session.advanceBy(Duration.ofSeconds(2))
            }
            Scenario.Media -> AttachmentKind.entries.forEach {
                session.receiveMedia(it, byteArrayOf(0x4f, 0x72, 0x62), "application/x-orbiliq-fixture")
            }
            Scenario.OfflineRecovery -> {
                session.setOnline(false)
                session.receiveText(text = "Während der Offline-Phase eingetroffen")
                execute(CommandPayload.SendText("Offline-Versuch"))
                session.advanceBy(Duration.ofSeconds(5)); session.setOnline(true)
                execute(CommandPayload.SendText("Nach Wiederverbindung gesendet"))
            }
            Scenario.UnknownDelivery -> {
                val command = session.newCommand(CommandPayload.SendText("Antwort nach Versand verloren"))
                session.failNext(MockFault.UnknownAfterCommit)
                results += session.execute(command)
                check(session.reconcile(ReconcileRequest(command.id, command.route)) is ReconcileResult.Confirmed)
                results += session.execute(command)
            }
            Scenario.RevisionConflict -> {
                val ref = (execute(CommandPayload.SendText("Original")) as DispatchResult.Confirmed).remoteMessage!!
                session.remoteEdit(ref, "Parallel auf anderem Gerät bearbeitet")
                execute(CommandPayload.EditText(ref, "Veraltete lokale Bearbeitung", "1"))
            }
            Scenario.Capabilities -> {
                session.setProfile(MockProfile.ReadOnly)
                execute(CommandPayload.SendText("Nicht unterstützt"))
                session.setProfile(MockProfile.TextOnly)
                execute(CommandPayload.SetTyping(true))
                session.setProfile(MockProfile.Full)
                execute(CommandPayload.SendText("Wieder freigegeben"))
            }
            Scenario.SyncGap -> { session.simulateSyncGap(); session.resync() }
        }
        return results
    }
}
