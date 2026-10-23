package dev.phillipslabs.comcord

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import de.connect2x.trixnity.messenger.MatrixMessenger
import de.connect2x.trixnity.messenger.viewmodel.RootRouter
import de.connect2x.trixnity.messenger.viewmodel.RootViewModel
import de.connect2x.trixnity.messenger.viewmodel.connecting.AddMatrixAccountMethod
import de.connect2x.trixnity.messenger.viewmodel.connecting.AddMatrixAccountState
import de.connect2x.trixnity.messenger.viewmodel.connecting.AddMatrixAccountViewModel
import de.connect2x.trixnity.messenger.viewmodel.connecting.PasswordLoginViewModel
import kotlinx.coroutines.flow.StateFlow

private val AppBg = Color(0xFF1A1D24)
private val GuildRailBg = Color(0xFF12141A)
private val ChannelPanelBg = Color(0xFF21252E)
private val ChatPanelBg = Color(0xFF2A2E38)
private val ComposerBg = Color(0xFF3A3F4B)
private val Accent = Color(0xFF4E69FF)
private val TextStrong = Color(0xFFE6E8EE)
private val TextMuted = Color(0xFFA7ACB7)

@Composable
fun App(
    rootViewModel: RootViewModel,
    matrixMessenger: MatrixMessenger,
) {
    val stackState = rootViewModel.stack.subscribeAsState()
    val route = stackState.value.active.instance

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AppBg,
        ) {
            when (route) {
                is RootRouter.Wrapper.AddMatrixAccount -> AddMatrixAccountScreen(route.viewModel)
                is RootRouter.Wrapper.PasswordLogin -> PasswordLoginScreen(route.viewModel)
                is RootRouter.Wrapper.Main -> ShellHost(matrixMessenger)
                else -> CenterStatus(routeStatus(route))
            }
        }
    }
}

@Composable
private fun CenterStatus(status: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = status, color = TextStrong, fontSize = 18.sp)
    }
}

@Composable
private fun AddMatrixAccountScreen(viewModel: AddMatrixAccountViewModel) {
    val serverField by viewModel.serverUrl.collectAsStateCompat()
    val discoveryState by viewModel.serverDiscoveryState.collectAsStateCompat()

    LaunchedEffect(serverField.text) {
        if (serverField.text.isBlank()) {
            viewModel.serverUrl.update("https://matrix.org")
        }
    }

    val passwordMethod =
        (discoveryState as? AddMatrixAccountViewModel.ServerDiscoveryState.Success)
            ?.addMatrixAccountMethods
            ?.filterIsInstance<AddMatrixAccountMethod.Password>()
            ?.firstOrNull()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Connect Matrix Account", color = TextStrong, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Choose a homeserver and continue with username/password login.",
            color = TextMuted,
        )

        OutlinedTextField(
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
            modifier = Modifier.fillMaxWidth(),
            value = serverField.text,
            onValueChange = { viewModel.serverUrl.update(it) },
            label = { Text("Homeserver URL") },
            singleLine = true,
        )

        Text(
            text =
                when (discoveryState) {
                    AddMatrixAccountViewModel.ServerDiscoveryState.Loading -> "Checking homeserver..."
                    is AddMatrixAccountViewModel.ServerDiscoveryState.Failure ->
                        (discoveryState as AddMatrixAccountViewModel.ServerDiscoveryState.Failure).message

                    is AddMatrixAccountViewModel.ServerDiscoveryState.Success ->
                        "Homeserver ready"

                    AddMatrixAccountViewModel.ServerDiscoveryState.None -> "Enter a homeserver"
                },
            color = TextMuted,
        )

        Button(
            enabled = passwordMethod != null,
            onClick = {
                val method = passwordMethod ?: return@Button
                viewModel.selectAddMatrixAccountMethod(method)
            },
        ) {
            Text("Continue with Password")
        }
    }
}

@Composable
private fun PasswordLoginScreen(viewModel: PasswordLoginViewModel) {
    val usernameField by viewModel.username.collectAsStateCompat()
    val passwordField by viewModel.password.collectAsStateCompat()
    val canLogin by viewModel.canLogin.collectAsStateCompat()
    val loginState by viewModel.addMatrixAccountState.collectAsStateCompat()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Password Login", color = TextStrong, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(viewModel.serverUrl, color = TextMuted)

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
            text =
                when (loginState) {
                    AddMatrixAccountState.None -> ""
                    AddMatrixAccountState.Connecting -> "Connecting..."
                    AddMatrixAccountState.Success -> "Connected"
                    is AddMatrixAccountState.Failure -> (loginState as AddMatrixAccountState.Failure).message
                },
            color = TextMuted,
        )
    }
}

