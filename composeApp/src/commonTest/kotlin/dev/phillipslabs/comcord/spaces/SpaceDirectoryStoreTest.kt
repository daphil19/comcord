package dev.phillipslabs.comcord.spaces

import de.connect2x.trixnity.core.model.events.m.room.Membership
import dev.phillipslabs.comcord.ALL_ROOMS_SPACE_ID
import dev.phillipslabs.comcord.rooms.RoomKind
import kotlin.test.Test
import kotlin.test.assertEquals

class SpaceDirectoryStoreTest {
    @Test
    fun `build space directory includes all rooms bucket and parent space mapping`() {
        val state =
            buildSpaceDirectoryState(
                rooms =
                    listOf(
                        SpaceRoomSnapshot(
                            roomId = "!space:example.com",
                            displayName = "Workspace",
                            membership = Membership.JOIN,
                            isSpace = true,
                            isDirect = false,
                        ),
                        SpaceRoomSnapshot(
                            roomId = "!room:example.com",
                            displayName = "General",
                            membership = Membership.JOIN,
                            isSpace = false,
                            isDirect = false,
                        ),
                    ),
                parentLookup = mapOf("!room:example.com" to setOf("!space:example.com")),
                syncStateLabel = "Synced",
            )

        assertEquals(listOf(ALL_ROOMS_SPACE_ID, "!space:example.com"), state.spaces.map { it.id })
        assertEquals(listOf("!room:example.com"), state.roomsBySpace.getValue(ALL_ROOMS_SPACE_ID).map { it.id })
        assertEquals(listOf("!room:example.com"), state.roomsBySpace.getValue("!space:example.com").map { it.id })
    }

    @Test
    fun `build space directory classifies direct rooms separately`() {
        val state =
            buildSpaceDirectoryState(
                rooms =
                    listOf(
                        SpaceRoomSnapshot(
                            roomId = "!dm:example.com",
                            displayName = "Alice",
                            membership = Membership.JOIN,
                            isSpace = false,
                            isDirect = true,
                        ),
                    ),
                parentLookup = emptyMap(),
                syncStateLabel = "Synced",
            )

        assertEquals(
            RoomKind.DirectMessage,
            state.roomsBySpace
                .getValue(ALL_ROOMS_SPACE_ID)
                .single()
                .kind,
        )
    }
}
