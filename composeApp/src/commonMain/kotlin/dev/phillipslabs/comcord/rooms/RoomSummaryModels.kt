package dev.phillipslabs.comcord.rooms

enum class RoomKind {
    Room,
    DirectMessage,
}

data class SpaceSummary(
    val id: String,
    val name: String,
)

data class RoomSummary(
    val id: String,
    val name: String,
    val kind: RoomKind,
    val isInvite: Boolean = false,
    val unreadCount: Int = 0,
)

data class RoomSection(
    val title: String,
    val rooms: List<RoomSummary>,
)
