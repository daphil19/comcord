package dev.phillipslabs.comcord.timeline

import dev.phillipslabs.comcord.rooms.RoomKind
import dev.phillipslabs.comcord.rooms.RoomSummary
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TimelineStoreTest {
    @Test
    fun `observe builds timeline state for selected room`() =
        runTest {
            val store =
                TimelineStore(
                    repository =
                        FakeTimelineRepository(
                            entries =
                                listOf(
                                    TimelineEventRecord(
                                        id = "one",
                                        authorId = "@alice:example.com",
                                        authorName = "Alice",
                                        body = "Hello",
                                        timestamp = Instant.parse("2026-04-18T10:15:00Z"),
                                        kind = TimelineEventKind.Message,
                                    ),
                                ),
                        ),
                    timeZone = TimeZone.UTC,
                )

            val state =
                store
                    .observe(
                        selectedRoom = RoomSummary(id = "!room:example.com", name = "General", kind = RoomKind.Room),
                        currentUserId = "@alice:example.com",
                    ).drop(1)
                    .first()

            assertEquals("!room:example.com", state.roomId)
            assertEquals("General", state.roomName)
            assertEquals(TimelineLoadState.Ready, state.loadState)
            assertTrue(state.isReady)
            assertEquals(2, state.items.size)
            assertIs<TimelineDateDivider>(state.items.first())
            assertIs<TimelineMessage>(state.items.last())
        }

    @Test
    fun `observe emits loading state before room entries`() =
        runTest {
            val store =
                TimelineStore(
                    repository = FakeTimelineRepository(emptyList()),
                    timeZone = TimeZone.UTC,
                )

            val state =
                store
                    .observe(
                        selectedRoom = RoomSummary(id = "!room:example.com", name = "General", kind = RoomKind.Room),
                        currentUserId = "@alice:example.com",
                    ).first()

            assertEquals("!room:example.com", state.roomId)
            assertEquals("General", state.roomName)
            assertEquals(TimelineLoadState.Loading, state.loadState)
            assertTrue(state.items.isEmpty())
        }

    @Test
    fun `observe emits error state when repository fails`() =
        runTest {
            val store =
                TimelineStore(
                    repository = FailingTimelineRepository(),
                    timeZone = TimeZone.UTC,
                )

            val state =
                store
                    .observe(
                        selectedRoom = RoomSummary(id = "!room:example.com", name = "General", kind = RoomKind.Room),
                        currentUserId = "@alice:example.com",
                    ).drop(1)
                    .first()

            assertEquals("!room:example.com", state.roomId)
            assertEquals("General", state.roomName)
            assertEquals(TimelineLoadState.Error, state.loadState)
            assertEquals("Timeline unavailable", state.errorMessage)
            assertTrue(state.items.isEmpty())
        }

    @Test
    fun `build timeline items inserts date divider and groups consecutive messages`() {
        val items =
            buildTimelineItems(
                entries =
                    listOf(
                        TimelineEventRecord(
                            id = "one",
                            authorId = "@alice:example.com",
                            authorName = "Alice",
                            body = "First",
                            timestamp = Instant.parse("2026-04-18T10:15:00Z"),
                            kind = TimelineEventKind.Message,
                        ),
                        TimelineEventRecord(
                            id = "two",
                            authorId = "@alice:example.com",
                            authorName = "Alice",
                            body = "Second",
                            timestamp = Instant.parse("2026-04-18T10:16:00Z"),
                            kind = TimelineEventKind.Message,
                        ),
                    ),
                currentUserId = "@alice:example.com",
                timeZone = TimeZone.UTC,
            )

        assertEquals(3, items.size)
        assertIs<TimelineDateDivider>(items[0])
        assertTrue((items[1] as TimelineMessage).groupedWithPrevious.not())
        assertTrue((items[2] as TimelineMessage).groupedWithPrevious)
    }

    @Test
    fun `build timeline items preserves retry key for failed message`() {
        val items =
            buildTimelineItems(
                entries =
                    listOf(
                        TimelineEventRecord(
                            id = "outbox-one",
                            authorId = "@alice:example.com",
                            authorName = "Alice",
                            body = "Retry me",
                            timestamp = Instant.parse("2026-04-18T10:15:00Z"),
                            kind = TimelineEventKind.Message,
                            deliveryStatus = MessageDeliveryStatus.Failed,
                            retryKey = "txn-one",
                        ),
                    ),
                currentUserId = "@alice:example.com",
                timeZone = TimeZone.UTC,
            )

        val message = items.last() as TimelineMessage
        assertEquals(MessageDeliveryStatus.Failed, message.deliveryStatus)
        assertEquals("txn-one", message.retryKey)
    }

    @Test
    fun `retry message delegates to repository`() =
        runTest {
            val repository = FakeTimelineRepository(emptyList())
            val room = RoomSummary(id = "!room:example.com", name = "General", kind = RoomKind.Room)
            val store = TimelineStore(repository, timeZone = TimeZone.UTC)

            val result = store.retryMessage(room, "txn-one")

            assertTrue(result.isSuccess)
            assertEquals(room, repository.lastRetriedRoom)
            assertEquals("txn-one", repository.lastRetryKey)
        }

    private class FakeTimelineRepository(
        private val entries: List<TimelineEventRecord>,
    ) : TimelineRepository {
        var lastRetriedRoom: RoomSummary? = null
        var lastRetryKey: String? = null

        override fun observeRoom(room: RoomSummary) = flowOf(entries)

        override suspend fun sendMessage(
            room: RoomSummary,
            senderId: String,
            senderName: String,
            body: String,
        ) = Result.success(Unit)

        override suspend fun retryMessage(
            room: RoomSummary,
            retryKey: String,
        ): Result<Unit> {
            lastRetriedRoom = room
            lastRetryKey = retryKey
            return Result.success(Unit)
        }
    }

    private class FailingTimelineRepository : TimelineRepository {
        override fun observeRoom(room: RoomSummary) =
            flow<List<TimelineEventRecord>> {
                throw IllegalStateException("Timeline unavailable")
            }

        override suspend fun sendMessage(
            room: RoomSummary,
            senderId: String,
            senderName: String,
            body: String,
        ) = Result.success(Unit)

        override suspend fun retryMessage(
            room: RoomSummary,
            retryKey: String,
        ) = Result.success(Unit)
    }
}
