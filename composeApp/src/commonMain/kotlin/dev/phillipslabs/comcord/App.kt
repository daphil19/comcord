package dev.phillipslabs.comcord

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import de.connect2x.trixnity.messenger.viewmodel.RootRouter
import de.connect2x.trixnity.messenger.viewmodel.RootViewModel

private val AppBg = Color(0xFF1A1D24)
private val GuildRailBg = Color(0xFF12141A)
private val ChannelPanelBg = Color(0xFF21252E)
private val ChatPanelBg = Color(0xFF2A2E38)
private val ComposerBg = Color(0xFF3A3F4B)
private val Accent = Color(0xFF4E69FF)
private val TextStrong = Color(0xFFE6E8EE)
private val TextMuted = Color(0xFFA7ACB7)

private data class ChannelItem(
    val id: String,
    val name: String,
    val unread: Int,
)

private data class MessageItem(
    val author: String,
    val text: String,
    val time: String,
)

private val initialChannels =
    listOf(
        ChannelItem("general", "general", 3),
        ChannelItem("announce", "announcements", 0),
        ChannelItem("ios", "ios-builds", 1),
        ChannelItem("android", "android-dev", 0),
        ChannelItem("web", "web-client", 5),
    )

private val initialMessages =
    listOf(
        MessageItem("Alma", "Initial shell is up across targets.", "09:14"),
        MessageItem("River", "Matrix core is running in single-account mode.", "09:16"),
        MessageItem("Tess", "Next: auth and room sync.", "09:19"),
    )

@Suppress("ktlint:compose:preview-public-check")
@Composable
fun App(rootViewModel: RootViewModel) {
    val stackState = rootViewModel.stack.subscribeAsState()
    val currentRoute = stackState.value.active.instance
    val matrixStatus = routeStatus(currentRoute)

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AppBg,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val compact = maxWidth < 840.dp

                Row(modifier = Modifier.fillMaxSize()) {
                    GuildRail(compact = compact)
                    if (!compact) {
                        ChannelPanel(
                            status = matrixStatus,
                            channels = initialChannels,
                        )
                    }
                    ChatPanel(
                        selectedChannel = if (compact) "#general (mobile)" else "#general",
                        messages = initialMessages,
                    )
                }
            }
        }
    }
}

@Composable
private fun GuildRail(compact: Boolean) {
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
        GuildDot(label = "C", active = true)
        GuildDot(label = "M", active = false)
        GuildDot(label = "D", active = false)
        Spacer(modifier = Modifier.weight(1f))
        GuildDot(label = "+", active = false)
    }
}

@Composable
private fun GuildDot(
    label: String,
    active: Boolean,
) {
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(if (active) 16.dp else 24.dp))
                .background(if (active) Accent else Color(0xFF2A2D36)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = TextStrong,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ChannelPanel(
    status: String,
    channels: List<ChannelItem>,
) {
    Column(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(272.dp)
                .background(ChannelPanelBg)
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = "Comcord",
            color = TextStrong,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        StatusBadge(status)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "TEXT CHANNELS",
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            items(channels.size) { index ->
                val channel = channels[index]
                ChannelRow(channel)
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
        Text(
            text = status,
            color = TextStrong,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChannelRow(channel: ChannelItem) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2A2E37))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#${channel.name}",
            color = TextStrong,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (channel.unread > 0) {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Accent)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = channel.unread.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ChatPanel(
    selectedChannel: String,
    messages: List<MessageItem>,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(ChatPanelBg)
                .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            text = selectedChannel,
            color = TextStrong,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(messages.size) { index ->
                val message = messages[index]
                MessageRow(message)
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ComposerBg)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = "Message #general",
                color = TextMuted,
            )
        }
    }
}

@Composable
private fun MessageRow(message: MessageItem) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3A4150)),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = message.author,
                    color = TextStrong,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                Text(
                    text = message.time,
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = message.text,
                color = TextStrong,
                fontSize = 13.sp,
            )
        }
    }
}

private fun routeStatus(route: RootRouter.Wrapper): String =
    when (route) {
        is RootRouter.Wrapper.AddMatrixAccount -> "Waiting for account setup"
        is RootRouter.Wrapper.Main -> "Connected"
        is RootRouter.Wrapper.MatrixClientInitialization -> "Initializing Matrix client"
        is RootRouter.Wrapper.MatrixClientInitializationFailure -> "Client initialization failed"
        RootRouter.Wrapper.None -> "Ready"
        is RootRouter.Wrapper.OAuth2Login -> "OAuth2 login available"
        is RootRouter.Wrapper.PasswordLogin -> "Password login required"
        is RootRouter.Wrapper.RegisterMatrixAccount -> "Registration available"
        is RootRouter.Wrapper.RemoveMatrixAccount -> "Removing account"
        is RootRouter.Wrapper.SSOLogin -> "SSO login available"
    }
