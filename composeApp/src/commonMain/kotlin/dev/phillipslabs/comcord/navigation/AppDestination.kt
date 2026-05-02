package dev.phillipslabs.comcord.navigation

import de.connect2x.trixnity.messenger.viewmodel.RootRouter
import de.connect2x.trixnity.messenger.viewmodel.connecting.AddMatrixAccountViewModel
import de.connect2x.trixnity.messenger.viewmodel.connecting.PasswordLoginViewModel

sealed interface AppDestination {
    data class AddAccount(
        val viewModel: AddMatrixAccountViewModel,
    ) : AppDestination

    data class PasswordLogin(
        val viewModel: PasswordLoginViewModel,
    ) : AppDestination

    data object Main : AppDestination

    data class Status(
        val label: String,
    ) : AppDestination
}

fun RootRouter.Wrapper.toAppDestination(): AppDestination =
    when (this) {
        is RootRouter.Wrapper.AddMatrixAccount -> AppDestination.AddAccount(viewModel)
        is RootRouter.Wrapper.PasswordLogin -> AppDestination.PasswordLogin(viewModel)
        is RootRouter.Wrapper.Main -> AppDestination.Main
        is RootRouter.Wrapper.MatrixClientInitialization -> AppDestination.Status("Initializing Matrix client")
        is RootRouter.Wrapper.MatrixClientInitializationFailure -> AppDestination.Status("Client initialization failed")
        RootRouter.Wrapper.None -> AppDestination.Status("Ready")
        is RootRouter.Wrapper.OAuth2Login -> AppDestination.Status("OAuth2 login available")
        is RootRouter.Wrapper.RegisterMatrixAccount -> AppDestination.Status("Registration available")
        is RootRouter.Wrapper.RemoveMatrixAccount -> AppDestination.Status("Removing account")
        is RootRouter.Wrapper.SSOLogin -> AppDestination.Status("SSO login available")
    }
