package dev.phillipslabs.comcord

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.media
import de.connect2x.trixnity.messenger.MatrixClients
import de.connect2x.trixnity.messenger.MatrixMessenger
import de.connect2x.trixnity.messenger.MatrixMessengerAccountSettings
import de.connect2x.trixnity.messenger.MatrixMessengerSettingsHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

class TrixnityMatrixSource(
    private val matrixClient: MatrixClient,
    private val settings: MatrixMessengerSettingsHolder,
    private val logoutAccount: suspend () -> Result<Unit>,
) : MatrixSource {
    override val accountId: String = matrixClient.userId.full
    override val directory: Flow<MatrixDirectory> = observeRoomDirectory(matrixClient)

    override val hiddenViewIds: Flow<Set<String>> =
        settings[matrixClient.userId].map { account ->
            account.hiddenViews()
        }

    override suspend fun setViewHidden(
        id: String,
        hidden: Boolean,
    ) {
        settings.update(matrixClient.userId) { account ->
            val current = account.hiddenViews()
            val updated = if (hidden) current + id else current - id
            this[HIDDEN_VIEWS_KEY] = JsonArray(updated.sorted().map(::JsonPrimitive))
        }
    }

    override fun timeline(roomId: String): RoomTimeline = TrixnityRoomTimeline(matrixClient, roomId)

    override suspend fun loadAvatar(url: String): ByteArray? =
        try {
            matrixClient.media
                .getThumbnail(url, AVATAR_SIZE, AVATAR_SIZE, maxSize = MAX_AVATAR_BYTES)
                .getOrNull()
                ?.toByteArray(maxSize = MAX_AVATAR_BYTES)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    override suspend fun logout(): Result<Unit> = logoutAccount()
}

/** Startup bridge: retain the bootstrap shell's first-account policy. */
fun MatrixMessenger.accountSources(): Flow<MatrixSource?> {
    val clients = di.get<MatrixClients>()
    return clients
        .map { it.entries.firstOrNull() }
        .distinctUntilChanged()
        .map { entry ->
            entry?.let { (userId, client) ->
                TrixnityMatrixSource(client, di.get<MatrixMessengerSettingsHolder>()) { clients.logout(userId) }
            }
        }
}

private const val HIDDEN_VIEWS_KEY = "dev.phillipslabs.comcord.hiddenViews"
private const val AVATAR_SIZE = 64L
private const val MAX_AVATAR_BYTES = 2_000_000L

private fun MatrixMessengerAccountSettings?.hiddenViews(): Set<String> =
    (this?.get(HIDDEN_VIEWS_KEY) as? JsonArray)?.map { it.jsonPrimitive.content }?.toSet().orEmpty()
