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
class AccountStoreTest {
    @Test
    fun roomListFollowsTheAccountSource() =
        runTest {
            val source = FakeMatrixSource("@alice:localhost")
            val store = AccountStore(source, backgroundScope)
            runCurrent()

            source.emitSnapshot(
                MatrixDirectory(
                    channels = listOf(ChannelUi("!lobby:localhost", "Lobby")),
                    syncStateLabel = "Synced",
                ),
            )
            runCurrent()

            assertEquals("@alice:localhost", store.state.value.accountId)
            assertEquals(listOf(ChannelUi("!lobby:localhost", "Lobby")), store.state.value.rooms)
            assertEquals("Synced", store.state.value.directory.syncStateLabel)
        }

    @Test
    fun selectionUsesRoomIdentityAndTracksRenames() =
        runTest {
            val source = FakeMatrixSource("@alice:localhost")
            val store = AccountStore(source, backgroundScope)
            source.emitSnapshot(directory(ChannelUi("!one:localhost", "Lobby"), ChannelUi("!two:localhost", "Lobby")))
            runCurrent()
            store.selectRoom("!two:localhost")
            assertEquals(
                "!two:localhost",
                store.state.value.selectedRoom
                    ?.id,
            )

            source.emitSnapshot(directory(ChannelUi("!two:localhost", "Renamed")))
            runCurrent()
            assertEquals(
                "Renamed",
                store.state.value.selectedRoom
                    ?.name,
            )
            source.emitSnapshot(directory())
            runCurrent()
            assertNull(store.state.value.selectedRoomId)
        }

    @Test
    fun accountStoresKeepSelectionAndLogoutSeparate() =
        runTest {
            val alice = FakeMatrixSource("@alice:localhost")
            val bob = FakeMatrixSource("@bob:localhost")
            val aliceStore = AccountStore(alice, backgroundScope)
            val bobStore = AccountStore(bob, backgroundScope)
            alice.emitSnapshot(directory(ChannelUi("!one:localhost", "Alice's room")))
            bob.emitSnapshot(directory(ChannelUi("!two:localhost", "Bob's room")))
            runCurrent()
            aliceStore.selectRoom("!one:localhost")
            aliceStore.logout()
            runCurrent()
            assertTrue(alice.loggedOut)
            assertEquals(false, bob.loggedOut)
            assertNull(bobStore.state.value.selectedRoomId)
            assertEquals(
                listOf("Bob's room"),
                bobStore.state.value.rooms
                    .map { it.name },
            )
        }

    @Test
    fun removedServerFallsBackToAllRoomsAndUnknownSelectionsAreIgnored() =
        runTest {
            val source = FakeMatrixSource("@alice:localhost")
            val store = AccountStore(source, backgroundScope)
            source.emitSnapshot(
                directory().copy(
                    spaces = listOf(SpaceSnapshot("!space", "Space")),
                ),
            )
            runCurrent()
            store.selectServer("!space")
            assertEquals("!space", store.state.value.selectedServerId)
            source.emitSnapshot(directory())
            runCurrent()
            store.selectServer("unknown")
            store.selectRoom("unknown")
            assertEquals(ALL_ROOMS_VIEW_ID, store.state.value.selectedServerId)
            assertNull(store.state.value.selectedRoomId)
        }

    @Test
    fun leavingTheAccountStopsItsSourceCollection() =
        runTest {
            val source = FakeMatrixSource("@alice:localhost")
            val accountScope =
                CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
            val store = AccountStore(source, accountScope)
            runCurrent()
            accountScope.cancel()
            source.emitSnapshot(directory(ChannelUi("!late:localhost", "Late update")))
            runCurrent()
            assertEquals(emptyList(), store.state.value.rooms)
        }

