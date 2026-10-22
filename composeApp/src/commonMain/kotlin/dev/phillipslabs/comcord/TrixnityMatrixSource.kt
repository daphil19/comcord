package dev.phillipslabs.comcord

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.messenger.MatrixClients
import de.connect2x.trixnity.messenger.MatrixMessenger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class TrixnityMatrixSource(
    matrixClient: MatrixClient,
    private val logoutAccount: suspend () -> Result<Unit>,
) : MatrixSource {
    override val accountId: String = matrixClient.userId.full
    override val directory: Flow<RoomDirectoryState> = observeRoomDirectory(matrixClient)
    override suspend fun logout(): Result<Unit> = logoutAccount()
}

/** Startup bridge: retain the bootstrap shell's first-account policy. */
fun MatrixMessenger.shellSources(): Flow<MatrixSource?> {
    val clients = di.get<MatrixClients>()
    return clients.map { it.entries.firstOrNull() }
        .distinctUntilChanged()
        .map { entry ->
            entry?.let { (userId, client) -> TrixnityMatrixSource(client) { clients.logout(userId) } }
        }
}
