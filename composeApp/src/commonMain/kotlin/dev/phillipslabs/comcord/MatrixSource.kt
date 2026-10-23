package dev.phillipslabs.comcord

import kotlinx.coroutines.flow.Flow

/** One authenticated Account. SDK types stay inside the adapter. */
interface MatrixSource {
    val accountId: String
    val directory: Flow<MatrixDirectory>

    suspend fun logout(): Result<Unit>
}

const val ALL_ROOMS_VIEW_ID = "all-rooms"

data class ServerUi(
    val id: String,
    val name: String,
)

data class ChannelUi(
    val id: String,
    val name: String,
)

data class RoomDirectoryState(
    val servers: List<ServerUi>,
    val channelsByServer: Map<String, List<ChannelUi>>,
    val syncStateLabel: String,
    val categoriesByServer: Map<String, List<CategoryUi>> = emptyMap(),
)

fun emptyRoomDirectory() =
    RoomDirectoryState(
        servers = emptyList(),
        channelsByServer = mapOf(ALL_ROOMS_VIEW_ID to emptyList()),
        syncStateLabel = "Starting sync",
    )

/** Joined Matrix state, without SDK types or parent claims. */
data class MatrixDirectory(
    val channels: List<ChannelUi> = emptyList(),
    val spaces: List<SpaceSnapshot> = emptyList(),
    val syncStateLabel: String = "Starting sync",
)

data class SpaceSnapshot(
    val id: String,
    val name: String,
    val children: List<SpaceChild> = emptyList(),
)

data class SpaceChild(
    val id: String,
    val order: String? = null,
)

data class CategoryUi(
    val id: String,
    val name: String,
    val channels: List<ChannelUi>,
)
