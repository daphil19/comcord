package dev.phillipslabs.comcord

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.messenger.MatrixClients
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.viewmodel.RootRouter
import de.connect2x.trixnity.messenger.viewmodel.RootViewModel
import de.connect2x.trixnity.messenger.viewmodel.connecting.AddMatrixAccountMethod
import de.connect2x.trixnity.messenger.viewmodel.connecting.AddMatrixAccountState
import de.connect2x.trixnity.messenger.viewmodel.connecting.AddMatrixAccountViewModel
import de.connect2x.trixnity.messenger.viewmodel.initialsync.InitialSyncRouter
import de.connect2x.trixnity.messenger.viewmodel.util.toFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Real workflow/storage test; native rendering and host event dispatch are verified separately. */
class MessengerAuthenticationSmokeTest {
    @Test
    fun discoveryAndPasswordRetryRestoreTheSameDevice() =
        MessengerWorkflowFixture.run {
            exerciseAuthentication(this)
        }

    private suspend fun exerciseAuthentication(fixture: MessengerWorkflowFixture) {
        val proxy = fixture.proxy
        var host = fixture.createHost()
        val factory: suspend () -> MatrixMultiMessenger = { fixture.messengerFactory(host)() }

        proxy.discoveryUnavailable = true
        host.resume()
        host.initialize { throw IOException("Controlled local storage failure") }
        assertIs<ComcordApplicationState.Failure>(host.state.value)
        host.initialize(factory)
        val ready = assertIs<ComcordApplicationState.Ready>(host.state.value)
        stage("discovery/password retry") { authenticate(ready.rootViewModel, proxy, host) }
        val client =
            ready.messenger.di
                .get<MatrixClients>()
                .first { it.isNotEmpty() }
                .values
                .single()
        stage("held initial sync across stop") { assertHeldSync(host, client, proxy) }
        val main =
            (ready.rootViewModel.stack.value.active.instance as RootRouter.Wrapper.Main)
                .viewModel
        stage("settled initial sync") {
            main.initialSyncStack.toFlow().first {
                it.active.instance ==
                    InitialSyncRouter.Wrapper.None
            }
        }
        stage("foreground lifecycle") { assertForegroundSync(host, client) }
        val room =
            client.api.room
                .createRoom(name = "Restored Channel")
                .getOrThrow()
        awaitChannel(ready, room.full)
        val device = client.deviceId
        host.closeSuspending()
        assertEquals(ComcordApplicationState.Closed, host.state.value)
        host = fixture.createHost()
        stage("storage bootstrap failure") { assertStorageFailure(fixture, host) }
        val restored =
            stage("restore while backgrounded") { restoreWhileStopped(host, factory) }
        stage(
            "persistent device restoration",
        ) { assertRestored(restored, room.full, device) }
    }

    private suspend fun assertStorageFailure(
        fixture: MessengerWorkflowFixture,
        host: ComcordApplicationHost,
    ) {
        val invalidRoot =
            fixture.storage.resolve("unusable-storage-root").apply { writeText("A file cannot contain client storage") }
        host.initialize(fixture.messengerFactory(host, invalidRoot))
        assertIs<ComcordApplicationState.Failure>(host.state.value)
    }

    private suspend fun restoreWhileStopped(
        host: ComcordApplicationHost,
        factory: suspend () -> MatrixMultiMessenger,
    ): ComcordApplicationState.Ready =
        coroutineScope {
            val created = CompletableDeferred<MatrixMultiMessenger>()
            val initialization = async { host.initialize { factory().also { created.complete(it) } } }
            val messenger =
                created
                    .await()
                    .activeMatrixMessenger
                    .filterNotNull()
                    .first()
            val client =
                messenger.di
                    .get<MatrixClients>()
                    .first { it.isNotEmpty() }
                    .values
                    .single()
            assertEquals(ComcordApplicationState.Loading, host.state.value)
            assertEquals(SyncState.STOPPED, client.syncState.value)
            host.resume()
            initialization.await()
            assertIs<ComcordApplicationState.Ready>(host.state.value)
        }

    private suspend fun <T> stage(
        name: String,
        action: suspend () -> T,
    ): T {
        println("AUTH24 start $name")
        return try {
            withTimeout(30_000) { action() }.also { println("AUTH24 PASS $name") }
        } catch (failure: TimeoutCancellationException) {
            throw AssertionError("Timed out during $name", failure)
        }
    }

    private suspend fun authenticate(
        root: RootViewModel,
        proxy: SendFaultProxy,
        host: ComcordApplicationHost,
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
        proxy.loginGate.hold()
        proxy.initialSyncGate.hold()
        login.tryLogin()
        while (!proxy.loginGate.arrived) delay(10)
        host.stop()
        proxy.loginGate.release()
        root.stack.toFlow().first { it.active.instance is RootRouter.Wrapper.Main }
    }

    private suspend fun assertHeldSync(
        host: ComcordApplicationHost,
        client: MatrixClient,
        proxy: SendFaultProxy,
    ) {
        assertEquals(SyncState.STOPPED, client.syncState.value)
        assertEquals(null, withTimeoutOrNull(200) { client.syncState.first { it != SyncState.STOPPED } })
        host.resume()
        while (!proxy.initialSyncGate.arrived) delay(10)
        host.stop()
        proxy.initialSyncGate.release()
        stage("initial sync stopped") { client.syncState.first { it == SyncState.STOPPED } }
        assertEquals(null, withTimeoutOrNull(200) { client.syncState.first { it != SyncState.STOPPED } })
        host.resume()
    }

    private suspend fun assertForegroundSync(
        host: ComcordApplicationHost,
        client: MatrixClient,
    ) {
        stage("sync RUNNING") { client.syncState.first { it == SyncState.RUNNING } }
        host.stop()
        stage("sync STOPPED") { client.syncState.first { it == SyncState.STOPPED } }
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
        restoredClient.syncState.first { it == SyncState.RUNNING }
        val fresh =
            restoredClient.api.room
                .createRoom(name = "After restart")
                .getOrThrow()
        awaitChannel(ready, fresh.full)
    }
}
