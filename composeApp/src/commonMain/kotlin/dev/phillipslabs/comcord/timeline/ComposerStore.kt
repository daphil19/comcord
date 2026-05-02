package dev.phillipslabs.comcord.timeline

import dev.phillipslabs.comcord.rooms.RoomSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ComposerState(
    val roomId: String? = null,
    val draft: String = "",
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val placeholder: String = "Select a room to start chatting",
) {
    val canSend: Boolean = roomId != null && draft.isNotBlank() && !isSending
}

class ComposerStore(
    private val repository: TimelineRepository,
) {
    private data class ComposerContext(
        val accountId: String,
        val room: RoomSummary?,
    )

    private var selectedContext: ComposerContext? = null
    private val draftsByAccountAndRoom = mutableMapOf<String, String>()
    private val mutableState = MutableStateFlow(ComposerState())
    val state: StateFlow<ComposerState> = mutableState.asStateFlow()

    fun setContext(
        accountId: String,
        room: RoomSummary?,
    ) {
        selectedContext = ComposerContext(accountId, room)
        val draft = room?.let { draftsByAccountAndRoom[draftKey(accountId, it.id)] }.orEmpty()
        mutableState.value =
            ComposerState(
                roomId = room?.id,
                draft = draft,
                placeholder =
                    if (room == null) {
                        "Select a room to start chatting"
                    } else {
                        "Message ${room.name}"
                    },
            )
    }

    fun updateDraft(draft: String) {
        selectedContext?.let { context ->
            context.room?.let { room ->
                draftsByAccountAndRoom[draftKey(context.accountId, room.id)] = draft
            }
        }
        mutableState.update { current ->
            current.copy(draft = draft, errorMessage = null)
        }
    }

    suspend fun sendMessage(
        senderId: String,
        senderName: String,
    ) {
        val context = selectedContext ?: return
        val room = context.room ?: return
        val draft = state.value.draft.trim()
        if (draft.isEmpty()) return

        mutableState.update { current ->
            current.copy(isSending = true, errorMessage = null)
        }

        repository
            .sendMessage(room, senderId, senderName, draft)
            .onSuccess {
                draftsByAccountAndRoom.remove(draftKey(context.accountId, room.id))
                if (selectedContext == context) {
                    mutableState.update { current ->
                        current.copy(draft = "", isSending = false, errorMessage = null)
                    }
                }
            }.onFailure { error ->
                if (selectedContext == context) {
                    mutableState.update { current ->
                        current.copy(isSending = false, errorMessage = error.message ?: "Unable to send message.")
                    }
                }
            }
    }

    private fun draftKey(
        accountId: String,
        roomId: String,
    ): String = "$accountId::$roomId"
}
