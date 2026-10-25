package dev.phillipslabs.comcord

import kotlinx.coroutines.flow.Flow

/** One authenticated Account. SDK types stay inside the adapter. */
interface MatrixSource {
    val accountId: String
    val directory: Flow<MatrixDirectory>
    val hiddenViewIds: Flow<Set<String>>

    suspend fun setViewHidden(
        id: String,
        hidden: Boolean,
    )

    suspend fun loadAvatar(url: String): ByteArray?

    suspend fun logout(): Result<Unit>
}

const val DIRECT_MESSAGES_VIEW_ID = "direct-messages"
const val ORPHAN_ROOMS_VIEW_ID = "orphan-rooms"

val BUILT_IN_VIEWS =
    listOf(
        RailDestinationUi(DIRECT_MESSAGES_VIEW_ID, "Direct Messages"),
        RailDestinationUi(ALL_ROOMS_VIEW_ID, "All Rooms"),
        RailDestinationUi(ORPHAN_ROOMS_VIEW_ID, "Orphan Rooms"),
    )

const val ALL_ROOMS_VIEW_ID = "all-rooms"

data class RailDestinationUi(
    val id: String,
    val name: String,
)

data class RoomUi(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
)

data class RoomDirectoryState(
    val servers: List<RailDestinationUi>,
    val channelsByServer: Map<String, List<RoomUi>>,
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
    val channels: List<RoomUi> = emptyList(),
    val spaces: List<SpaceSnapshot> = emptyList(),
    val directMessages: List<RoomUi> = emptyList(),
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
    val channels: List<RoomUi>,
)
