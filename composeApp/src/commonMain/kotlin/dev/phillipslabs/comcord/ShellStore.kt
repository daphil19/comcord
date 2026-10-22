package dev.phillipslabs.comcord

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShellState(
    val accountId: String,
    val directory: RoomDirectoryState = emptyRoomDirectory(),
    val selectedGuildId: String = ALL_ROOMS_GUILD_ID,
    val selectedRoomId: String? = null,
) {
    val rooms: List<RoomUi> get() = directory.roomsByGuild[selectedGuildId].orEmpty()
    val selectedRoom: RoomUi? get() = directory.roomsByGuild.values.flatten().firstOrNull { it.id == selectedRoomId }
}

/** Own one store and its scope per Account; cancel the scope when that shell leaves. */
class ShellStore(
    private val source: MatrixSource,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(ShellState(accountId = source.accountId))
    val state = mutableState.asStateFlow()

    init {
        scope.launch {
            source.directory.collect { directory ->
                mutableState.update { current ->
                    current.copy(
                        directory = directory,
                        selectedGuildId = current.selectedGuildId.takeIf { id -> directory.guilds.any { it.id == id } }
                            ?: ALL_ROOMS_GUILD_ID,
                        selectedRoomId = current.selectedRoomId.takeIf { id ->
                            directory.roomsByGuild.values.any { rooms -> rooms.any { it.id == id } }
                        },
                    )
                }
            }
        }
    }

    fun selectGuild(id: String) {
        mutableState.update { current ->
            if (current.directory.guilds.any { it.id == id }) current.copy(selectedGuildId = id) else current
        }
    }

    fun selectRoom(id: String) {
        mutableState.update { current ->
            if (current.rooms.any { it.id == id }) current.copy(selectedRoomId = id) else current
        }
    }

    fun logout() {
        scope.launch { source.logout() }
    }
}
