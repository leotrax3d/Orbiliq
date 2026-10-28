// SPDX-License-Identifier: MIT
package org.signal.registration

/** Restored state and navigation must never expose primary-account registration. */
internal object CompanionRegistrationPolicy {
  fun allows(route: RegistrationRoute): Boolean = when (route) {
    is RegistrationRoute.LinkAccount -> !route.showCreateAccount
    is RegistrationRoute.MessageSync, is RegistrationRoute.FullyComplete -> true
    is RegistrationRoute.AllowNotifications -> allows(route.nextRoute)
    else -> false
  }
}
