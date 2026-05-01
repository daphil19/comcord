package dev.phillipslabs.comcord.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.connect2x.trixnity.messenger.viewmodel.connecting.AddMatrixAccountMethod
import de.connect2x.trixnity.messenger.viewmodel.connecting.AddMatrixAccountState
import de.connect2x.trixnity.messenger.viewmodel.connecting.AddMatrixAccountViewModel
import de.connect2x.trixnity.messenger.viewmodel.connecting.PasswordLoginViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class AddAccountUiState(
    val serverUrl: String,
    val statusLabel: String,
    val canContinue: Boolean,
    val continueWithPassword: (() -> Unit)?,
)

data class PasswordLoginUiState(
    val serverUrl: String,
    val username: String,
    val password: String,
    val canLogin: Boolean,
    val statusLabel: String,
)

class LoginPresenter {
    fun addAccountState(viewModel: AddMatrixAccountViewModel): AddAccountUiState {
        val serverUrl = viewModel.serverUrl.value.text
        val discoveryState = viewModel.serverDiscoveryState.value
        val passwordMethod =
            (discoveryState as? AddMatrixAccountViewModel.ServerDiscoveryState.Success)
                ?.addMatrixAccountMethods
                ?.filterIsInstance<AddMatrixAccountMethod.Password>()
                ?.firstOrNull()
        val directPasswordFallback =
            if (passwordMethod == null &&
                discoveryState is AddMatrixAccountViewModel.ServerDiscoveryState.Failure &&
                (serverUrl.startsWith("http://") || serverUrl.startsWith("https://"))
            ) {
                AddMatrixAccountMethod.Password(serverUrl)
            } else {
                null
            }
        val selectedPasswordMethod = passwordMethod ?: directPasswordFallback

        return AddAccountUiState(
            serverUrl = serverUrl,
            statusLabel =
                when (discoveryState) {
                    AddMatrixAccountViewModel.ServerDiscoveryState.Loading -> "Checking homeserver..."
                    is AddMatrixAccountViewModel.ServerDiscoveryState.Failure -> discoveryState.message
                    is AddMatrixAccountViewModel.ServerDiscoveryState.Success -> "Homeserver ready"
                    AddMatrixAccountViewModel.ServerDiscoveryState.None -> "Enter a homeserver"
                },
            canContinue = selectedPasswordMethod != null,
            continueWithPassword =
                selectedPasswordMethod?.let { method ->
                    { viewModel.selectAddMatrixAccountMethod(method) }
                },
        )
    }

    fun passwordLoginState(viewModel: PasswordLoginViewModel): PasswordLoginUiState =
        PasswordLoginUiState(
            serverUrl = viewModel.serverUrl,
            username = viewModel.username.value.text,
            password = viewModel.password.value.text,
            canLogin = viewModel.canLogin.value,
            statusLabel =
                when (val loginState = viewModel.addMatrixAccountState.value) {
                    AddMatrixAccountState.None -> ""
                    AddMatrixAccountState.Connecting -> "Connecting..."
                    AddMatrixAccountState.Success -> "Connected"
                    is AddMatrixAccountState.Failure -> loginState.message
                },
        )
}

@Composable
internal fun <T> StateFlow<T>.collectAsStateCompat(): State<T> = collectAsStateWithLifecycle()

@Composable
internal fun <T> Flow<T>.collectAsStateCompat(initial: T): State<T> = collectAsStateWithLifecycle(initialValue = initial)