@Composable
private fun ShellHost(matrixMessenger: MatrixMessenger) {
    val sources = remember(matrixMessenger) { matrixMessenger.accountSources() }
    val source by sources.collectAsState(initial = null)
    val accountSource = source
    if (accountSource == null) {
        CenterStatus("No active account")
    } else {
        key(accountSource) {
            val scope = rememberCoroutineScope()
            val store = remember(accountSource) { AccountStore(accountSource, scope) }
            MainShell(store)
        }
    }
}

@Composable
private fun MainShell(store: AccountStore) {
    val state by store.state.collectAsState()
    val roomDirectory = state.directory
    val roomsForGuild = state.rooms
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 900.dp

        Row(modifier = Modifier.fillMaxSize()) {
            GuildRail(
                compact = compact,
                guilds = roomDirectory.guilds,
                selectedGuildId = state.selectedGuildId,
                onSelectGuild = store::selectGuild,
                onLogout = store::logout,
            )
            if (!compact) {
                ChannelPanel(
                    status = roomDirectory.syncStateLabel,
                    channels = roomsForGuild,
                    selectedRoomId = state.selectedRoomId,
                    onSelectRoom = { store.selectRoom(it.id) },
                )
            }
            ChatPanel(
                selectedChannel = state.selectedRoom?.name ?: "Select a room",
                roomsCount = roomsForGuild.size,
                syncStateLabel = roomDirectory.syncStateLabel,
            )
        }
    }
}

@Composable
private fun GuildRail(
    compact: Boolean,
    guilds: List<GuildUi>,
    selectedGuildId: String,
    onSelectGuild: (String) -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(if (compact) 56.dp else 72.dp)
                .background(GuildRailBg)
                .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        guilds.forEach { guild ->
            val label = guild.name.take(2).uppercase()
            GuildDot(
                label = label,
                active = guild.id == selectedGuildId,
                onClick = { onSelectGuild(guild.id) },
            )
        }
        Spacer(Modifier.weight(1f))
        GuildDot(
            label = "LO",
            active = false,
            onClick = onLogout,
        )
    }
}

@Composable
private fun GuildDot(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(if (active) 16.dp else 24.dp))
                .background(if (active) Accent else Color(0xFF2A2D36))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = TextStrong, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ChannelPanel(
    status: String,
    channels: List<RoomUi>,
    selectedRoomId: String?,
    onSelectRoom: (RoomUi) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(280.dp)
                .background(ChannelPanelBg)
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text("Comcord", color = TextStrong, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        StatusBadge(status)
        Spacer(modifier = Modifier.height(16.dp))
        Text("ROOMS", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            items(channels.size) { index ->
                val channel = channels[index]
                ChannelRow(
                    roomName = channel.name,
                    selected = selectedRoomId == channel.id,
                    onClick = { onSelectRoom(channel) },
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2D3340))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Accent),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = status, color = TextStrong, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ChannelRow(
    roomName: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) Color(0xFF343A47) else Color(0xFF2A2E37))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#$roomName",
            color = TextStrong,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChatPanel(
    selectedChannel: String,
    roomsCount: Int,
    syncStateLabel: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(ChatPanelBg)
                .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(text = selectedChannel, color = TextStrong, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(14.dp))
        Text("Synced rooms in view: $roomsCount", color = TextMuted)
        Spacer(modifier = Modifier.height(8.dp))
        Text("State: $syncStateLabel", color = TextMuted)
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ComposerBg)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(text = "Timeline view next", color = TextMuted)
        }
    }
}

@Composable
private fun <T> StateFlow<T>.collectAsStateCompat() = collectAsState()

private fun routeStatus(route: RootRouter.Wrapper): String =
    when (route) {
        is RootRouter.Wrapper.AddMatrixAccount -> "Waiting for account setup"
        is RootRouter.Wrapper.Main -> "Connected"
        is RootRouter.Wrapper.MatrixClientInitialization -> "Initializing Matrix client"
        is RootRouter.Wrapper.MatrixClientInitializationFailure -> "Client initialization failed"
        RootRouter.Wrapper.None -> "Ready"
        is RootRouter.Wrapper.OAuth2AuthorizationCodeLogin -> "OAuth2 login available"
        is RootRouter.Wrapper.OAuth2DeviceAuthorizationLogin -> "OAuth2 device login available"
        is RootRouter.Wrapper.PasswordLogin -> "Password login required"
        is RootRouter.Wrapper.RegisterMatrixAccount -> "Registration available"
        is RootRouter.Wrapper.RemoveMatrixAccount -> "Removing account"
        is RootRouter.Wrapper.SSOLogin -> "SSO login available"
    }
