package dev.phillipslabs.comcord

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
internal class TrixnityRoomTimeline(
    private val client: MatrixClient,
    roomId: String,
) : RoomTimeline {
    private val matrixRoomId = RoomId(roomId)
    private val timeline = client.room.getTimeline { events -> events.flatMapLatest(::message) }

    override val snapshots: Flow<TimelineSnapshot> =
        channelFlow {
            launch {
                var initialized = false
                client.room
                    .getById(matrixRoomId)
                    .map { it?.lastEventId }
                    .distinctUntilChanged()
                    .collect { lastEventId ->
                        if (lastEventId == null) {
                            send(TimelineSnapshot())
                        } else if (!initialized) {
                            timeline.init(
                                matrixRoomId,
                                lastEventId,
                                configBefore = {
                                    minSize = PAGE_SIZE
                                    maxSize = PAGE_SIZE
                                },
                                configAfter = { minSize = 1 },
                            )
                            initialized = true
                        }
                    }
            }
            launch {
                timeline.state.map { it.isInitialized && it.canLoadAfter }.distinctUntilChanged().collect { hasNewer ->
                    if (hasNewer) {
                        timeline.loadAfter {
                            minSize = 1
                            maxSize = null
                        }
                    }
                }
            }
            timeline.state
                .filter { it.isInitialized }
                .flatMapLatest { state ->
                    if (state.elements.isEmpty()) {
                        flowOf(TimelineSnapshot(canLoadOlder = state.canLoadBefore))
                    } else {
                        combine(state.elements) { messages ->
                            TimelineSnapshot(messages.filterNotNull(), canLoadOlder = state.canLoadBefore)
                        }
                    }
                }.collect { send(it) }
        }

    override suspend fun loadOlder() {
        timeline.loadBefore {
            minSize = PAGE_SIZE
            maxSize = PAGE_SIZE
        }
    }

    private fun message(event: TimelineEvent): Flow<MessageUi?> {
        val content = event.content?.getOrNull() as? RoomMessageEventContent ?: return flowOf(null)
        return client.user.getById(event.event.roomId, event.event.sender).map { sender ->
            MessageUi(
                id = event.event.id.full,
                senderId = event.event.sender.full,
                senderName = sender?.name ?: event.event.sender.full,
                avatarUrl = sender?.event?.content?.avatarUrl,
                timestamp = event.event.originTimestamp,
                body = content.body,
            )
        }
    }
}

private const val PAGE_SIZE = 30L
