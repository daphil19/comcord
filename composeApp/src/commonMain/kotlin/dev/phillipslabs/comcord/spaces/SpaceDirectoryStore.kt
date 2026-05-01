package dev.phillipslabs.comcord.spaces

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.room.getAllState
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent.RoomType
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.space.ParentEventContent
import dev.phillipslabs.comcord.ALL_ROOMS_SPACE_ID
import dev.phillipslabs.comcord.rooms.RoomKind
import dev.phillipslabs.comcord.rooms.RoomSummary
import dev.phillipslabs.comcord.rooms.SpaceSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

data class SpaceDirectoryState(
    val spaces: List<SpaceSummary>,
    val roomsBySpace: Map<String, List<RoomSummary>>,
    val syncStateLabel: String,
)

data class SpaceRoomSnapshot(
    val roomId: String,
    val displayName: String,
    val membership: Membership,
    val isSpace: Boolean,
    val isDirect: Boolean,
)

class SpaceDirectoryStore(
    private val matrixClient: MatrixClient,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<SpaceDirectoryState> {
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
            val snapshots =
                rooms.map {
                    SpaceRoomSnapshot(
                        roomId = it.roomId.full,
                        displayName = it.displayName(),
                        membership = it.membership,
                        isSpace = it.createEventContent?.type == RoomType.Space,
                        isDirect = it.isDirect,
                    )
                }
            val joinedRooms = rooms.filter { it.membership == Membership.JOIN || it.membership == Membership.INVITE }
            val nonSpaceRooms = joinedRooms.filter { it.createEventContent?.type != RoomType.Space }

            val parentLookupFlow: Flow<Map<String, Set<String>>> =
                if (nonSpaceRooms.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    combine(
                        nonSpaceRooms.map { room ->
                            matrixClient.room
                                .getAllState<ParentEventContent>(room.roomId)
                                .onStart { emit(emptyMap()) }
                                .flatMapLatest { byStateKey ->
                                    val stateByKey = byStateKey.orEmpty()
                                    if (stateByKey.isEmpty()) {
                                        flowOf(room.roomId.full to emptySet())
                                    } else {
                                        combine(
                                            stateByKey.map { (stateKey, stateFlow) ->
                                                stateFlow.map { stateEvent -> stateKey to stateEvent }
                                            }
                                        ) { stateEntries ->
                                            val parentIds =
                                                stateEntries.mapNotNull { (stateKey, stateEvent) ->
                                                    if (stateEvent == null) null else stateKey
                                                }.toSet()
                                            room.roomId.full to parentIds
                                        }
                                    }
                                }
                        }
                    ) { parentEntries -> parentEntries.toMap() }
                }

            combine(parentLookupFlow, syncLabelFlow) { parentLookup, syncLabel ->
                buildSpaceDirectoryState(
                    rooms = snapshots,
                    parentLookup = parentLookup,
                    syncStateLabel = syncLabel,
                )
            }
        }
    }
}

internal fun buildSpaceDirectoryState(
    rooms: List<SpaceRoomSnapshot>,
    parentLookup: Map<String, Set<String>>,
    syncStateLabel: String,
): SpaceDirectoryState {
    val activeRooms = rooms.filter { it.membership == Membership.JOIN || it.membership == Membership.INVITE }
    val spaces = activeRooms.filter { it.isSpace }
    val nonSpaceRooms = activeRooms.filterNot { it.isSpace }
    val spaceIds = spaces.map { it.roomId }.toSet()

    val allSpaces =
        buildList {
            add(SpaceSummary(id = ALL_ROOMS_SPACE_ID, name = "All Rooms"))
            addAll(
                spaces
                    .sortedBy { it.displayName.lowercase() }
                    .map { SpaceSummary(id = it.roomId, name = it.displayName) }
            )
        }

    val roomsBySpace = linkedMapOf<String, MutableList<RoomSummary>>()
    allSpaces.forEach { roomsBySpace[it.id] = mutableListOf() }

    nonSpaceRooms.sortedBy { it.displayName.lowercase() }.forEach { room ->
        val summary =
            RoomSummary(
                id = room.roomId,
                name = room.displayName,
                kind = if (room.isDirect) RoomKind.DirectMessage else RoomKind.Room,
                isInvite = room.membership == Membership.INVITE,
            )
        roomsBySpace.getValue(ALL_ROOMS_SPACE_ID).add(summary)
        parentLookup[room.roomId].orEmpty()
            .filter { spaceIds.contains(it) }
            .forEach { parentId ->
                roomsBySpace.getOrPut(parentId) { mutableListOf() }.add(summary)
            }
    }

    return SpaceDirectoryState(
        spaces = allSpaces,
        roomsBySpace = roomsBySpace.mapValues { it.value.toList() },
        syncStateLabel = syncStateLabel,
    )
}

private fun Room.displayName(): String =
    name?.explicitName?.takeIf { it.isNotBlank() }
        ?: roomId.full
