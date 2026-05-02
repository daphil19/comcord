package dev.phillipslabs.comcord.timeline

import dev.phillipslabs.comcord.rooms.RoomKind
import dev.phillipslabs.comcord.rooms.RoomSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class TimelineLoadState {
    NoRoomSelected,
    Loading,
    Ready,
    Error,
}

data class TimelineState(
    val roomId: String? = null,
    val roomName: String = "Select a room",
    val roomSubtitle: String = "Choose a conversation from the sidebar to load its timeline.",
    val items: List<TimelineItem> = emptyList(),
    val loadState: TimelineLoadState = TimelineLoadState.NoRoomSelected,
    val errorMessage: String? = null,
) {
    val isReady: Boolean = loadState == TimelineLoadState.Ready
}

class TimelineStore(
    private val repository: TimelineRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    fun observe(
        selectedRoom: RoomSummary?,
        currentUserId: String,
    ): Flow<TimelineState> {
        if (selectedRoom == null) return flowOf(TimelineState())

        val baseState = selectedRoom.baseTimelineState()

        return repository
            .observeRoom(selectedRoom)
            .map { entries ->
                baseState.copy(
                    items = buildTimelineItems(entries = entries, currentUserId = currentUserId, timeZone = timeZone),
                    loadState = TimelineLoadState.Ready,
                    errorMessage = null,
                )
            }.onStart {
                emit(baseState.copy(loadState = TimelineLoadState.Loading))
            }.catch { error ->
                emit(
                    baseState.copy(
                        loadState = TimelineLoadState.Error,
                        errorMessage = error.message ?: "Unable to load this room timeline.",
                    ),
                )
            }
    }

    suspend fun retryMessage(
        room: RoomSummary,
        retryKey: String,
    ): Result<Unit> = repository.retryMessage(room, retryKey)

    private fun RoomSummary.baseTimelineState(): TimelineState =
        TimelineState(
            roomId = id,
            roomName = name,
            roomSubtitle =
                when {
                    isInvite -> "Invite pending"
                    kind == RoomKind.DirectMessage -> "Direct message"
                    else -> "Room conversation"
                },
        )
}

internal fun buildTimelineItems(
    entries: List<TimelineEventRecord>,
    currentUserId: String,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): List<TimelineItem> {
    if (entries.isEmpty()) return emptyList()

    val sortedEntries = entries.sortedBy { it.timestamp }

    return buildList {
        var previousMessage: TimelineEventRecord? = null
        var previousDateLabel: String? = null

        sortedEntries.forEach { entry ->
            val localDateTime = entry.timestamp.toLocalDateTime(timeZone)
            val dateLabel = "${localDateTime.date.month.name.lowercase().replaceFirstChar(Char::titlecase)} ${localDateTime.date.day}"

            if (dateLabel != previousDateLabel) {
                add(TimelineDateDivider(key = "date-${entry.id}", label = dateLabel))
                previousDateLabel = dateLabel
                previousMessage = null
            }

            when (entry.kind) {
                TimelineEventKind.Notice -> {
                    add(TimelineSystemNotice(key = entry.id, body = entry.body))
                    previousMessage = null
                }

                TimelineEventKind.Message -> {
                    val previous = previousMessage
                    val groupedWithPrevious =
                        previous != null &&
                            previous.kind == TimelineEventKind.Message &&
                            previous.authorId == entry.authorId &&
                            previous.authorName == entry.authorName &&
                            previous.timestamp.toLocalDateTime(timeZone).date == localDateTime.date

                    add(
                        TimelineMessage(
                            key = entry.id,
                            authorName = entry.authorName,
                            body = entry.body,
                            timeLabel = localDateTime.time.toString().take(5),
                            isOwn = entry.authorId == currentUserId,
                            groupedWithPrevious = groupedWithPrevious,
                            deliveryStatus = entry.deliveryStatus,
                            retryKey = entry.retryKey,
                        ),
                    )
                    previousMessage = entry
                }
            }
        }
    }
}
