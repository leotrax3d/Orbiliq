// SPDX-License-Identifier: AGPL-3.0-or-later
package org.signal.network.api

import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.libsignal.net.RequestResult
import org.signal.network.NetworkResult
import org.signal.network.rest.SignalRestClient
import org.whispersystems.signalservice.api.registration.RegistrationApi
import org.whispersystems.signalservice.internal.push.PushServiceSocket

/** Real API methods with strict mocks: no account, keys, SMS or network needed. */
class CompanionRegistrationBoundaryTest {
  @Test fun primarySessionCannotBeCreated() = runTest {
    val transport = mockk<SignalRestClient>()
    val api = RegistrationApiV2(transport, false)
    assertTrue(api.createVerificationSession("+12025550123", null, null, null) is RequestResult.ApplicationError)
    confirmVerified(transport)
  }

  @Test fun primarySmsAndVerificationCannotBeSubmitted() = runTest {
    val transport = mockk<SignalRestClient>()
    val api = RegistrationApiV2(transport, false)
    assertTrue(api.requestVerificationCode("synthetic", null, false, RegistrationApiV2.VerificationCodeTransport.SMS) is RequestResult.ApplicationError)
    assertTrue(api.submitVerificationCode("synthetic", "000000") is RequestResult.ApplicationError)
    confirmVerified(transport)
  }

  @Test fun primaryRegistrationCannotReachHttpEvenWithRecoveryCredentials() = runTest {
    val transport = mockk<SignalRestClient>()
    val api = RegistrationApiV2(transport, true)
    val result = api.registerAccount(null, "synthetic", null, "synthetic-recovery", null,
      mockk(), mockk(), null, null, false)
    assertTrue(result is RequestResult.ApplicationError)
    confirmVerified(transport)
  }

  @Test fun legacyPrimaryApiCannotReachSocket() {
    val socket = mockk<PushServiceSocket>()
    val api = RegistrationApi(socket)
    assertTrue(api.createRegistrationSession(null, null, null) is NetworkResult.ApplicationError)
    assertTrue(api.registerAccount(null, null, null, null, null, null, false) is NetworkResult.ApplicationError)
    confirmVerified(socket)
  }
}
