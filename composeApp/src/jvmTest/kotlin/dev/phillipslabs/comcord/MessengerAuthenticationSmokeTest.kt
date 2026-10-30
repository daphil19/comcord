package dev.phillipslabs.comcord

import com.arkivanov.decompose.DecomposeSettings
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.messenger.MatrixClients
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create
import de.connect2x.trixnity.messenger.secrets.SecretByteArrays
import de.connect2x.trixnity.messenger.secrets.SecretByteArraysImpl
import de.connect2x.trixnity.messenger.util.RootPath
import de.connect2x.trixnity.messenger.viewmodel.RootRouter
import de.connect2x.trixnity.messenger.viewmodel.RootViewModel
import de.connect2x.trixnity.messenger.viewmodel.connecting.AddMatrixAccountMethod
import de.connect2x.trixnity.messenger.viewmodel.connecting.AddMatrixAccountState
import de.connect2x.trixnity.messenger.viewmodel.connecting.AddMatrixAccountViewModel
import de.connect2x.trixnity.messenger.viewmodel.util.toFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toPath
import org.koin.dsl.module
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Real workflow/storage test; native rendering and host event dispatch are verified separately. */
@OptIn(ExperimentalCoroutinesApi::class)
class MessengerAuthenticationSmokeTest {
    @Test
    fun discoveryAndPasswordRetryRestoreTheSameDevice() {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            val originalSettings = DecomposeSettings.settings
            // Official Decompose setting: this headless test substitutes one serialized UI thread.
            DecomposeSettings.settings = originalSettings.copy(mainThreadCheckEnabled = false)
            Dispatchers.setMain(dispatcher)
            try {
                runBlocking(dispatcher) {
                    SynapseContainer().start().use { synapse ->
                        SendFaultProxy(synapse.url).use { proxy ->
                            val storage = Files.createTempDirectory("comcord-auth24-").toFile()
                            val factory = messengerFactory(storage, proxy.url, dispatcher)
                            var host = ComcordApplicationHost(dispatcher)
                            try {
                                withTimeout(120_000) {
                                    proxy.discoveryUnavailable = true
                                    host.resume()
                                    host.initialize(factory)
                                    val ready = assertIs<ComcordApplicationState.Ready>(host.state.value)
                                    authenticate(ready.rootViewModel, proxy)
                                    val client =
                                        ready.messenger.di
                                            .get<MatrixClients>()
                                            .first { it.isNotEmpty() }
                                            .values
                                            .single()
                                    assertForegroundSync(host, client)
                                    val room =
                                        client.api.room
                                            .createRoom(name = "Restored Channel")
                                            .getOrThrow()
                                    awaitChannel(ready, room.full)
                                    val device = client.deviceId
                                    host.closeSuspending()
                                    assertEquals(ComcordApplicationState.Closed, host.state.value)
                                    host = ComcordApplicationHost(dispatcher)
                                    host.resume()
                                    host.initialize(factory)
                                    assertRestored(assertIs(host.state.value), room.full, device)
                                }
                            } finally {
                                host.closeSuspending()
                                storage.deleteRecursively()
                            }
                        }
                    }
                }
            } finally {
                Dispatchers.resetMain()
                DecomposeSettings.settings = originalSettings
            }
        }
    }

    private fun messengerFactory(
        storage: File,
        homeserver: String,
        dispatcher: CoroutineDispatcher,
    ): suspend () -> MatrixMultiMessenger =
        {
            MatrixMultiMessenger.create(coroutineContext = dispatcher) {
                comcordMatrixConfig()
                appId = "dev.phillipslabs.comcord.auth24"
                messengerConfiguration {
                    defaultHomeServer = homeserver
                    modulesFactories += {
                        module { single<SecretByteArrays> { SecretByteArraysImpl(get(), lazy { emptyList() }) } }
                    }
                }
                modulesFactories += { module { single { RootPath(storage.absolutePath.toPath()) } } }
            }
        }

    private suspend fun authenticate(
        root: RootViewModel,
        proxy: SendFaultProxy,
    ) {
        val discovery =
            (
                root.stack
                    .toFlow()
                    .first { it.active.instance is RootRouter.Wrapper.AddMatrixAccount }
                    .active.instance as RootRouter.Wrapper.AddMatrixAccount
            ).viewModel
        val outage =
            discovery.serverDiscoveryState.first {
                it is AddMatrixAccountViewModel.ServerDiscoveryState.Failure
            }
        assertTrue((outage as AddMatrixAccountViewModel.ServerDiscoveryState.Failure).message.isNotBlank())
        proxy.discoveryUnavailable = false
        // The same action as Retry discovery: the SDK TextField epoch retriggers discovery.
        discovery.serverUrl.update(discovery.serverUrl.value.text)
        val methods =
            discovery.serverDiscoveryState.first {
                it is AddMatrixAccountViewModel.ServerDiscoveryState.Success
            } as AddMatrixAccountViewModel.ServerDiscoveryState.Success
        discovery.selectAddMatrixAccountMethod(
            methods.addMatrixAccountMethods.filterIsInstance<AddMatrixAccountMethod.Password>().single(),
        )
        val login =
            (
                root.stack
                    .toFlow()
                    .first { it.active.instance is RootRouter.Wrapper.PasswordLogin }
                    .active.instance as RootRouter.Wrapper.PasswordLogin
            ).viewModel
        login.username.update("alice")
        login.password.update("wrong-password")
        login.canLogin.first { it }
        login.tryLogin()
        val failure = login.addMatrixAccountState.first { it is AddMatrixAccountState.Failure }
        assertTrue((failure as AddMatrixAccountState.Failure).message.isNotBlank())
        login.password.update("alicepass")
        login.tryLogin()
        root.stack.toFlow().first { it.active.instance is RootRouter.Wrapper.Main }
    }

    private suspend fun assertForegroundSync(
        host: ComcordApplicationHost,
        client: MatrixClient,
    ) {
        client.syncState.first { it == SyncState.RUNNING }
        host.stop()
        client.syncState.first { it == SyncState.STOPPED }
        host.resume()
        client.syncState.first { it == SyncState.RUNNING }
    }

    private suspend fun awaitChannel(
        ready: ComcordApplicationState.Ready,
        roomId: String,
    ) {
        ready.messenger.accountSources().first { it != null }!!.directory.first {
            it.channels.any { channel -> channel.id == roomId }
        }
    }

    private suspend fun assertRestored(
        ready: ComcordApplicationState.Ready,
        roomId: String,
        device: String,
    ) {
        ready.rootViewModel.stack
            .toFlow()
            .first { it.active.instance is RootRouter.Wrapper.Main }
        val restoredClient =
            ready.messenger.di
                .get<MatrixClients>()
                .first { it.isNotEmpty() }
                .values
                .single()
        assertEquals("@alice:localhost", restoredClient.userId.full)
        assertEquals(device, restoredClient.deviceId)
        awaitChannel(ready, roomId)
    }
}
