package dev.phillipslabs.comcord

import com.arkivanov.decompose.DecomposeSettings
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create
import de.connect2x.trixnity.messenger.secrets.SecretByteArrays
import de.connect2x.trixnity.messenger.secrets.SecretByteArraysImpl
import de.connect2x.trixnity.messenger.util.RootPath
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toPath
import org.koin.dsl.module
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors

/** Headless real workflows: replace only Main; SDK clients/workers keep their production dispatchers. */
class MessengerWorkflowFixture private constructor(
    private val dispatcher: CoroutineDispatcher,
    val storage: File,
    val proxy: SendFaultProxy,
) {
    private val hosts = mutableListOf<ComcordApplicationHost>()

    fun createHost(): ComcordApplicationHost = ComcordApplicationHost(dispatcher).also(hosts::add)

    fun messengerFactory(
        host: ComcordApplicationHost,
        storageRoot: File = storage,
    ): suspend () -> MatrixMultiMessenger =
        {
            MatrixMultiMessenger.create {
                comcordMatrixConfig(host)
                appId = "dev.phillipslabs.comcord.workflow-smoke"
                messengerConfiguration {
                    defaultHomeServer = proxy.url
                    // Only disposable test secrets bypass the OS keychain.
                    modulesFactories += {
                        module { single<SecretByteArrays> { SecretByteArraysImpl(get(), lazy { emptyList() }) } }
                    }
                }
                modulesFactories += { module { single { RootPath(storageRoot.absolutePath.toPath()) } } }
            }
        }

    private suspend fun exercise(block: suspend MessengerWorkflowFixture.() -> Unit) {
        val outcome = runCatching { withTimeout(SCENARIO_TIMEOUT_MS) { block() } }
        val cleanup = withContext(NonCancellable) { hosts.asReversed().map { runCatching { it.closeSuspending() } } }
        val original = outcome.exceptionOrNull()
        if (original != null) {
            cleanup.mapNotNull { it.exceptionOrNull() }.forEach(original::addSuppressed)
            throw original
        }
        cleanup.forEach { it.getOrThrow() }
    }

    companion object {
        private const val SCENARIO_TIMEOUT_MS = 120_000L

        @OptIn(ExperimentalCoroutinesApi::class)
        fun run(block: suspend MessengerWorkflowFixture.() -> Unit) {
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
                val originalSettings = DecomposeSettings.settings
                // Official test setting: a serialized headless Main replaces the native UI thread.
                DecomposeSettings.settings = originalSettings.copy(mainThreadCheckEnabled = false)
                Dispatchers.setMain(dispatcher)
                try {
                    runBlocking(dispatcher) { runWithResources(dispatcher, block) }
                } finally {
                    Dispatchers.resetMain()
                    DecomposeSettings.settings = originalSettings
                }
            }
        }

        private suspend fun runWithResources(
            dispatcher: CoroutineDispatcher,
            block: suspend MessengerWorkflowFixture.() -> Unit,
        ) {
            SynapseContainer().start().use { synapse ->
                SendFaultProxy(synapse.url).use { proxy ->
                    val storage = Files.createTempDirectory("comcord-workflow-").toFile()
                    try {
                        MessengerWorkflowFixture(dispatcher, storage, proxy).exercise(block)
                    } finally {
                        storage.deleteRecursively()
                    }
                }
            }
        }
    }
}
