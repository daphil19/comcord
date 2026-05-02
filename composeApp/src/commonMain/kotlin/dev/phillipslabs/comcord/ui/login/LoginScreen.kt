package dev.phillipslabs.comcord.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.connect2x.trixnity.messenger.viewmodel.connecting.AddMatrixAccountViewModel
import de.connect2x.trixnity.messenger.viewmodel.connecting.PasswordLoginViewModel
import dev.phillipslabs.comcord.account.LoginPresenter
import dev.phillipslabs.comcord.account.collectAsStateCompat
import dev.phillipslabs.comcord.design.ComcordColors

@Composable
fun RouteStatusScreen(
    status: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = ComcordColors.AppBackground,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = status, color = ComcordColors.TextStrong, fontSize = 18.sp)
        }
    }
}

@Composable
fun AddMatrixAccountScreen(
    viewModel: AddMatrixAccountViewModel,
    modifier: Modifier = Modifier,
    presenter: LoginPresenter = LoginPresenter(),
) {
    val serverField by viewModel.serverUrl.collectAsStateCompat()
    val discoveryState by viewModel.serverDiscoveryState.collectAsStateCompat()

    LaunchedEffect(serverField.text) {
        if (serverField.text.isBlank()) {
            viewModel.serverUrl.update("https://matrix.org")
        }
    }

    val uiState = presenter.addAccountState(viewModel)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = ComcordColors.AppBackground,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Connect Matrix Account", color = ComcordColors.TextStrong, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Choose a homeserver and continue with username/password login.",
                color = ComcordColors.TextMuted,
            )

            OutlinedTextField(
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                modifier = Modifier.fillMaxWidth(),
                value = serverField.text,
                onValueChange = { viewModel.serverUrl.update(it) },
                label = { Text("Homeserver URL") },
                singleLine = true,
            )

            Text(
                text = uiState.statusLabel,
                color = ComcordColors.TextMuted,
            )

            Button(
                enabled = uiState.canContinue,
                onClick = {
                    uiState.continueWithPassword?.invoke()
                },
            ) {
                Text("Continue with Password")
            }
        }
    }
}

@Composable
fun PasswordLoginScreen(
    viewModel: PasswordLoginViewModel,
    modifier: Modifier = Modifier,
    presenter: LoginPresenter = LoginPresenter(),
) {
    val usernameField by viewModel.username.collectAsStateCompat()
    val passwordField by viewModel.password.collectAsStateCompat()
    val canLogin by viewModel.canLogin.collectAsStateCompat()
    val loginState by viewModel.addMatrixAccountState.collectAsStateCompat()
    val uiState = presenter.passwordLoginState(viewModel)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = ComcordColors.AppBackground,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Password Login", color = ComcordColors.TextStrong, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(uiState.serverUrl, color = ComcordColors.TextMuted)

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = usernameField.text,
                onValueChange = { viewModel.username.update(it) },
                label = { Text("Username") },
                singleLine = true,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = passwordField.text,
                onValueChange = { viewModel.password.update(it) },
                label = { Text("Password") },
                singleLine = true,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = viewModel::back) { Text("Back") }
                Button(enabled = canLogin, onClick = viewModel::tryLogin) { Text("Log In") }
            }

            Text(
                text = uiState.statusLabel,
                color = ComcordColors.TextMuted,
            )
        }
    }
}
