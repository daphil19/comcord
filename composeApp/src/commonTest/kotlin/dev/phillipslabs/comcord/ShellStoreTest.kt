package dev.phillipslabs.comcord

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ShellStoreTest {
    @Test
    fun roomListFollowsTheAccountSource() = runTest {
        val source = FakeMatrixSource("@alice:localhost")
        val store = ShellStore(source, backgroundScope)
        runCurrent()

        source.emitDirectory(
            RoomDirectoryState(
                guilds = listOf(GuildUi(ALL_ROOMS_GUILD_ID, "All Rooms")),
                roomsByGuild = mapOf(ALL_ROOMS_GUILD_ID to listOf(RoomUi("!lobby:localhost", "Lobby"))),
                syncStateLabel = "Synced",
            ),
        )
        runCurrent()

        assertEquals("@alice:localhost", store.state.value.accountId)
        assertEquals(listOf(RoomUi("!lobby:localhost", "Lobby")), store.state.value.rooms)
        assertEquals("Synced", store.state.value.directory.syncStateLabel)
    }
    @Test
    fun selectionUsesRoomIdentityAndTracksRenames() = runTest {
        val source = FakeMatrixSource("@alice:localhost")
        val store = ShellStore(source, backgroundScope)
        source.emitDirectory(directory(RoomUi("!one:localhost", "Lobby"), RoomUi("!two:localhost", "Lobby")))
        runCurrent()
        store.selectRoom("!two:localhost")
        assertEquals("!two:localhost", store.state.value.selectedRoom?.id)

        source.emitDirectory(directory(RoomUi("!two:localhost", "Renamed")))
        runCurrent()
        assertEquals("Renamed", store.state.value.selectedRoom?.name)
        source.emitDirectory(directory())
        runCurrent()
        assertNull(store.state.value.selectedRoomId)
    }

    @Test
    fun accountStoresKeepSelectionAndLogoutSeparate() = runTest {
        val alice = FakeMatrixSource("@alice:localhost")
        val bob = FakeMatrixSource("@bob:localhost")
        val aliceStore = ShellStore(alice, backgroundScope)
        val bobStore = ShellStore(bob, backgroundScope)
        alice.emitDirectory(directory(RoomUi("!one:localhost", "Alice's room")))
        bob.emitDirectory(directory(RoomUi("!two:localhost", "Bob's room")))
        runCurrent()
        aliceStore.selectRoom("!one:localhost")
        aliceStore.logout()
        runCurrent()
        assertTrue(alice.loggedOut)
        assertEquals(false, bob.loggedOut)
        assertNull(bobStore.state.value.selectedRoomId)
        assertEquals(listOf("Bob's room"), bobStore.state.value.rooms.map { it.name })
    }

    @Test
    fun removedGuildFallsBackToAllRoomsAndUnknownSelectionsAreIgnored() = runTest {
        val source = FakeMatrixSource("@alice:localhost")
        val store = ShellStore(source, backgroundScope)
        source.emitDirectory(directory().copy(guilds = listOf(GuildUi(ALL_ROOMS_GUILD_ID, "All Rooms"), GuildUi("!space", "Space"))))
        runCurrent()
        store.selectGuild("!space")
        assertEquals("!space", store.state.value.selectedGuildId)
        source.emitDirectory(directory())
        runCurrent()
        store.selectGuild("unknown")
        store.selectRoom("unknown")
        assertEquals(ALL_ROOMS_GUILD_ID, store.state.value.selectedGuildId)
        assertNull(store.state.value.selectedRoomId)
    }

    @Test
    fun leavingTheAccountStopsItsSourceCollection() = runTest {
        val source = FakeMatrixSource("@alice:localhost")
        val accountScope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
        val store = ShellStore(source, accountScope)
        runCurrent()
        accountScope.cancel()
        source.emitDirectory(directory(RoomUi("!late:localhost", "Late update")))
        runCurrent()
        assertEquals(emptyList(), store.state.value.rooms)
    }

    private fun directory(vararg rooms: RoomUi) = RoomDirectoryState(
        guilds = listOf(GuildUi(ALL_ROOMS_GUILD_ID, "All Rooms")),
        roomsByGuild = mapOf(ALL_ROOMS_GUILD_ID to rooms.toList()),
        syncStateLabel = "Synced",
    )
}
