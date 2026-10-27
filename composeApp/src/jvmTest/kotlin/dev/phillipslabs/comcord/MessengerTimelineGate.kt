package dev.phillipslabs.comcord

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.testTag
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.classicLoginWithPassword
import de.connect2x.trixnity.clientserverapi.model.authentication.IdentifierType
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.messenger.MatrixClients
import de.connect2x.trixnity.messenger.MatrixMessenger
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create
import de.connect2x.trixnity.messenger.multi.singleModeMatrixMessenger
import de.connect2x.trixnity.messenger.secrets.SecretByteArrays
import de.connect2x.trixnity.messenger.secrets.SecretByteArraysImpl
import de.connect2x.trixnity.messenger.util.RootPath
import de.connect2x.trixnity.messenger.viewmodel.MatrixClientViewModelContextImpl
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.TimelineViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.TimelineViewModelFactory
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.BaseTimelineElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.message.RoomMessageTimelineElementViewModel
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toPath
import org.jetbrains.skia.Image
import org.koin.dsl.module
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/** Disposable experiment. Only AccountStore owns selection; Messenger owns every timeline element. */
class MessengerTimelineGate private constructor(
    private val synapse: SynapseContainer,
    private val storage: File,
    private val multi: MatrixMultiMessenger,
    private val messenger: MatrixMessenger,
    val client: MatrixClient,
    val roomA: RoomId,
    val roomB: RoomId,
    private val accountScope: CoroutineScope,
    val store: AccountStore,
) : AutoCloseable {
    // Handles retained only for lifecycle evidence; no events or SDK presentation state are copied.
    val sessions = mutableListOf<Session>()
    var current: Session? = null
        private set

    class Session(
        val roomId: RoomId,
        val lifecycle: LifecycleRegistry,
        val job: Job,
        val vm: TimelineViewModel,
    ) {
        val pendingLoads = AtomicInteger()
    }

    var visible by mutableStateOf(true)

    private fun open(roomId: RoomId): Session {
        val lifecycle = LifecycleRegistry()
        val job = SupervisorJob()
        val context =
            MatrixClientViewModelContextImpl(
                messenger.di,
                DefaultComponentContext(lifecycle),
                client.userId,
                Dispatchers.Default + job,
                "gate:${roomId.full}",
            )
        val vm =
            TimelineViewModelFactory.create(
                context,
                roomId,
                onBack = {},
                onOpenRoomSettings = {},
                onOpenUserProfile = {},
                onOpenMention = { _, _ -> },
                onOpenMetadata = {},
            )
        lifecycle.resume()
        return Session(roomId, lifecycle, job, vm).also {
            sessions += it
            current = it
        }
    }

    @Composable
    fun Content(modifier: Modifier = Modifier) {
        val account by store.state.collectAsState()
        MaterialTheme {
            Surface(modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Row {
                        Button(onClick = { store.selectRoom(roomA.full) }) { Text("Channel A") }
                        Button(onClick = { store.selectRoom(roomB.full) }) { Text("Channel B") }
                        Button(onClick = { visible = false }) { Text("Destroy fixture") }
                    }
                    account.selectedRoomId?.takeIf { visible }?.let { selected ->
                        androidx.compose.runtime.key(selected) {
                            val session = remember(selected) { open(RoomId(selected)) }
                            DisposableEffect(session) {
                                onDispose {
                                    session.lifecycle.destroy()
                                    // Leave the evidence parent active; SDK destruction must cancel its children.
                                    if (current === session) current = null
                                }
                            }
                            Timeline(session)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Timeline(
        session: Session,
        modifier: Modifier = Modifier,
    ) {
        val vm = session.vm
        val elements by vm.elements.collectAsState()
        val canLoadBefore by vm.canLoadBefore.collectAsState()
        val error by vm.error.collectAsState()
        val scrollTo by vm.scrollTo.collectAsState()
        val scope = rememberCoroutineScope()
        // Deliberately use ONLY the manual viewport mode supported by Messenger.
        LaunchedEffect(vm) {
            session.pendingLoads.incrementAndGet()
            try {
                while (isActive) vm.loadAfter()
            } finally {
                session.pendingLoads.decrementAndGet()
            }
        }
        LaunchedEffect(scrollTo) { scrollTo?.let { vm.finishedScrollTo(it) } }
        Column(modifier) {
            Button(
                enabled = canLoadBefore != false,
                onClick = { scope.launch { vm.loadBefore() } },
            ) { Text("Older messages") }
            error?.let { Text(it) }
            LazyColumn(Modifier.testTag("timeline")) {
                items(elements, key = { it.key }) { holder -> Message(holder) }
            }
        }
    }

    @Composable
    private fun Message(
        holder: BaseTimelineElementHolderViewModel,
        modifier: Modifier = Modifier,
    ) {
        val element by holder.element.collectAsState()
        val sender by holder.sender.collectAsState()
        Row(modifier) {
            val image by (sender?.image ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()
            if (image != null) {
                Image(Image.makeFromEncoded(image!!).toComposeImageBitmap(), "Avatar")
            } else {
                Text(sender?.initials ?: "?")
            }
            Column {
                Text("${sender?.name.orEmpty()} ${holder.formattedTime}")
                Text((element as? RoomMessageTimelineElementViewModel<*>)?.body ?: "Loading message")
            }
        }
    }

    fun hasBody(body: String): Boolean =
        current?.vm?.elements?.value?.any {
            (it.element.value as? RoomMessageTimelineElementViewModel<*>)?.body == body
        } == true

    suspend fun send(
        roomId: RoomId,
        body: String,
    ) = client.api.room
        .sendMessageEvent(
            roomId,
            RoomMessageEventContent.TextBased.Text(body),
        ).getOrThrow()

    override fun close() {
        try {
            accountScope.cancel()
            sessions.forEach {
                try {
                    it.lifecycle.destroy()
                } finally {
                    it.job.cancel()
                }
            }
        } finally {
            runBlocking { releaseResources(multi, synapse, storage) }
        }
    }

    companion object {
        suspend fun start(): MessengerTimelineGate {
            val synapse = SynapseContainer()
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            var storage: File? = null
            var multi: MatrixMultiMessenger? = null
            try {
                synapse.start()
                val clientStorage = Files.createTempDirectory("comcord-gate21-").toFile()
                storage = clientStorage
                val messenger = createMessenger(clientStorage)
                multi = messenger
                return initialize(synapse, clientStorage, messenger, scope)
            } catch (failure: Throwable) {
                scope.cancel()
                try {
                    withContext(NonCancellable) { releaseResources(multi, synapse, storage) }
                } catch (cleanupFailure: Throwable) {
                    failure.addSuppressed(cleanupFailure)
                }
                throw failure
            }
        }

        private suspend fun releaseResources(
            multi: MatrixMultiMessenger?,
            synapse: SynapseContainer,
            storage: File?,
        ) {
            try {
                multi?.closeSuspending()
            } finally {
                try {
                    synapse.close()
                } finally {
                    storage?.deleteRecursively()
                }
            }
        }

        private suspend fun initialize(
            synapse: SynapseContainer,
            storage: File,
            multi: MatrixMultiMessenger,
            scope: CoroutineScope,
        ): MessengerTimelineGate =
            withTimeout(90_000) {
                val messenger = multi.singleModeMatrixMessenger().first()
                val clients = messenger.di.get<MatrixClients>()
                clients.isInitialized.first { it }
                val auth =
                    MatrixClientAuthProviderData
                        .classicLoginWithPassword(
                            baseUrl = Url(synapse.url),
                            identifier = IdentifierType.User("alice"),
                            password = "alicepass",
                        ).getOrThrow()
                check(clients.create(auth) == MatrixClients.CreateResult.Success)
                val client = clients.first { it.isNotEmpty() }.values.single()
                val roomA =
                    client.api.room
                        .createRoom(name = "Gate A")
                        .getOrThrow()
                val roomB =
                    client.api.room
                        .createRoom(name = "Gate B")
                        .getOrThrow()
                repeat(40) { index ->
                    client.api.room
                        .sendMessageEvent(
                            roomA,
                            RoomMessageEventContent.TextBased.Text("Gate A message $index"),
                        ).getOrThrow()
                }
                client.api.room
                    .sendMessageEvent(
                        roomB,
                        RoomMessageEventContent.TextBased.Text("Gate B message"),
                    ).getOrThrow()
                client.startSync()
                val source = messenger.accountSources().first { it != null }!!
                val store = AccountStore(source, scope)
                store.state.first {
                    it.rooms.any { room -> room.id == roomA.full } &&
                        it.rooms.any { room -> room.id == roomB.full }
                }
                store.selectRoom(roomA.full)
                MessengerTimelineGate(synapse, storage, multi, messenger, client, roomA, roomB, scope, store)
            }

        private suspend fun createMessenger(storage: File) =
            MatrixMultiMessenger.create {
                comcordMatrixConfig()
                appId = "dev.phillipslabs.comcord.gate21"
                messengerConfiguration {
                    timelineViewStateSupported = false
                    timelineInitialSize = 5
                    modulesFactories +=
                        {
                            module {
                                single<SecretByteArrays> {
                                    SecretByteArraysImpl(
                                        get(),
                                        lazy { emptyList() },
                                    )
                                }
                            }
                        }
                }
                modulesFactories += { module { single { RootPath(storage.absolutePath.toPath()) } } }
            }
    }
}
