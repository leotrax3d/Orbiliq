package org.orbiliq.app

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.orbiliq.app.signal.AndroidSignalMessaging

/** Opt-in: names only the failing read operations, never contents, ids or bindings. */
class SignalTransportDiagnosticTest {
    @Test fun everyReadOperationSucceedsForTheConsentedAccount() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("signalDiagnose") == "true")
        val peer = AndroidSignalMessaging(InstrumentationRegistry.getInstrumentation().targetContext)
        val failures = mutableListOf<String>()
        val binding = peer.binding() ?: return@runBlocking assertEquals("binding", "missing")
        val threads = try { peer.threads(binding) } catch (_: Exception) { failures += "threads"; emptyList() }
        var pages = 0
        for (thread in threads) {
            try {
                val page = peer.texts(binding, thread.id)
                pages++
                if (page.rows.isNotEmpty()) peer.lookup(binding, thread.id, page.rows.map { it.id })
            } catch (_: Exception) { failures += "texts:${if (thread.direct) "direct" else "group"}" }
        }
        assertEquals("pages=$pages", emptyList<String>(), failures.distinct())
    }
}
