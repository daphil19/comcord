package dev.phillipslabs.comcord.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phillipslabs.comcord.design.ComcordColors
import dev.phillipslabs.comcord.rooms.RoomKind
import dev.phillipslabs.comcord.rooms.RoomListState
import dev.phillipslabs.comcord.rooms.RoomSummary

@Composable
fun RoomSidebar(
    syncStateLabel: String,
    roomListState: RoomListState,
    selectedRoomId: String?,
    onSelectRoom: (RoomSummary) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(300.dp)
                .background(ComcordColors.SurfaceSecondary)
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text("Comcord", color = ComcordColors.TextStrong, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        SyncBadge(syncStateLabel)
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            if (roomListState.sections.isEmpty()) {
                item {
                    Text(
                        text = "No rooms in this space yet.",
                        color = ComcordColors.TextMuted,
                        fontSize = 13.sp,
                    )
                }
            }
            roomListState.sections.forEach { section ->
                item {
                    Text(section.title.uppercase(), color = ComcordColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(section.rooms.size) { index ->
                    val room = section.rooms[index]
                    RoomRow(
                        room = room,
                        selected = selectedRoomId == room.id,
                        onClick = { onSelectRoom(room) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncBadge(status: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ComcordColors.SurfaceTertiary)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            modifier =
                Modifier
                    .clip(CircleShape)
                    .background(ComcordColors.Online)
                    .width(8.dp)
                    .height(8.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = status, color = ComcordColors.TextStrong, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RoomRow(
    room: RoomSummary,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) ComcordColors.SurfaceTertiary else ComcordColors.SurfacePrimary)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (room.kind == RoomKind.DirectMessage) room.name else "#${room.name}",
            color = ComcordColors.TextStrong,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (room.unreadCount > 0) {
            Text(
                text = room.unreadCount.toString(),
                color = ComcordColors.Unread,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
