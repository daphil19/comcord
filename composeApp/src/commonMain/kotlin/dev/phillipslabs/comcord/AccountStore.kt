package dev.phillipslabs.comcord

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountState(
    val accountId: String,
    val directory: RoomDirectoryState = emptyRoomDirectory(),
    val selectedServerId: String? = ALL_ROOMS_VIEW_ID,
    val selectedRoomId: String? = null,
    val hiddenViewIds: Set<String> = emptySet(),
    val collapsedCategoryIds: Set<String> = emptySet(),
) {
    val visibleViews: List<ServerUi> get() = BUILT_IN_VIEWS.filter { it.id !in hiddenViewIds }
    val destinations: List<ServerUi> get() = visibleViews + directory.servers
    val categories: List<CategoryUi> get() = directory.categoriesByServer[selectedServerId].orEmpty()
    val uncategorizedChannels: List<ChannelUi> get() = directory.channelsByServer[selectedServerId].orEmpty()
    val rooms: List<ChannelUi> get() = uncategorizedChannels + categories.flatMap { it.channels }
    val selectedRoom: ChannelUi? get() =
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

    init {
        scope.launch {
            combine(
                source.directory,
                source.hiddenViewIds,
            ) { snapshot, hidden -> snapshot.projectDirectory() to hidden }
                .collect { (directory, hidden) ->
                    mutableState.update { current ->
                        val updated = current.copy(directory = directory, hiddenViewIds = hidden)
                        updated.copy(
                            selectedServerId =
                                updated.selectedServerId.takeIf { id -> updated.destinations.any { it.id == id } }
                                    ?: updated.destinations.firstOrNull { it.id == ALL_ROOMS_VIEW_ID }?.id
                                    ?: updated.destinations.firstOrNull()?.id,
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
            if (current.destinations.any { it.id == id }) {
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
