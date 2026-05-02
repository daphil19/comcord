package dev.phillipslabs.comcord.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phillipslabs.comcord.design.ComcordColors
import dev.phillipslabs.comcord.rooms.RoomKind
import dev.phillipslabs.comcord.rooms.RoomListState
import dev.phillipslabs.comcord.rooms.RoomSummary
import dev.phillipslabs.comcord.timeline.ComposerState
import dev.phillipslabs.comcord.timeline.MessageDeliveryStatus
import dev.phillipslabs.comcord.timeline.TimelineDateDivider
import dev.phillipslabs.comcord.timeline.TimelineItem
import dev.phillipslabs.comcord.timeline.TimelineLoadState
import dev.phillipslabs.comcord.timeline.TimelineMessage
import dev.phillipslabs.comcord.timeline.TimelineState
import dev.phillipslabs.comcord.timeline.TimelineSystemNotice

@Composable
fun RoomTimelinePane(
    compact: Boolean,
    roomListState: RoomListState,
    selectedRoomId: String?,
    timelineState: TimelineState,
    composerState: ComposerState,
    syncStateLabel: String,
    onSelectRoom: (String) -> Unit,
    onDraftChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onRetryMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(ComcordColors.SurfaceTertiary),
    ) {
        if (compact) {
            CompactRoomPicker(
                roomListState = roomListState,
                selectedRoomId = selectedRoomId,
                onSelectRoom = onSelectRoom,
            )
        }

        if (timelineState.loadState != TimelineLoadState.Ready) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            ) {
                TimelineUnavailableState(
                    loadState = timelineState.loadState,
                    errorMessage = timelineState.errorMessage,
                    syncStateLabel = syncStateLabel,
                    hasRooms = roomListState.sections.any { it.rooms.isNotEmpty() },
                )
            }
        } else if (timelineState.items.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            ) {
                ConversationEmptyState(roomName = timelineState.roomName)
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            ) {
                items(timelineState.items, key = TimelineItem::key) { item ->
                    when (item) {
                        is TimelineDateDivider -> TimelineDateDividerRow(item)
                        is TimelineMessage -> TimelineMessageRow(item, onRetryMessage)
                        is TimelineSystemNotice -> TimelineSystemNoticeRow(item)
                    }
                }
            }
        }

        ComposerBar(
            composerState = composerState,
            onDraftChange = onDraftChange,
            onSendMessage = onSendMessage,
        )
    }
}

@Composable
private fun CompactRoomPicker(
    roomListState: RoomListState,
    selectedRoomId: String?,
    onSelectRoom: (String) -> Unit,
) {
    val rooms = roomListState.sections.flatMap { it.rooms }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
        Text(
            text = "Rooms",
            color = ComcordColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (rooms.isEmpty()) {
            Text(
                text = "No rooms in this space yet.",
                color = ComcordColors.TextMuted,
                fontSize = 13.sp,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rooms, key = RoomSummary::id) { room ->
                    val selected = room.id == selectedRoomId
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (selected) ComcordColors.Accent else ComcordColors.SurfacePrimary)
                                .clickable { onSelectRoom(room.id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = if (room.kind == RoomKind.DirectMessage) room.name else "#${room.name}",
                            color = ComcordColors.TextStrong,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineUnavailableState(
    loadState: TimelineLoadState,
    errorMessage: String?,
    syncStateLabel: String,
    hasRooms: Boolean,
) {
    val title =
        when (loadState) {
            TimelineLoadState.NoRoomSelected -> {
                if (hasRooms) "Select a room to load its timeline." else "No rooms in this space yet."
            }

            TimelineLoadState.Loading -> {
                "Loading conversation..."
            }

            TimelineLoadState.Error -> {
                "Unable to load this conversation."
            }

            TimelineLoadState.Ready -> {
                ""
            }
        }
    val detail =
        when (loadState) {
            TimelineLoadState.NoRoomSelected -> {
                if (hasRooms) {
                    "Sync: $syncStateLabel"
                } else {
                    "Switch spaces or create a room after sync completes."
                }
            }

            TimelineLoadState.Loading -> {
                "Sync: $syncStateLabel"
            }

            TimelineLoadState.Error -> {
                errorMessage ?: "Try selecting the room again after sync catches up."
            }

            TimelineLoadState.Ready -> {
                ""
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = ComcordColors.TextStrong,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = detail,
            color = ComcordColors.TextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ConversationEmptyState(roomName: String) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No messages in $roomName yet.",
            color = ComcordColors.TextStrong,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Send the first message to start the conversation.",
            color = ComcordColors.TextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TimelineDateDividerRow(item: TimelineDateDivider) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = item.label,
            color = ComcordColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun TimelineSystemNoticeRow(item: TimelineSystemNotice) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = item.body,
            color = ComcordColors.TextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TimelineMessageRow(
    item: TimelineMessage,
    onRetryMessage: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (item.isOwn) Alignment.End else Alignment.Start,
    ) {
        if (!item.groupedWithPrevious) {
            Text(
                text = item.authorName,
                color = ComcordColors.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Column(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (item.isOwn) ComcordColors.Accent else ComcordColors.SurfacePrimary)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = item.body,
                color = ComcordColors.TextStrong,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = timelineMetaLabel(item),
                color = ComcordColors.TextMuted,
                fontSize = 11.sp,
            )
            if (item.deliveryStatus == MessageDeliveryStatus.Failed && item.retryKey != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onRetryMessage(item.retryKey) },
                    modifier = Modifier.height(36.dp),
                ) {
                    Text("Retry")
                }
            }
        }
    }
}

private fun timelineMetaLabel(item: TimelineMessage): String {
    val status =
        when (item.deliveryStatus) {
            MessageDeliveryStatus.Failed -> "Failed"
            MessageDeliveryStatus.Sending -> "Sending"
            MessageDeliveryStatus.Sent -> null
        }
    return listOfNotNull(item.timeLabel, status).joinToString(" • ")
}

@Composable
private fun ComposerBar(
    composerState: ComposerState,
    onDraftChange: (String) -> Unit,
    onSendMessage: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(ComcordColors.SurfaceSecondary)
                .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        if (composerState.errorMessage != null) {
            Text(
                text = composerState.errorMessage,
                color = ComcordColors.Destructive,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = composerState.draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                enabled = composerState.roomId != null && !composerState.isSending,
                placeholder = {
                    Text(
                        text = composerState.placeholder,
                        color = ComcordColors.TextMuted,
                    )
                },
                minLines = 2,
                maxLines = 5,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ComcordColors.SurfacePrimary,
                        unfocusedContainerColor = ComcordColors.SurfacePrimary,
                        disabledContainerColor = ComcordColors.SurfacePrimary,
                        focusedBorderColor = ComcordColors.Accent,
                        unfocusedBorderColor = ComcordColors.SurfaceTertiary,
                        disabledBorderColor = ComcordColors.SurfaceTertiary,
                        focusedTextColor = ComcordColors.TextStrong,
                        unfocusedTextColor = ComcordColors.TextStrong,
                        disabledTextColor = ComcordColors.TextMuted,
                        cursorColor = ComcordColors.Accent,
                    ),
            )
            Button(
                onClick = onSendMessage,
                enabled = composerState.canSend,
                modifier = Modifier.width(104.dp).height(56.dp),
            ) {
                Text(if (composerState.isSending) "Sending" else "Send")
            }
        }
    }
}
