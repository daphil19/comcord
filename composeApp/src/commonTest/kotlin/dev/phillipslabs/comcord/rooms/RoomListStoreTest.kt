package dev.phillipslabs.comcord.rooms

import dev.phillipslabs.comcord.ALL_ROOMS_SPACE_ID
import dev.phillipslabs.comcord.spaces.SpaceDirectoryState
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomListStoreTest {
    @Test
    fun `build groups invite rooms before regular rooms`() {
        val state =
            SpaceDirectoryState(
                spaces = listOf(SpaceSummary(ALL_ROOMS_SPACE_ID, "All Rooms")),
                roomsBySpace =
                    mapOf(
                        ALL_ROOMS_SPACE_ID to
                            listOf(
                                RoomSummary(id = "!invite:example.com", name = "Invite", kind = RoomKind.Room, isInvite = true),
                                RoomSummary(id = "!room:example.com", name = "General", kind = RoomKind.Room),
                            ),
                    ),
                syncStateLabel = "Synced",
            )

        val result = RoomListStore().build(ALL_ROOMS_SPACE_ID, state)

        assertEquals(listOf("Invites", "Rooms"), result.sections.map { it.title })
        assertEquals(
            "!invite:example.com",
            result.sections
                .first()
                .rooms
                .first()
                .id,
        )
        assertEquals(
            "!room:example.com",
            result.sections
                .last()
                .rooms
                .first()
                .id,
        )
    }

    @Test
    fun `build keeps direct messages in their own section`() {
        val state =
            SpaceDirectoryState(
                spaces = listOf(SpaceSummary(ALL_ROOMS_SPACE_ID, "All Rooms")),
                roomsBySpace =
                    mapOf(
                        ALL_ROOMS_SPACE_ID to
                            listOf(
                                RoomSummary(id = "!dm:example.com", name = "Alice", kind = RoomKind.DirectMessage),
                                RoomSummary(id = "!room:example.com", name = "General", kind = RoomKind.Room),
                            ),
                    ),
                syncStateLabel = "Synced",
            )

        val result = RoomListStore().build(ALL_ROOMS_SPACE_ID, state)

        assertEquals(listOf("Direct Messages", "Rooms"), result.sections.map { it.title })
        assertEquals(
            "!dm:example.com",
            result.sections
                .first()
                .rooms
                .first()
                .id,
        )
    }
}
