package dev.phillipslabs.comcord.bootstrap

import de.connect2x.trixnity.messenger.MatrixMessenger
import de.connect2x.trixnity.messenger.viewmodel.RootViewModel
import dev.phillipslabs.comcord.account.AccountSessionStore
import dev.phillipslabs.comcord.navigation.AppNavigator

data class ComcordRootComponent(
    val rootViewModel: RootViewModel,
    val matrixMessenger: MatrixMessenger,
    val sessionStore: AccountSessionStore,
    val appNavigator: AppNavigator,
)
