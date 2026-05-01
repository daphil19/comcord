package dev.phillipslabs.comcord.navigation

import dev.phillipslabs.comcord.ALL_ROOMS_SPACE_ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

data class AppNavigationState(
    val selectedSpaceId: String = ALL_ROOMS_SPACE_ID,
    val selectedRoomId: String? = null,
)

class AppNavigator(
    initialState: Map<String, AppNavigationState> = emptyMap(),
) {
    private val mutableState = MutableStateFlow(initialState)

    fun observe(accountId: String): Flow<AppNavigationState> =
        mutableState.map { states -> states[accountId] ?: AppNavigationState() }

    fun currentState(accountId: String): AppNavigationState =
        mutableState.value[accountId] ?: AppNavigationState()

    fun selectSpace(accountId: String, spaceId: String) {
        mutableState.update { current ->
            current.updateAccount(accountId) { state ->
                if (state.selectedSpaceId == spaceId) {
                    state
                } else {
                    state.copy(selectedSpaceId = spaceId, selectedRoomId = null)
                }
            }
        }
    }

    fun selectRoom(accountId: String, roomId: String?) {
        mutableState.update { current ->
            current.updateAccount(accountId) { state ->
                if (state.selectedRoomId == roomId) state else state.copy(selectedRoomId = roomId)
            }
        }
    }

    fun reconcile(accountId: String, availableSpaceIds: Set<String>, availableRoomIds: Set<String>) {
        mutableState.update { current ->
            current.updateAccount(accountId) { state ->
                val nextSpaceId =
                    state.selectedSpaceId.takeIf { availableSpaceIds.contains(it) } ?: ALL_ROOMS_SPACE_ID
                val nextRoomId = state.selectedRoomId?.takeIf { availableRoomIds.contains(it) }
                state.copy(selectedSpaceId = nextSpaceId, selectedRoomId = nextRoomId)
            }
        }
    }

    fun ensureRoomSelected(accountId: String, availableRoomIds: List<String>) {
        mutableState.update { current ->
            current.updateAccount(accountId) { state ->
                val nextRoomId = state.selectedRoomId?.takeIf { availableRoomIds.contains(it) } ?: availableRoomIds.firstOrNull()
                if (nextRoomId == state.selectedRoomId) state else state.copy(selectedRoomId = nextRoomId)
            }
        }
    }
}

private inline fun Map<String, AppNavigationState>.updateAccount(
    accountId: String,
    update: (AppNavigationState) -> AppNavigationState,
): Map<String, AppNavigationState> {
    val current = this[accountId] ?: AppNavigationState()
    val next = update(current)
    return if (next == current && containsKey(accountId)) this else this + (accountId to next)
}
