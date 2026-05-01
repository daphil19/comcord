package dev.phillipslabs.comcord.rooms

import dev.phillipslabs.comcord.spaces.SpaceDirectoryState

data class RoomListState(
    val sections: List<RoomSection>,
)

class RoomListStore {
    fun build(spaceId: String, directoryState: SpaceDirectoryState): RoomListState {
        val rooms = directoryState.roomsBySpace[spaceId].orEmpty()
        val inviteRooms = rooms.filter { it.isInvite }
        val directMessages = rooms.filter { it.kind == RoomKind.DirectMessage && !it.isInvite }
        val regularRooms = rooms.filter { it.kind == RoomKind.Room && !it.isInvite }

        return RoomListState(
            sections =
                buildList {
                    if (inviteRooms.isNotEmpty()) add(RoomSection("Invites", inviteRooms))
                    if (directMessages.isNotEmpty()) add(RoomSection("Direct Messages", directMessages))
                    if (regularRooms.isNotEmpty()) add(RoomSection("Rooms", regularRooms))
                },
        )
    }
}
