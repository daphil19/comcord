package dev.phillipslabs.comcord

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.room.getAllState
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent.RoomType
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.space.ChildEventContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
internal fun observeRoomDirectory(matrixClient: MatrixClient): Flow<MatrixDirectory> {
    val syncLabelFlow =
        matrixClient.syncState.map { syncState ->
            when (syncState) {
                SyncState.ERROR -> "Sync error"
                SyncState.INITIAL_SYNC -> "Initial sync"
                SyncState.RUNNING -> "Synced"
                SyncState.STARTED -> "Syncing"
                SyncState.STOPPED -> "Not syncing"
                SyncState.TIMEOUT -> "Sync timeout"
            }
        }

    val roomsFlow =
        matrixClient.room
            .getAll()
            .flattenValues()
            .map { rooms -> rooms.toList() }

    return roomsFlow.flatMapLatest { rooms ->
        val joinedRooms = rooms.filter { it.membership == Membership.JOIN }
        val spaces = joinedRooms.filter { it.createEventContent?.type == RoomType.Space }
        combine(observeChildren(matrixClient, spaces), syncLabelFlow) { children, syncLabel ->
            MatrixDirectory(
                channels =
                    joinedRooms
                        .filter { it.createEventContent?.type != RoomType.Space && !it.isDirect }
                        .map { ChannelUi(it.roomId.full, it.displayName()) },
                spaces =
                    spaces.map {
                        SpaceSnapshot(
                            it.roomId.full,
                            it.displayName(),
                            children[it.roomId.full].orEmpty(),
                        )
                    },
                syncStateLabel = syncLabel,
            )
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun observeChildren(
    matrixClient: MatrixClient,
    spaces: List<Room>,
): Flow<Map<String, List<SpaceChild>>> =
    if (spaces.isEmpty()) {
        flowOf(emptyMap())
    } else {
        combine(
            spaces.map { space ->
                matrixClient.room.getAllState<ChildEventContent>(space.roomId).flatMapLatest { states ->
                    if (states.isNullOrEmpty()) {
                        flowOf(space.roomId.full to emptyList())
                    } else {
                        combine(
                            states.map { (id, event) ->
                                event.map { state ->
                                    state?.content?.takeIf { it.via.isNotEmpty() }?.let { SpaceChild(id, it.order) }
                                }
                            },
                        ) { links -> space.roomId.full to links.filterNotNull() }
                    }
                }
            },
        ) { it.toMap() }
    }

private fun Room.displayName(): String = name?.explicitName?.takeIf { it.isNotBlank() } ?: roomId.full