    @Test
    fun joinedSubspaceIsACategoryWithNestedChannels() =
        runTest {
            val source = FakeMatrixSource("@alice:localhost")
            val store = AccountStore(source, backgroundScope)
            source.emitSnapshot(
                MatrixDirectory(
                    channels = listOf(ChannelUi("channel", "General")),
                    spaces =
                        listOf(
                            SpaceSnapshot("server", "Server", listOf(SpaceChild("category"))),
                            SpaceSnapshot("category", "Category", listOf(SpaceChild("deep"))),
                            SpaceSnapshot("deep", "Deep", listOf(SpaceChild("channel"))),
                        ),
                ),
            )
            runCurrent()
            assertEquals(
                listOf("server"),
                store.state.value.directory.servers
                    .map { it.id },
            )
            store.selectServer("server")
            assertEquals(
                listOf("channel"),
                store.state.value.categories
                    .single()
                    .channels
                    .map { it.id },
            )
        }

    @Test
    fun channelsFollowChildOrderThenNameAndAppearInEachServer() =
        runTest {
            val source = FakeMatrixSource("alice")
            val store = AccountStore(source, backgroundScope)
            source.emitSnapshot(
                MatrixDirectory(
                    channels =
                        listOf(
                            ChannelUi("a", "Alpha"),
                            ChannelUi("b", "Beta"),
                            ChannelUi("c", "Charlie"),
                            ChannelUi("d", "Delta"),
                        ),
                    spaces =
                        listOf(
                            SpaceSnapshot(
                                "one",
                                "One",
                                listOf(
                                    SpaceChild("a"),
                                    SpaceChild("c", "0"),
                                    SpaceChild("b", "0"),
                                    SpaceChild("d", "\n"),
                                ),
                            ),
                            SpaceSnapshot("two", "Two", listOf(SpaceChild("b"))),
                        ),
                ),
            )
            runCurrent()
            store.selectServer("one")
            assertEquals(
                listOf("b", "c", "a", "d"),
                store.state.value.rooms
                    .map { it.id },
            )
            store.selectServer("two")
            assertEquals(
                listOf("b"),
                store.state.value.rooms
                    .map { it.id },
            )
        }

    @Test
    fun cyclesAndRepeatedDescendantsTerminateWithoutDuplicatingChannels() =
        runTest {
            val source = FakeMatrixSource("alice")
            val store = AccountStore(source, backgroundScope)
            source.emitSnapshot(
                MatrixDirectory(
                    channels = listOf(ChannelUi("direct", "Direct"), ChannelUi("nested", "Nested")),
                    spaces =
                        listOf(
                            SpaceSnapshot("root", "Root", listOf(SpaceChild("direct"), SpaceChild("category"))),
                            SpaceSnapshot(
                                "category",
                                "Category",
                                listOf(SpaceChild("deep"), SpaceChild("nested"), SpaceChild("direct")),
                            ),
                            SpaceSnapshot("deep", "Deep", listOf(SpaceChild("category"), SpaceChild("nested"))),
                            SpaceSnapshot("isolated-loop", "Loop", listOf(SpaceChild("isolated-loop"))),
                        ),
                ),
            )
            runCurrent()
            store.selectServer("root")
            assertEquals(
                listOf("root"),
                store.state.value.directory.servers
                    .map { it.id },
            )
            assertEquals(
                listOf("direct", "nested"),
                store.state.value.rooms
                    .map { it.id },
            )
            assertEquals(
                listOf("nested"),
                store.state.value.categories
                    .single()
                    .channels
                    .map { it.id },
            )
        }

    @Test
    fun categoryCollapseSurvivesServerSwitchesAndSyncButStaysWithItsAccount() =
        runTest {
            val source = FakeMatrixSource("alice")
            val store = AccountStore(source, backgroundScope)
            val otherAccount = AccountStore(FakeMatrixSource("bob"), backgroundScope)
            val snapshot =
                MatrixDirectory(
                    spaces =
                        listOf(
                            SpaceSnapshot("one", "One", listOf(SpaceChild("category"))),
                            SpaceSnapshot("category", "Category"),
                            SpaceSnapshot("two", "Two"),
                        ),
                )
            source.emitSnapshot(snapshot)
            runCurrent()
            store.selectServer("one")
            store.toggleCategory("category")
            store.selectServer("two")
            store.selectServer("one")
            source.emitSnapshot(snapshot.copy(syncStateLabel = "Synced"))
            runCurrent()
            assertEquals("one", store.state.value.selectedServerId)
            assertEquals(setOf("category"), store.state.value.collapsedCategoryIds)
            assertEquals(emptySet(), otherAccount.state.value.collapsedCategoryIds)
            store.toggleCategory("unknown")
            store.toggleCategory("category")
            assertEquals(emptySet(), store.state.value.collapsedCategoryIds)
        }

