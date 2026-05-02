package dev.phillipslabs.comcord.timeline

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.flatten
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.room.message.text
import de.connect2x.trixnity.client.room.toFlowList
import de.connect2x.trixnity.client.store.RoomOutboxMessage
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.isReplacing
import de.connect2x.trixnity.client.store.originTimestamp
import de.connect2x.trixnity.client.store.sender
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent
import de.connect2x.trixnity.core.model.events.m.ReactionEventContent
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.bodyWithoutFallback
import dev.phillipslabs.comcord.rooms.RoomSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds

class MatrixTimelineRepository(
    private val matrixClient: MatrixClient,
) : TimelineRepository {
    override fun observeRoom(room: RoomSummary): Flow<List<TimelineEventRecord>> {
        val roomId = RoomId(room.id)
        val maxTimelineItems = MutableStateFlow(60)

        val timelineEvents =
            matrixClient.room
                .getLastTimelineEvents(roomId) {
                    maxSize = maxTimelineItems.value.toLong()
                    minSize = 1
                    fetchSize = maxTimelineItems.value.toLong()
                    fetchTimeout = 5.seconds
                    decryptionTimeout = 5.seconds
                }.toFlowList(maxTimelineItems)
                .flatten()
                .map { events ->
                    events.mapNotNull { mapTimelineEventRecord(it) }
                }

        val outboxEvents =
            matrixClient.room
                .getOutbox(roomId)
                .flatten()
                .map { outbox ->
                    outbox
                        .filter { !it.isDraft && it.eventId == null }
                        .mapNotNull { mapOutboxEventRecord(it) }
                }

        return combine(timelineEvents, outboxEvents) { timeline, outbox ->
            (timeline + outbox)
                .sortedBy { it.timestamp }
                .distinctBy { it.id }
        }
    }

    override suspend fun sendMessage(
        room: RoomSummary,
        senderId: String,
        senderName: String,
        body: String,
    ): Result<Unit> =
        runCatching {
            matrixClient.room.sendMessage(RoomId(room.id)) {
                text(body)
            }
            Unit
        }

    override suspend fun retryMessage(
        room: RoomSummary,
        retryKey: String,
    ): Result<Unit> =
        runCatching {
            matrixClient.room.retrySendMessage(RoomId(room.id), retryKey)
        }

    private fun mapTimelineEventRecord(event: TimelineEvent): TimelineEventRecord? {
        if (event.isReplacing) return null

        val mergedEvent = event.mergedEvent?.getOrNull() ?: return null
        val timestamp = Instant.fromEpochMilliseconds(event.originTimestamp)

        return when (mergedEvent) {
            is RoomEvent.MessageEvent<*> -> {
                when (val content = mergedEvent.content) {
                    is ReactionEventContent -> null
                    is RoomMessageEventContent.TextBased.Notice ->
                        TimelineEventRecord(
                            id = event.eventId.full,
                            authorId = null,
                            authorName = mergedEvent.sender.full,
                            body = content.bodyWithoutFallback,
                            timestamp = timestamp,
                            kind = TimelineEventKind.Notice,
                        )

                    is RoomMessageEventContent ->
                        TimelineEventRecord(
                            id = event.eventId.full,
                            authorId = event.sender.full,
                            authorName = if (event.sender == matrixClient.userId) "You" else mergedEvent.sender.full,
                            body = content.bodyWithoutFallback,
                            timestamp = timestamp,
                            kind = TimelineEventKind.Message,
                        )

                    else -> null
                }
            }

            is RoomEvent.StateEvent<*> -> {
                when (val content = mergedEvent.content) {
                    is MemberEventContent ->
                        TimelineEventRecord(
                            id = event.eventId.full,
                            authorId = null,
                            authorName = mergedEvent.sender.full,
                            body = membershipNotice(mergedEvent.stateKey, content.membership.name.lowercase()),
                            timestamp = timestamp,
                            kind = TimelineEventKind.Notice,
                        )

                    else -> null
                }
            }
        }
    }

    private fun mapOutboxEventRecord(message: RoomOutboxMessage<*>): TimelineEventRecord? {
        val roomMessage = message.content as? RoomMessageEventContent ?: return null

        return TimelineEventRecord(
            id = "outbox-${message.transactionId}",
            authorId = matrixClient.userId.full,
            authorName = "You",
            body = roomMessage.bodyWithoutFallback,
            timestamp = Instant.fromEpochMilliseconds(message.createdAt.toEpochMilliseconds()),
            kind = TimelineEventKind.Message,
            deliveryStatus =
                when {
                    message.sendError != null -> MessageDeliveryStatus.Failed
                    else -> MessageDeliveryStatus.Sending
                },
            retryKey = message.transactionId.takeIf { message.sendError != null },
        )
    }
}

private fun membershipNotice(
    userId: String,
    membership: String,
): String =
    when (membership) {
        "join" -> "$userId joined the room."
        "invite" -> "$userId was invited."
        "leave" -> "$userId left the room."
        "ban" -> "$userId was banned."
        "knock" -> "$userId requested access."
        else -> "$userId membership changed."
    }
