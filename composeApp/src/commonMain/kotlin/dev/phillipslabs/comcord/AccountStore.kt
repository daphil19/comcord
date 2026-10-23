package dev.phillipslabs.comcord

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountState(
    val accountId: String,
    val directory: RoomDirectoryState = emptyRoomDirectory(),
    val selectedServerId: String = ALL_ROOMS_VIEW_ID,
    val selectedRoomId: String? = null,
    val collapsedCategoryIds: Set<String> = emptySet(),
) {
    val categories: List<CategoryUi> get() = directory.categoriesByServer[selectedServerId].orEmpty()
    val uncategorizedChannels: List<ChannelUi> get() = directory.channelsByServer[selectedServerId].orEmpty()
    val rooms: List<ChannelUi> get() = uncategorizedChannels + categories.flatMap { it.channels }
    val selectedRoom: ChannelUi? get() =
        directory.channelsByServer
            .getValue(ALL_ROOMS_VIEW_ID)
            .firstOrNull { it.id == selectedRoomId }
}

/** Own one store and its scope per Account; cancel the scope when its Account UI leaves. */
class AccountStore(
    private val source: MatrixSource,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(AccountState(accountId = source.accountId))
    val state = mutableState.asStateFlow()

    init {
        scope.launch {
            source.directory.collect { snapshot ->
                val directory = snapshot.projectDirectory()
                mutableState.update { current ->
                    current.copy(
                        directory = directory,
                        selectedServerId =
                            current.selectedServerId.takeIf { id -> directory.servers.any { it.id == id } }
                                ?: ALL_ROOMS_VIEW_ID,
                        selectedRoomId =
                            current.selectedRoomId.takeIf { id ->
                                directory.channelsByServer.values.any { rooms -> rooms.any { it.id == id } }
                            },
                    )
                }
            }
        }
    }

    fun selectServer(id: String) {
        mutableState.update { current ->
            if (id == ALL_ROOMS_VIEW_ID ||
                current.directory.servers.any { it.id == id }
            ) {
                current.copy(selectedServerId = id)
            } else {
                current
            }
        }
    }

    fun selectRoom(id: String) {
        mutableState.update { current ->
            if (current.rooms.any { it.id == id }) current.copy(selectedRoomId = id) else current
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

    fun logout() {
        scope.launch { source.logout() }
    }
}
