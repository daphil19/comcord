package dev.phillipslabs.comcord

import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.classicLoginWithPassword
import de.connect2x.trixnity.clientserverapi.model.authentication.IdentifierType
import de.connect2x.trixnity.messenger.MatrixClients
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create
import de.connect2x.trixnity.messenger.multi.singleModeMatrixMessenger
import de.connect2x.trixnity.messenger.secrets.SecretByteArrays
import de.connect2x.trixnity.messenger.secrets.SecretByteArraysImpl
import de.connect2x.trixnity.messenger.util.RootPath
import io.ktor.http.Url
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toPath
import org.koin.dsl.module
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrixnityMatrixSourceSmokeTest {
    @Test
    fun passwordLoginSyncRoomListAndLogout() = runBlocking {
        SynapseContainer().start().use { synapse ->
            val storage = Files.createTempDirectory("comcord-messenger-").toFile()
            try {
                val multi = MatrixMultiMessenger.create {
                    comcordMatrixConfig()
                    appId = "dev.phillipslabs.comcord.smoke"
                    messengerConfiguration {
                        // Disposable test secrets must never consult the host OS keychain.
                        modulesFactories += {
                            module {
                                single<SecretByteArrays> { SecretByteArraysImpl(get(), lazy { emptyList() }) }
                            }
                        }
                    }
                    modulesFactories += { module { single { RootPath(storage.absolutePath.toPath()) } } }
                }
                try {
                    withTimeout(90_000) {
                        val messenger = multi.singleModeMatrixMessenger().first()
                        val clients = messenger.di.get<MatrixClients>()
                        clients.isInitialized.first { it }
                        val auth = MatrixClientAuthProviderData.classicLoginWithPassword(
                            baseUrl = Url(synapse.url),
                            identifier = IdentifierType.User("alice"),
                            password = "alicepass",
                        ).getOrThrow()
                        assertEquals(MatrixClients.CreateResult.Success, clients.create(auth))
                        val client = clients.first { it.isNotEmpty() }.values.single()
                        val roomId = client.api.room.createRoom(name = "Smoke Lobby").getOrThrow()
                        // There is no SDK MainViewModel in this fixture to start sync.
                        client.startSync()
                        val source = messenger.shellSources().first { it != null }!!
                        assertEquals("@alice:localhost", source.accountId)
                        val directory = source.directory.first { state ->
                            state.roomsByGuild[ALL_ROOMS_GUILD_ID].orEmpty().any { it.id == roomId.full }
                        }
                        assertEquals("Smoke Lobby", directory.roomsByGuild[ALL_ROOMS_GUILD_ID]!!.single().name)
                        source.logout().getOrThrow()
                        assertTrue(clients.value.isEmpty())
                    }
                } finally {
                    multi.closeSuspending()
                }
            } finally {
                storage.deleteRecursively()
            }
        }
    }
}