    @Test
    fun viewsSeparateDirectMessagesFromChannelsAndFindNestedOrphans() =
        runTest {
            val source = FakeMatrixSource("alice")
            val store = AccountStore(source, backgroundScope)
            val dm = ChannelUi("dm", "Bob", avatarUrl = "mxc://localhost/bob")
            source.emitSnapshot(
                MatrixDirectory(
                    channels =
                        listOf(
                            ChannelUi("dm", "Room name"),
                            ChannelUi("group", "Group chat"),
                            ChannelUi("nested", "Nested"),
                        ),
                    directMessages = listOf(dm),
                    spaces =
                        listOf(
                            SpaceSnapshot("server", "Server", listOf(SpaceChild("dm"), SpaceChild("category"))),
                            SpaceSnapshot("category", "Category", listOf(SpaceChild("nested"))),
                        ),
                ),
            )
            runCurrent()
            assertEquals(
                listOf("group", "nested"),
                store.state.value.rooms
                    .map { it.id },
            )
            store.selectServer(DIRECT_MESSAGES_VIEW_ID)
            assertEquals(listOf(dm), store.state.value.rooms)
            store.selectRoom("dm")
            assertEquals(dm, store.state.value.selectedRoom)
            source.emitSnapshot(source.directory.value.copy(syncStateLabel = "Synced"))
            runCurrent()
            assertEquals(DIRECT_MESSAGES_VIEW_ID, store.state.value.selectedServerId)
            store.selectServer(ORPHAN_ROOMS_VIEW_ID)
            assertEquals(
                listOf("group"),
                store.state.value.rooms
                    .map { it.id },
            )
            store.selectServer("server")
            assertEquals(
                listOf("nested"),
                store.state.value.rooms
                    .map { it.id },
            )
        }

    @Test
    fun hiddenViewsSurviveStoreRecreationAndCanBeRestoredPerAccount() =
        runTest {
            val source = FakeMatrixSource("alice")
            val store = AccountStore(source, backgroundScope)
            runCurrent()
            assertEquals(BUILT_IN_VIEWS, store.state.value.visibleViews)
            store.selectServer(DIRECT_MESSAGES_VIEW_ID)
            store.setViewHidden(DIRECT_MESSAGES_VIEW_ID, true)
            runCurrent()
            val restarted = AccountStore(source, backgroundScope)
            val bob = AccountStore(FakeMatrixSource("bob"), backgroundScope)
            runCurrent()
            assertEquals(
                listOf(ALL_ROOMS_VIEW_ID, ORPHAN_ROOMS_VIEW_ID),
                restarted.state.value.visibleViews
                    .map { it.id },
            )
            assertEquals(ALL_ROOMS_VIEW_ID, store.state.value.selectedServerId)
            restarted.selectServer(DIRECT_MESSAGES_VIEW_ID)
            assertEquals(ALL_ROOMS_VIEW_ID, restarted.state.value.selectedServerId)
            assertEquals(BUILT_IN_VIEWS, bob.state.value.visibleViews)
            restarted.setViewHidden(DIRECT_MESSAGES_VIEW_ID, false)
            runCurrent()
            assertEquals(BUILT_IN_VIEWS, restarted.state.value.visibleViews)
            BUILT_IN_VIEWS.forEach { restarted.setViewHidden(it.id, true) }
            runCurrent()
            assertEquals(emptyList(), restarted.state.value.visibleViews)
            assertNull(restarted.state.value.selectedServerId)
            restarted.setViewHidden(ORPHAN_ROOMS_VIEW_ID, false)
            runCurrent()
            assertEquals(ORPHAN_ROOMS_VIEW_ID, restarted.state.value.selectedServerId)
        }

    private fun directory(vararg rooms: ChannelUi) =
        MatrixDirectory(
            channels = rooms.toList(),
            syncStateLabel = "Synced",
        )
}
