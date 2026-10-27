package dev.phillipslabs.comcord

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountState(
    val accountId: String,
    val directory: RoomDirectoryState = emptyRoomDirectory(),
    val selectedServerId: String? = ALL_ROOMS_VIEW_ID,
    val selectedRoomId: String? = null,
    val hiddenViewIds: Set<String> = emptySet(),
    val timeline: RoomTimelineUi? = null,
    val collapsedCategoryIds: Set<String> = emptySet(),
) {
    val visibleViews: List<RailDestinationUi> get() = BUILT_IN_VIEWS.filter { it.id !in hiddenViewIds }
    val destinations: List<RailDestinationUi> get() = visibleViews + directory.servers
    val categories: List<CategoryUi> get() = directory.categoriesByServer[selectedServerId].orEmpty()
    val uncategorizedChannels: List<RoomUi> get() = directory.channelsByServer[selectedServerId].orEmpty()
    val rooms: List<RoomUi> get() = uncategorizedChannels + categories.flatMap { it.channels }
    val selectedRoom: RoomUi? get() =
        directory.channelsByServer.values
            .flatten()
            .firstOrNull { it.id == selectedRoomId }
}

/** Own one store and its scope per Account; cancel the scope when its Account UI leaves. */
class AccountStore(
    private val source: MatrixSource,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(AccountState(accountId = source.accountId))
    val state = mutableState.asStateFlow()

    private class ActiveTimeline(
        val roomId: String,
        val source: RoomTimeline,
        val scope: CoroutineScope,
    )

    private val timelineRetry = MutableStateFlow(0)

    private var activeTimeline: ActiveTimeline? = null

    init {
        scope.launch {
            combine(state.map { it.selectedRoomId }.distinctUntilChanged(), timelineRetry) { roomId, _ -> roomId }
                .collectLatest { roomId ->
                    if (roomId != null) observeTimeline(roomId)
                }
        }
        scope.launch {
            combine(
                source.directory,
                source.hiddenViewIds,
            ) { snapshot, hidden -> snapshot.projectDirectory() to hidden }
                .collect { (directory, hidden) ->
                    mutableState.update { current ->
                        val updated = current.copy(directory = directory, hiddenViewIds = hidden)
                        val roomStillPresent =
                            directory.channelsByServer.values.any { rooms ->
                                rooms.any { it.id == current.selectedRoomId }
                            }
                        updated.copy(
                            timeline = current.timeline.takeIf { roomStillPresent },
                            selectedServerId =
                                updated.selectedServerId.takeIf { id -> updated.destinations.any { it.id == id } }
                                    ?: updated.destinations.firstOrNull { it.id == ALL_ROOMS_VIEW_ID }?.id
                                    ?: updated.destinations.firstOrNull()?.id,
                            selectedRoomId =
                                current.selectedRoomId.takeIf { roomStillPresent },
                        )
                    }
                }
        }
    }

    fun selectServer(id: String) {
        mutableState.update { current ->
            if (current.destinations.any { it.id == id }) {
                current.copy(selectedServerId = id)
            } else {
                current
            }
        }
    }

    fun selectRoom(id: String) {
        if (state.value.selectedRoomId == id && state.value.timeline?.error != null) {
            mutableState.update { it.copy(timeline = RoomTimelineUi(id)) }
            timelineRetry.value += 1
            return
        }
        mutableState.update { current ->
            if (current.rooms.any { it.id == id } && current.selectedRoomId != id) {
                current.copy(selectedRoomId = id, timeline = RoomTimelineUi(id))
            } else {
                current
            }
        }
    }

    private suspend fun observeTimeline(roomId: String) =
        coroutineScope {
            val active = ActiveTimeline(roomId, source.timeline(roomId), this)
            activeTimeline = active
            try {
                active.source.snapshots.collect { snapshot ->
                    mutableState.update { current ->
                        if (current.selectedRoomId != roomId) {
                            current
                        } else {
                            current.copy(
                                timeline =
                                    (current.timeline ?: RoomTimelineUi(roomId)).copy(
                                        messages = snapshot.messages,
                                        canLoadOlder = snapshot.canLoadOlder,
                                        isLoading = snapshot.isLoading,
                                        error = null,
                                    ),
                            )
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                updateTimelineError(active, "Could not load messages. Select the Channel again to retry.")
            } finally {
                if (activeTimeline === active) activeTimeline = null
            }
        }

    fun loadOlderMessages() {
        val active = activeTimeline?.takeIf { it.roomId == state.value.selectedRoomId } ?: return
        val current = state.value.timeline
        if (current?.canRequestOlder != true) return
        mutableState.update { it.copy(timeline = current.copy(isLoadingOlder = true, error = null)) }
        active.scope.launch {
            try {
                active.source.loadOlder()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                updateTimelineError(active, "Could not load older messages. Try again.")
            } finally {
                mutableState.update { state ->
                    if (activeTimeline !== active) {
                        state
                    } else {
                        state.copy(
                            timeline = state.timeline?.copy(isLoadingOlder = false),
                        )
                    }
                }
            }
        }
    }

    private fun updateTimelineError(
        active: ActiveTimeline,
        error: String,
    ) {
        mutableState.update { current ->
            if (activeTimeline !== active || current.selectedRoomId != active.roomId) {
                current
            } else {
                current.copy(
                    timeline = current.timeline?.copy(isLoading = false, error = error),
                )
            }
        }
    }

    fun toggleCategory(id: String) {
        mutableState.update { current ->
            if (current.categories.none { it.id == id }) {
                current
            } else {
                current.copy(
                    collapsedCategoryIds =
                        if (id in current.collapsedCategoryIds) {
                            current.collapsedCategoryIds - id
                        } else {
                            current.collapsedCategoryIds + id
                        },
                )
            }
        }
    }

    fun setViewHidden(
        id: String,
        hidden: Boolean,
    ) {
        if (BUILT_IN_VIEWS.none { it.id == id }) return
        scope.launch { source.setViewHidden(id, hidden) }
    }

    suspend fun loadAvatar(url: String): ByteArray? = source.loadAvatar(url)

    fun logout() {
        scope.launch { source.logout() }
    }
}
