package dev.phillipslabs.comcord.timeline

import dev.phillipslabs.comcord.rooms.RoomKind
import dev.phillipslabs.comcord.rooms.RoomSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Instant

class InMemoryTimelineRepository(
    private val now: () -> Instant = { Instant.parse("2026-04-18T12:00:00Z") },
) : TimelineRepository {
    private val rooms = linkedMapOf<String, MutableStateFlow<List<TimelineEventRecord>>>()
    private var nextTransaction = 0

    override fun observeRoom(room: RoomSummary): Flow<List<TimelineEventRecord>> = flowFor(room)

    override suspend fun sendMessage(
        room: RoomSummary,
        senderId: String,
        senderName: String,
        body: String,
    ): Result<Unit> {
        val normalizedBody = body.trim()
        if (normalizedBody.isEmpty()) return Result.failure(IllegalArgumentException("Message cannot be empty."))

        val flow = flowFor(room)
        val transactionId = "local-${room.id}-${nextTransaction++}"
        if (normalizedBody.startsWith("/fail")) {
            flow.value =
                flow.value + TimelineEventRecord(
                    id = "outbox-$transactionId",
                    authorId = senderId,
                    authorName = senderName,
                    body = normalizedBody.removePrefix("/fail").trim().ifEmpty { normalizedBody },
                    timestamp = now(),
                    kind = TimelineEventKind.Message,
                    deliveryStatus = MessageDeliveryStatus.Failed,
                    retryKey = transactionId,
                )
            return Result.failure(IllegalStateException("Simulated send failure."))
        }

        flow.value =
            flow.value + TimelineEventRecord(
                id = transactionId,
                authorId = senderId,
                authorName = senderName,
                body = normalizedBody,
                timestamp = now(),
                kind = TimelineEventKind.Message,
            )
        return Result.success(Unit)
    }

    override suspend fun retryMessage(
        room: RoomSummary,
        retryKey: String,
    ): Result<Unit> {
        val flow = flowFor(room)
        val current = flow.value
        var retried = false
        flow.value =
            current.map { event ->
                if (event.retryKey == retryKey && event.deliveryStatus == MessageDeliveryStatus.Failed) {
                    retried = true
                    event.copy(
                        id = "local-${room.id}-$retryKey",
                        deliveryStatus = MessageDeliveryStatus.Sent,
                        retryKey = null,
                    )
                } else {
                    event
                }
            }
        return if (retried) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalArgumentException("No failed message found for retry."))
        }
    }

    private fun flowFor(room: RoomSummary): MutableStateFlow<List<TimelineEventRecord>> =
        rooms.getOrPut(room.id) {
            MutableStateFlow(seedTimeline(room))
        }

    private fun seedTimeline(room: RoomSummary): List<TimelineEventRecord> {
        val instant = now()
        val introAuthor =
            when {
                room.isInvite -> "Invite Bot"
                room.kind == RoomKind.DirectMessage -> room.name
                else -> "Comcord Bot"
            }
        val introBody =
            when {
                room.isInvite -> "This room still needs your response."
                room.kind == RoomKind.DirectMessage -> "Say hi to ${room.name}."
                else -> "Welcome to ${room.name}. Shared timeline wiring is now owned by Comcord."
            }

        return listOf(
            TimelineEventRecord(
                id = "notice-${room.id}",
                authorId = null,
                authorName = "Comcord",
                body = "Timeline loaded for ${room.name}.",
                timestamp = instant,
                kind = TimelineEventKind.Notice,
            ),
            TimelineEventRecord(
                id = "intro-${room.id}",
                authorId = "@seed:${room.id}",
                authorName = introAuthor,
                body = introBody,
                timestamp = instant,
                kind = TimelineEventKind.Message,
            ),
        )
    }
}
