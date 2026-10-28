package org.orbiliq.app

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.orbiliq.core.provider.signal.SignalBridgeContract
import org.orbiliq.core.provider.signal.SignalLinkState

/** Local IPC only. Requires the separately built, equally signed companion, no Signal account.
 * Does not approve access, scan a QR, revoke user consent or send Signal traffic.
 */
class SignalCompanionIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val uri = Uri.parse("content://${SignalBridgeContract.AUTHORITY}")

    private fun requireCompanion() {
        val pm = context.packageManager
        val provider = pm.resolveContentProvider(SignalBridgeContract.AUTHORITY, 0)
        assumeTrue("Separate companion not installed", provider != null)
        assertEquals(SignalBridgeContract.PACKAGE, provider!!.packageName)
        assertEquals(PackageManager.SIGNATURE_MATCH, pm.checkSignatures(context.packageName, provider.packageName))
    }

    @Test fun statusContainsOnlyVersionLocalStateAndEmptyCapabilities() {
        requireCompanion()
        val result = context.contentResolver.call(uri, "status", null,
            Bundle().apply { putInt("protocolVersion", SignalBridgeContract.VERSION) })!!
        assertEquals(setOf("protocolVersion", "linkState", "capabilities"), result.keySet())
        assertEquals(SignalBridgeContract.VERSION, result.getInt("protocolVersion"))
        assertTrue(SignalLinkState.entries.any { it.name == result.getString("linkState") })
        assertEquals(0, result.getStringArray("capabilities")!!.size)
    }

    @Test fun incompatibleVersionDoesNotExposeAccountState() {
        requireCompanion()
        val result = context.contentResolver.call(uri, "status", null,
            Bundle().apply { putInt("protocolVersion", Int.MAX_VALUE) })!!
        assertEquals(SignalLinkState.UpdateRequired.name, result.getString("linkState"))
        assertEquals(setOf("protocolVersion", "linkState", "capabilities"), result.keySet())
    }

    @Test fun genericQueryCannotReadCompanionStorage() {
        requireCompanion()
        try {
            context.contentResolver.query(uri, null, null, null, null)?.close()
            fail("Query access must be rejected")
        } catch (_: SecurityException) { }
    }

    /** Opt-in real-device assertion. Never reads an account ID, QR or key material. */
    @Test fun localLinkStateMatchesExplicitHardwareExpectation() {
        val expected = InstrumentationRegistry.getArguments().getString("expectedSignalLinkState")
        assumeTrue("No explicit hardware link-state expectation", expected != null)
        requireCompanion()
        require(SignalLinkState.entries.any { it.name == expected })
        // Android may create ContentProviders before Application.onCreate finishes
        // initializing SignalStore. Only tolerate this explicitly unavailable state;
        // never retry away a revoked, untrusted or incompatible state.
        val deadline = SystemClock.elapsedRealtime() + 15_000
        var status: org.orbiliq.core.provider.signal.SignalCompanionStatus
        do {
            val result = context.contentResolver.call(uri, "status", null,
                Bundle().apply { putInt("protocolVersion", SignalBridgeContract.VERSION) })!!
            status = SignalBridgeContract.decodeStatus(result.getInt("protocolVersion", -1),
                result.getString("linkState"), result.getStringArray("capabilities")?.toList())
            if (status.link != SignalLinkState.Unavailable || SystemClock.elapsedRealtime() >= deadline) break
            SystemClock.sleep(200)
        } while (true)
        assertEquals("Local linking state differs from explicit hardware expectation", expected, status.link.name)
    }
}
