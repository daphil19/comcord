package dev.phillipslabs.comcord

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import dev.phillipslabs.comcord.bootstrap.ComcordRootComponent
import dev.phillipslabs.comcord.design.ComcordTheme
import dev.phillipslabs.comcord.navigation.toAppDestination
import dev.phillipslabs.comcord.ui.login.AddMatrixAccountScreen
import dev.phillipslabs.comcord.ui.login.PasswordLoginScreen
import dev.phillipslabs.comcord.ui.login.RouteStatusScreen
import dev.phillipslabs.comcord.ui.shell.ComcordShell

@Composable
fun App(rootComponent: ComcordRootComponent) {
    val route =
        rootComponent.rootViewModel.stack
            .subscribeAsState()
            .value.active.instance
    val destination = route.toAppDestination()

    ComcordTheme {
        when (destination) {
            is dev.phillipslabs.comcord.navigation.AppDestination.AddAccount -> {
                AddMatrixAccountScreen(destination.viewModel)
            }

            is dev.phillipslabs.comcord.navigation.AppDestination.PasswordLogin -> {
                PasswordLoginScreen(destination.viewModel)
            }

            dev.phillipslabs.comcord.navigation.AppDestination.Main -> {
                ComcordShell(rootComponent)
            }

            is dev.phillipslabs.comcord.navigation.AppDestination.Status -> {
                RouteStatusScreen(destination.label)
            }
        }
    }
}
