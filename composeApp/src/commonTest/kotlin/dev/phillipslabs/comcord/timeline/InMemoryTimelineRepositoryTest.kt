package dev.phillipslabs.comcord.timeline

import dev.phillipslabs.comcord.rooms.RoomKind
import dev.phillipslabs.comcord.rooms.RoomSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InMemoryTimelineRepositoryTest {
    private val room = RoomSummary(id = "!room:example.com", name = "General", kind = RoomKind.Room)

    @Test
    fun `failed local send can be retried`() =
        runTest {
            val repository = InMemoryTimelineRepository(now = { Instant.parse("2026-04-18T12:00:00Z") })

            val sendResult =
                repository.sendMessage(
                    room = room,
                    senderId = "@alice:example.com",
                    senderName = "Alice",
                    body = "/fail hello",
                )

            assertTrue(sendResult.isFailure)

            val failedMessage =
                repository.observeRoom(room).first().last()

            assertEquals(MessageDeliveryStatus.Failed, failedMessage.deliveryStatus)
            assertNotNull(failedMessage.retryKey)

            val retryResult = repository.retryMessage(room, failedMessage.retryKey)

            assertTrue(retryResult.isSuccess)
            assertEquals(
                MessageDeliveryStatus.Sent,
                repository
                    .observeRoom(room)
                    .first()
                    .last()
                    .deliveryStatus,
            )
        }
}
