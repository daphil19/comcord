package dev.phillipslabs.comcord

import kotlinx.coroutines.flow.Flow

/** One authenticated Account. SDK types stay inside the adapter. */
interface MatrixSource {
    val accountId: String
    val directory: Flow<RoomDirectoryState>

    suspend fun logout(): Result<Unit>
}

const val ALL_ROOMS_GUILD_ID = "all-rooms"

data class GuildUi(
    val id: String,
    val name: String,
)

data class RoomUi(
    val id: String,
    val name: String,
)

data class RoomDirectoryState(
    val guilds: List<GuildUi>,
    val roomsByGuild: Map<String, List<RoomUi>>,
    val syncStateLabel: String,
)

fun emptyRoomDirectory() =
    RoomDirectoryState(
        guilds = listOf(GuildUi(ALL_ROOMS_GUILD_ID, "All Rooms")),
        roomsByGuild = mapOf(ALL_ROOMS_GUILD_ID to emptyList()),
        syncStateLabel = "Starting sync",
    )
