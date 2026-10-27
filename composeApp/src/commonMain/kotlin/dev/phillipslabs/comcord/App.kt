package dev.phillipslabs.comcord

import androidx.compose.foundation.Image
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import org.jetbrains.compose.resources.decodeToImageBitmap

private val AppBg = Color(0xFF1A1D24)
private val ServerRailBg = Color(0xFF12141A)
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
    modifier: Modifier = Modifier,
) {
    val stackState = rootViewModel.stack.subscribeAsState()
    val route = stackState.value.active.instance

    MaterialTheme {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = AppBg,
        ) {
            when (route) {
                is RootRouter.Wrapper.AddMatrixAccount -> AddMatrixAccountScreen(route.viewModel)
                is RootRouter.Wrapper.PasswordLogin -> PasswordLoginScreen(route.viewModel)
                is RootRouter.Wrapper.Main -> ActiveAccountScreen(matrixMessenger)
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
                    AddMatrixAccountViewModel.ServerDiscoveryState.Loading -> {
                        "Checking homeserver..."
                    }

                    is AddMatrixAccountViewModel.ServerDiscoveryState.Failure -> {
                        (discoveryState as AddMatrixAccountViewModel.ServerDiscoveryState.Failure).message
                    }

                    is AddMatrixAccountViewModel.ServerDiscoveryState.Success -> {
                        "Homeserver ready"
                    }

                    AddMatrixAccountViewModel.ServerDiscoveryState.None -> {
                        "Enter a homeserver"
                    }
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
private fun ActiveAccountScreen(matrixMessenger: MatrixMessenger) {
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
    val roomsForServer = state.rooms
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 900.dp

        Row(modifier = Modifier.fillMaxSize()) {
            ServerRail(
                compact = compact,
                state = state,
                store = store,
            )
            if (!compact) {
                ChannelPanel(state, store)
            }
            ChatPanel(
                selectedChannel = state.selectedRoom?.name ?: "Select a Channel",
                roomsCount = roomsForServer.size,
                syncStateLabel = roomDirectory.syncStateLabel,
            )
        }
    }
}

@Composable
private fun ServerRail(
    compact: Boolean,
    state: AccountState,
    store: AccountStore,
) {
    Column(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(if (compact) 56.dp else 72.dp)
                .background(ServerRailBg)
                .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.destinations.forEach { server ->
            val label =
                server.name
                    .split(' ')
                    .filter { it.isNotBlank() }
                    .take(2)
                    .joinToString("") { it.take(1) }
                    .uppercase()
            ServerDot(
                label = label,
                active = server.id == state.selectedServerId,
                description = server.name,
                onClick = { store.selectServer(server.id) },
            )
        }
        Spacer(Modifier.weight(1f))
        ViewVisibilityMenu(state, store)
        ServerDot(
            label = "LO",
            description = "Log out",
            active = false,
            onClick = store::logout,
        )
    }
}

@Composable
private fun ViewVisibilityMenu(
    state: AccountState,
    store: AccountStore,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ServerDot("V", false, "Show or hide Views") { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BUILT_IN_VIEWS.forEach { view ->
                val visible = view.id !in state.hiddenViewIds
                DropdownMenuItem(
                    text = { Text(view.name) },
                    leadingIcon = { Checkbox(checked = visible, onCheckedChange = null) },
                    onClick = { store.setViewHidden(view.id, visible) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerDot(
    label: String,
    active: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.End),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier =
                Modifier
                    .semantics { contentDescription = description }
                    .size(48.dp)
                    .clip(RoundedCornerShape(if (active) 16.dp else 24.dp))
                    .background(if (active) Accent else Color(0xFF2A2D36))
                    .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, color = TextStrong, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ChannelPanel(
    state: AccountState,
    store: AccountStore,
) {
    val name = state.destinations.firstOrNull { it.id == state.selectedServerId }?.name ?: "Select a View or Server"
    val directMessages = state.selectedServerId == DIRECT_MESSAGES_VIEW_ID
    Column(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(280.dp)
                .background(ChannelPanelBg)
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(name, color = TextStrong, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        StatusBadge(state.directory.syncStateLabel)
        Spacer(modifier = Modifier.height(16.dp))
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            items(state.uncategorizedChannels.size) { index ->
                val channel = state.uncategorizedChannels[index]
                ChannelRow(
                    channel = channel,
                    selected = state.selectedRoomId == channel.id,
                    directMessage = directMessages,
                    store = store,
                )
            }
            state.categories.forEach { category ->
                item(key = "category:${category.id}") {
                    val collapsed = category.id in state.collapsedCategoryIds
                    Text(
                        text = "${if (collapsed) "▸" else "▾"} ${category.name}",
                        color = TextMuted,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    store.toggleCategory(
                                        category.id,
                                    )
                                }.padding(vertical = 8.dp),
                    )
                }
                if (category.id !in state.collapsedCategoryIds) {
                    items(category.channels.size) { index ->
                        val channel = category.channels[index]
                        ChannelRow(channel, state.selectedRoomId == channel.id, false, store)
                    }
                }
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
    channel: RoomUi,
    selected: Boolean,
    directMessage: Boolean,
    store: AccountStore,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) Color(0xFF343A47) else Color(0xFF2A2E37))
                .clickable { store.selectRoom(channel.id) }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (directMessage) {
            DirectMessageAvatar(channel, store)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = if (directMessage) channel.name else "#${channel.name}",
            color = TextStrong,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DirectMessageAvatar(
    channel: RoomUi,
    store: AccountStore,
) {
    val bitmap by produceState<ImageBitmap?>(null, channel.avatarUrl, store) {
        value = null
        val url = channel.avatarUrl
        if (url != null) {
            val bytes = store.loadAvatar(url)
            value = bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
        }
    }
    Box(
        modifier = Modifier.size(28.dp).clip(CircleShape).background(Accent),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image == null) {
            Text(channel.name.take(1).uppercase(), color = TextStrong)
        } else {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
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
