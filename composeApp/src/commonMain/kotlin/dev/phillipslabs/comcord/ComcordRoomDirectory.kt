package dev.phillipslabs.comcord

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

const val ALL_ROOMS_GUILD_ID = "all-rooms"

data class GuildUi(
    val id: String,
    val name: String,
)

data class RoomUi(
    val id: RoomId,
    val name: String,
)

data class RoomDirectoryState(
    val guilds: List<GuildUi>,
    val roomsByGuild: Map<String, List<RoomUi>>,
    val syncStateLabel: String,
)

@OptIn(ExperimentalCoroutinesApi::class)
fun observeRoomDirectory(matrixClient: MatrixClient): Flow<RoomDirectoryState> {
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
        val activeRooms = rooms.filter { it.membership == Membership.JOIN || it.membership == Membership.INVITE }
        val spaces = activeRooms.filter { it.createEventContent?.type == RoomType.Space }
        val roomsWithoutSpaces = activeRooms.filter { it.createEventContent?.type != RoomType.Space }

        val parentLookupFlow: Flow<Map<RoomId, Set<String>>> =
            if (roomsWithoutSpaces.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    roomsWithoutSpaces.map { room ->
                        matrixClient.room
                            .getAllState<ParentEventContent>(room.roomId)
                            .flatMapLatest { byStateKey ->
                                val stateByKey = byStateKey.orEmpty()
                                if (stateByKey.isEmpty()) {
                                    flowOf(room.roomId to emptySet())
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
                                        room.roomId to parentIds
                                    }
                                }
                            }
                    }
                ) { parentEntries -> parentEntries.toMap() }
            }

        combine(parentLookupFlow, syncLabelFlow) { parentLookup, syncLabel ->
            val guilds =
                buildList {
                    add(GuildUi(id = ALL_ROOMS_GUILD_ID, name = "All Rooms"))
                    addAll(
                        spaces.sortedBy { it.displayName() }
                            .map { space -> GuildUi(id = space.roomId.full, name = space.displayName()) }
                    )
                }

            val spaceIds = spaces.map { it.roomId.full }.toSet()
            val roomsByGuild = mutableMapOf<String, MutableList<RoomUi>>()
            guilds.forEach { guild -> roomsByGuild[guild.id] = mutableListOf() }

            roomsWithoutSpaces.sortedBy { it.displayName() }.forEach { room ->
                val roomUi = RoomUi(id = room.roomId, name = room.displayName())
                roomsByGuild.getOrPut(ALL_ROOMS_GUILD_ID) { mutableListOf() }.add(roomUi)

                val parentGuildIds = parentLookup[room.roomId].orEmpty().filter { spaceIds.contains(it) }
                parentGuildIds.forEach { parentGuildId ->
                    roomsByGuild.getOrPut(parentGuildId) { mutableListOf() }.add(roomUi)
                }
            }

            RoomDirectoryState(
                guilds = guilds,
                roomsByGuild = roomsByGuild.mapValues { it.value.toList() },
                syncStateLabel = syncLabel,
            )
        }
    }
}

private fun Room.displayName(): String =
    name?.explicitName?.takeIf { it.isNotBlank() }
        ?: roomId.full
