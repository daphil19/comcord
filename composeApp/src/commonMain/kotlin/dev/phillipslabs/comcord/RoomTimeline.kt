package dev.phillipslabs.comcord

import kotlinx.coroutines.flow.Flow

/** A room-scoped session. Collect snapshots while the room is selected. */
interface RoomTimeline {
    val snapshots: Flow<TimelineSnapshot>

    suspend fun loadOlder()
}

data class MessageUi(
    val id: String,
    val senderId: String,
    val senderName: String,
    val avatarUrl: String?,
    val timestamp: Long,
    val body: String,
)

/** Messages are ordered from oldest to newest. */
data class TimelineSnapshot(
    val messages: List<MessageUi> = emptyList(),
    val canLoadOlder: Boolean = false,
    val isLoading: Boolean = false,
)

data class RoomTimelineUi(
    val roomId: String,
    val messages: List<MessageUi> = emptyList(),
    val canLoadOlder: Boolean = false,
    val isLoading: Boolean = true,
    val isLoadingOlder: Boolean = false,
    val error: String? = null,
) {
    val canRequestOlder: Boolean get() = canLoadOlder && !isLoading && !isLoadingOlder
}
