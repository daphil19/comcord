package dev.phillipslabs.comcord.account

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.messenger.MatrixClients
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

data class AccountSession(
    val userId: UserId,
    val client: MatrixClient,
)

data class AccountSessionState(
    val sessions: List<AccountSession>,
    val activeSession: AccountSession?,
)

class AccountSessionStore(
    private val matrixClients: MatrixClients,
) {
    private val activeUserId = MutableStateFlow<UserId?>(null)
    private var currentUserIds: List<UserId> = emptyList()

    val state: Flow<AccountSessionState> =
        matrixClients.combine(activeUserId) { clients, requestedActiveUserId ->
            val sessions =
                clients.entries
                    .map { (userId, client) -> AccountSession(userId, client) }
                    .sortedBy { it.userId.full }
            currentUserIds = sessions.map { it.userId }
            val activeSession =
                sessions.firstOrNull { it.userId == requestedActiveUserId }
                    ?: sessions.firstOrNull()
            AccountSessionState(
                sessions = sessions,
                activeSession = activeSession,
            )
        }

    fun selectAccount(userId: UserId) {
        activeUserId.value = userId
    }

    suspend fun logout(userId: UserId) {
        activeUserId.value =
            selectActiveUserAfterLogout(
                currentUserIds = currentUserIds,
                activeUserId = activeUserId.value,
                loggedOutUserId = userId,
            )
        matrixClients.logout(userId)
    }
}

internal fun selectActiveUserAfterLogout(
    currentUserIds: List<UserId>,
    activeUserId: UserId?,
    loggedOutUserId: UserId,
): UserId? {
    val remainingUserIds = currentUserIds.filterNot { it == loggedOutUserId }
    return when {
        activeUserId != loggedOutUserId && activeUserId in remainingUserIds -> activeUserId
        else -> remainingUserIds.firstOrNull()
    }
}
