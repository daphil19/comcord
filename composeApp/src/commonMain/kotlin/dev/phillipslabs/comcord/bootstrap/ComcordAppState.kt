package dev.phillipslabs.comcord.bootstrap

import de.connect2x.trixnity.messenger.MatrixClients
import de.connect2x.trixnity.messenger.MatrixMessenger
import de.connect2x.trixnity.messenger.createRoot
import dev.phillipslabs.comcord.account.AccountSessionStore
import dev.phillipslabs.comcord.navigation.AppNavigator

typealias ComcordAppState = ComcordRootComponent

fun createComcordAppState(matrixMessenger: MatrixMessenger): ComcordAppState =
    ComcordRootComponent(
        matrixMessenger = matrixMessenger,
        rootViewModel = matrixMessenger.createRoot(),
        sessionStore = AccountSessionStore(matrixMessenger.di.get<MatrixClients>()),
        appNavigator = AppNavigator(),
    )
