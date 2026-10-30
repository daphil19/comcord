package dev.phillipslabs.comcord

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.MatrixClientInitializationException
import de.connect2x.trixnity.messenger.viewmodel.TextFieldViewModel
import de.connect2x.trixnity.messenger.viewmodel.connecting.MatrixClientInitializationFailureViewModel
import de.connect2x.trixnity.messenger.viewmodel.uia.UiaRouter
import kotlinx.coroutines.flow.StateFlow

@Composable
fun InitializationFailureScreen(
    viewModel: MatrixClientInitializationFailureViewModel,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmRemoval by remember(viewModel) { mutableStateOf(false) }
    val failure = viewModel.initializationException
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Could not restore Account", style = MaterialTheme.typography.headlineSmall)
        Text(viewModel.userId.full)
        when (failure) {
            is MatrixClientInitializationException.DatabaseCannotBeDecryptedException -> {
                Text(
                    "The local Account data could not be decrypted and has been removed. " +
                        "Sign in again; restoring encrypted history may require verification or your recovery key.",
                )
                Button(onClick = viewModel::confirmDeletion) { Text("Continue to sign in") }
            }

            is MatrixClientInitializationException.DatabaseKeysManipulatedException -> {
                Text(
                    "The local storage keys have changed unexpectedly. " +
                        "Continuing removes this local Account data for all signed-in Accounts. " +
                        "Your Homeserver Account is not deleted.",
                )
                if (confirmRemoval) {
                    Button(onClick = viewModel::confirmDeletion) { Text("Confirm remove local Account data") }
                } else {
                    Button(onClick = { confirmRemoval = true }) { Text("Remove local Account data…") }
                }
            }

            else -> {
                Text(
                    "Close other Comcord windows and check that local storage is available, then retry. " +
                        "Retry keeps your saved Account data.",
                )
                Button(onClick = onRetry) { Text("Retry") }
                if (viewModel.deleteEnabled) {
                    Text(
                        "If retry does not help, removing this local Account lets you sign in again. " +
                            "Local keys and cached history will be lost; your Homeserver Account is kept.",
                    )
                    if (confirmRemoval) {
                        Button(onClick = viewModel::delete) { Text("Confirm remove local Account") }
                    } else {
                        Button(onClick = { confirmRemoval = true }) { Text("Remove local Account…") }
                    }
                }
            }
        }
    }
}

@Composable
fun AuthenticationChallenge(
    route: UiaRouter.Wrapper,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Confirm Account action", style = MaterialTheme.typography.headlineSmall)
        when (route) {
            UiaRouter.Wrapper.None -> {
                Unit
            }

            is UiaRouter.Wrapper.UiaActionConfirmation -> {
                val vm = route.viewModel
                Text(vm.confirmationMessage ?: "Authorizing…")
                ChallengeActions(vm.isPerformingAction, vm.error, vm::next, vm::cancel)
            }

            is UiaRouter.Wrapper.UiaStepPassword -> {
                val vm = route.viewModel
                ChallengeField("Username", vm.username)
                ChallengeField("Password", vm.password, secret = true)
                ChallengeActions(vm.isSubmitting, vm.error, vm::submit, vm::cancel)
            }

            is UiaRouter.Wrapper.UiaStepDummy -> {
                val vm = route.viewModel
                Text("Authorizing Account action…")
                ChallengeActions(vm.isLoading, vm.error, vm::next, vm::cancel)
            }

            is UiaRouter.Wrapper.UiaStepRegistrationToken -> {
                val vm = route.viewModel
                ChallengeField("Registration token", vm.registrationToken, secret = true)
                ChallengeActions(vm.isSubmitting, vm.error, vm::submit, vm::cancel)
            }

            is UiaRouter.Wrapper.UiaStepFallback -> {
                val vm = route.viewModel
                Text("Complete this Homeserver challenge in your browser:")
                Text(vm.fallbackUrl)
                val waiting by vm.waitForResult.collectAsState()
                val error by vm.error.collectAsState()
                error?.let { Text(it) }
                Button(enabled = !waiting, onClick = vm::openFallbackUrl) { Text("Open Homeserver challenge") }
                Button(enabled = !waiting, onClick = vm::confirm) { Text("Check completion") }
                Button(onClick = vm::cancel) { Text("Cancel") }
            }

            is UiaRouter.Wrapper.UiaStepEmailIdentity -> {
                val error by route.viewModel.error.collectAsState()
                Text(error ?: "This Homeserver email challenge is not supported.")
                Button(onClick = route.viewModel::cancel) { Text("Cancel") }
            }

            is UiaRouter.Wrapper.UiaStepMsisdn -> {
                val error by route.viewModel.error.collectAsState()
                Text(error ?: "This Homeserver phone challenge is not supported.")
                Button(onClick = route.viewModel::cancel) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun ChallengeField(
    label: String,
    field: TextFieldViewModel,
    secret: Boolean = false,
) {
    val value by field.collectAsState()
    OutlinedTextField(
        value = value.text,
        onValueChange = field::update,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions =
            KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text,
            ),
    )
}

@Composable
private fun ChallengeActions(
    busy: StateFlow<Boolean>,
    errors: StateFlow<String?>,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val working by busy.collectAsState()
        val error by errors.collectAsState()
        error?.let { Text(it) }
        Button(enabled = !working, onClick = onContinue) { Text(if (working) "Authorizing…" else "Continue") }
        Button(onClick = onCancel) { Text("Cancel") }
    }
}
