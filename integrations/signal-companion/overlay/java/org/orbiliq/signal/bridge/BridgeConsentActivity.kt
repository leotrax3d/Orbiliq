/* SPDX-License-Identifier: MIT */
package org.orbiliq.signal.bridge

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import android.widget.Toast
import org.orbiliq.core.provider.signal.SignalBridgeContract
import org.signal.registration.RegistrationActivity
import org.signal.registration.RegistrationRoute
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.service.KeyCachingService

/** System-provided callingPackage from startActivityForResult, never an Intent extra. */
class BridgeConsentActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    val access = BridgeAccess(this)
    try {
      if (callingPackage != SignalBridgeContract.CLIENT_PACKAGE) throw SecurityException()
      @Suppress("DEPRECATION")
      val uid = packageManager.getApplicationInfo(requireNotNull(callingPackage), 0).uid
      access.enforceUid(uid)
    } catch (_: Exception) {
      finish()
      return
    }
    val layout = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      val padding = (24 * resources.displayMetrics.density).toInt()
      setPadding(padding, padding, padding, padding)
    }
    layout.addView(TextView(this).apply {
      text = "Orbiliq Signal Companion · experimentell\n\n" +
        "Orbiliq darf nach deiner Zustimmung den lokalen Verknüpfungsstatus abfragen. " +
        "Nachrichten werden noch nicht an Orbiliq übertragen. Schlüssel und QR-Daten bleiben hier. " +
        "Verknüpfe ein zusätzliches Gerät; registriere deine Telefonnummer nicht erneut. " +
        "Für den QR-Scan benötigst du gegebenenfalls einen zweiten Bildschirm."
      textSize = 18f
    })
    fun button(label: String, action: () -> Unit) {
      layout.addView(Button(this).apply {
        text = label
        filterTouchesWhenObscured = true
        setOnClickListener { action() }
      })
    }
    button("Statuszugriff erlauben") {
      if (KeyCachingService.isLocked(this)) {
        startActivity(Intent(this, MainActivity::class.java))
      } else {
        try {
          access.approve()
          setResult(RESULT_OK)
          finish()
        } catch (_: java.io.IOException) {
          Toast.makeText(this, "Zustimmung konnte nicht gespeichert werden.", Toast.LENGTH_LONG).show()
        }
      }
    }
    button("Gerät verknüpfen / Companion öffnen") {
      val intent = if (SignalStore.account.isRegistered || KeyCachingService.isLocked(this)) {
        Intent(this, MainActivity::class.java)
      } else {
        RegistrationActivity.createIntent(this, startDestination = RegistrationRoute.LinkAccount(showCreateAccount = false))
      }
      startActivity(intent)
    }
    button("Orbiliq-Zugriff entziehen") { access.revoke(); setResult(RESULT_CANCELED); finish() }
    button("Abbrechen") { finish() }
    val scroll = ScrollView(this).apply { addView(layout) }
    scroll.setOnApplyWindowInsetsListener { view, insets ->
      @Suppress("DEPRECATION")
      view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
      insets
    }
    setContentView(scroll)
  }
}
