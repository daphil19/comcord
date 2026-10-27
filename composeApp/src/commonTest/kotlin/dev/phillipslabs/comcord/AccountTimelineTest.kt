package dev.phillipslabs.comcord

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AccountTimelineTest {
    @Test
    fun selectingAChannelLoadsItsMessagesWithSenderAndTime() =
        runTest {
            val source = FakeMatrixSource("@alice:localhost")
            source.emitSnapshot(MatrixDirectory(channels = listOf(RoomUi("room", "General"))))
            source.emitTimeline("room", TimelineSnapshot(listOf(message("first", 1000)), canLoadOlder = true))
            val store = AccountStore(source, backgroundScope)
            runCurrent()

            store.selectRoom("room")
            runCurrent()

            assertEquals(
                "room",
                store.state.value.timeline
                    ?.roomId,
            )
            assertEquals(
                listOf(message("first", 1000)),
                store.state.value.timeline
                    ?.messages,
            )
            assertFalse(
                store.state.value.timeline!!
                    .isLoading,
            )
        }

    @Test
    fun pagingBackPrependsHistoryAndStopsAtTheStart() =
        runTest {
            val source = FakeMatrixSource("@alice:localhost")
            source.emitSnapshot(MatrixDirectory(channels = listOf(RoomUi("room", "General"))))
            source.emitTimeline("room", TimelineSnapshot(listOf(message("new", 2000)), canLoadOlder = true))
            source.olderPages["room"] = ArrayDeque(listOf(listOf(message("old", 1000))))
            val store = AccountStore(source, backgroundScope)
            runCurrent()
            store.selectRoom("room")
            runCurrent()

            store.loadOlderMessages()
            store.loadOlderMessages()
            runCurrent()

            assertEquals(
                listOf("old", "new"),
                store.state.value.timeline!!
                    .messages
                    .map { it.id },
            )
            assertFalse(
                store.state.value.timeline!!
                    .canLoadOlder,
            )
            store.loadOlderMessages()
            runCurrent()
            assertEquals(
                listOf("old", "new"),
                store.state.value.timeline!!
                    .messages
                    .map { it.id },
            )
        }

    @Test
    fun incomingMessagesAppearWithoutReselectingTheRoom() =
        runTest {
            val source = FakeMatrixSource("@alice:localhost")
            source.emitSnapshot(MatrixDirectory(channels = listOf(RoomUi("room", "General"))))
            source.emitTimeline("room", TimelineSnapshot(listOf(message("first", 1000))))
            val store = AccountStore(source, backgroundScope)
            runCurrent()
            store.selectRoom("room")
            runCurrent()

            source.emitTimeline("room", TimelineSnapshot(listOf(message("first", 1000), message("incoming", 2000))))
            runCurrent()

            assertEquals(
                listOf("first", "incoming"),
                store.state.value.timeline!!
                    .messages
                    .map { it.id },
            )
        }

    @Test
    fun switchingBetweenChannelAndDmClearsTheOldTimelineAndIgnoresLateUpdates() =
        runTest {
            val source = FakeMatrixSource("@alice:localhost")
            source.emitSnapshot(
                MatrixDirectory(
                    channels = listOf(RoomUi("channel", "General")),
                    directMessages = listOf(RoomUi("dm", "Bob")),
                ),
            )
            source.emitTimeline("channel", TimelineSnapshot(listOf(message("channel-message", 1000))))
            source.emitTimeline("dm", TimelineSnapshot(listOf(message("dm-message", 2000))))
            val store = AccountStore(source, backgroundScope)
            runCurrent()
            store.selectRoom("channel")
            runCurrent()

            store.selectServer(DIRECT_MESSAGES_VIEW_ID)
            store.selectRoom("dm")
            assertEquals(
                emptyList(),
                store.state.value.timeline!!
                    .messages,
            )
            runCurrent()
            source.emitTimeline("channel", TimelineSnapshot(listOf(message("late-channel-message", 3000))))
            runCurrent()
            assertEquals(
                listOf("dm-message"),
                store.state.value.timeline!!
                    .messages
                    .map { it.id },
            )

            store.selectServer(ALL_ROOMS_VIEW_ID)
            store.selectRoom("channel")
            runCurrent()
            assertEquals(
                listOf("late-channel-message"),
                store.state.value.timeline!!
                    .messages
                    .map { it.id },
            )
        }

    @Test
    fun switchingRoomsCancelsPendingHistoryLoading() =
        runTest {
            val source = FakeMatrixSource("@alice:localhost")
            source.emitSnapshot(MatrixDirectory(channels = listOf(RoomUi("one", "One"), RoomUi("two", "Two"))))
            source.emitTimeline("one", TimelineSnapshot(listOf(message("new", 2000)), canLoadOlder = true))
            source.olderPages["one"] = ArrayDeque(listOf(listOf(message("old", 1000))))
            val pagingStarted = CompletableDeferred<Unit>()
            val releasePage = CompletableDeferred<Unit>()
            source.beforeLoadOlder = {
                pagingStarted.complete(Unit)
                releasePage.await()
            }
            val store = AccountStore(source, backgroundScope)
            runCurrent()
            store.selectRoom("one")
            runCurrent()
            store.loadOlderMessages()
            runCurrent()
            assertTrue(pagingStarted.isCompleted)

            store.selectRoom("two")
            runCurrent()
            releasePage.complete(Unit)
            runCurrent()
            assertEquals(
                "two",
                store.state.value.timeline!!
                    .roomId,
            )
            assertEquals(
                emptyList(),
                store.state.value.timeline!!
                    .messages,
            )
            assertFalse(
                store.state.value.timeline!!
                    .isLoadingOlder,
            )
            assertEquals(1, source.olderPages.getValue("one").size)
        }

    @Test
    fun leavingTheSelectedRoomClearsItsTimeline() =
        runTest {
            val source = FakeMatrixSource("@alice:localhost")
            source.emitSnapshot(MatrixDirectory(channels = listOf(RoomUi("room", "General"))))
            val store = AccountStore(source, backgroundScope)
            runCurrent()
            store.selectRoom("room")
            runCurrent()
            source.emitSnapshot(MatrixDirectory())
            runCurrent()
            assertNull(store.state.value.timeline)
        }

    @Test
    fun failedHistoryLoadKeepsMessagesAndCanBeRetried() =
        runTest {
            val source = FakeMatrixSource("@alice:localhost")
            source.emitSnapshot(MatrixDirectory(channels = listOf(RoomUi("room", "General"))))
            source.emitTimeline("room", TimelineSnapshot(listOf(message("new", 2000)), canLoadOlder = true))
            source.olderPages["room"] = ArrayDeque(listOf(listOf(message("old", 1000))))
            source.beforeLoadOlder = { error("Offline") }
            val store = AccountStore(source, backgroundScope)
            runCurrent()
            store.selectRoom("room")
            runCurrent()
            store.loadOlderMessages()
            runCurrent()
            assertEquals(
                listOf("new"),
                store.state.value.timeline!!
                    .messages
                    .map { it.id },
            )
            assertFalse(
                store.state.value.timeline!!
                    .isLoadingOlder,
            )
            assertTrue(
                store.state.value.timeline!!
                    .error != null,
            )

            source.beforeLoadOlder = {}
            store.loadOlderMessages()
            runCurrent()
            assertEquals(
                listOf("old", "new"),
                store.state.value.timeline!!
                    .messages
                    .map { it.id },
            )
            assertNull(
                store.state.value.timeline!!
                    .error,
            )
        }

    private fun message(
        id: String,
        time: Long,
    ) = MessageUi(
        id = id,
        senderId = "@alice:localhost",
        senderName = "Alice",
        avatarUrl = "mxc://localhost/alice",
        timestamp = time,
        body = id,
    )
}
