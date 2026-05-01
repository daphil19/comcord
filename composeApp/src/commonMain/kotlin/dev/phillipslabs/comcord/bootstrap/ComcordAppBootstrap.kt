package dev.phillipslabs.comcord.bootstrap

import de.connect2x.trixnity.messenger.MatrixClients
import de.connect2x.trixnity.messenger.MatrixMessenger
import de.connect2x.trixnity.messenger.createRoot

suspend fun createComcordAppBootstrap(
    createMatrixMessenger: suspend () -> MatrixMessenger,
): ComcordRootComponent {
    val matrixMessenger = createMatrixMessenger()
    val matrixClients = matrixMessenger.di.get<MatrixClients>()

    return ComcordRootComponent(
        rootViewModel = matrixMessenger.createRoot(),
        matrixMessenger = matrixMessenger,
        sessionStore = dev.phillipslabs.comcord.account.AccountSessionStore(matrixClients),
        appNavigator = dev.phillipslabs.comcord.navigation.AppNavigator(),
    )
}
