package dev.phillipslabs.comcord

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.room.message.text
import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.classicLoginWithPassword
import de.connect2x.trixnity.clientserverapi.model.authentication.IdentifierType
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.InitialStateEvent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.messenger.MatrixClients
import de.connect2x.trixnity.messenger.MatrixMessenger
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessengerConfiguration
import de.connect2x.trixnity.messenger.multi.singleModeMatrixMessenger
import de.connect2x.trixnity.messenger.secrets.SecretByteArrays
import de.connect2x.trixnity.messenger.secrets.SecretByteArraysImpl
import de.connect2x.trixnity.messenger.viewmodel.MatrixClientViewModelContextImpl
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.TimelineViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.TimelineViewModelFactory
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.BaseTimelineElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.OutboxElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.TimelineElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.message.EncryptedErrorTimelineElementViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.message.EncryptedWaitTimelineElementViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.message.RoomMessageTimelineElementViewModel
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.koin.dsl.module
import kotlin.random.Random

const val PLATFORM_GATE_ENDPOINT = "http://127.0.0.1:18723"

/** Opt-in disposable storage namespace. This experiment does not validate OS secret protection. */
fun MatrixMultiMessengerConfiguration.platformGateConfiguration() {
    comcordMatrixConfig()
    appId = "dev.phillipslabs.comcord.gate${Random.nextInt(1, Int.MAX_VALUE)}"
    println("PLATFORM_GATE disposable namespace $appId")
    messengerConfiguration {
        timelineViewStateSupported = false
        timelineInitialSize = 5
        modulesFactories += {
            module { single<SecretByteArrays> { SecretByteArraysImpl(get(), lazy { emptyList() }) } }
        }
    }
}

/** Real host callbacks own foreground sync. Scenario progress is an evidence journal, not timeline state. */
class PlatformGateHost {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var gate: PlatformMessengerGate? = null
    private var foreground = true
    private var destroyed = false
    var hostEvents by mutableStateOf("created")
        private set

    fun resume() {
        foreground = true
        hostEvents += " → resume"
        println("PLATFORM_GATE HOST resume")
        scope.launch { gate?.foreground(true) }
    }

    fun stop() {
        foreground = false
        hostEvents += " → stop"
        println("PLATFORM_GATE HOST stop")
        scope.launch { gate?.foreground(false) }
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        hostEvents += " → destroy"
        println("PLATFORM_GATE HOST destroy")
        scope.launch {
            gate?.close()
            gate = null
            scope.cancel()
        }
    }

    @Composable
    fun Content(factory: suspend () -> MatrixMultiMessenger, onClose: () -> Unit = { destroy() }) {
        var failure by remember { mutableStateOf<String?>(null) }
        var ready by remember { mutableStateOf<PlatformMessengerGate?>(null) }
        LaunchedEffect(Unit) {
            try {
                val created = PlatformMessengerGate.create(factory())
                gate = created
                if (destroyed) created.close() else {
                    created.foreground(foreground)
                    ready = created
                }
            } catch (error: Throwable) {
                failure = error.toString()
                println("PLATFORM_GATE FAIL initialization $error")
            }
        }
        MaterialTheme {
            Surface(Modifier.fillMaxSize()) {
                Column {
                    Text("Messenger platform gate #23")
                    Text("Host: $hostEvents")
                    Button(onClick = onClose) { Text("Close host") }
                    failure?.let { Text("FAIL: $it") }
                    ready?.Content() ?: Text("Initializing disposable workflow…")
                }
            }
        }
    }
}

class PlatformMessengerGate private constructor(
    private val multi: MatrixMultiMessenger,
    private val messenger: MatrixMessenger,
    private val client: MatrixClient,
    private val scope: CoroutineScope,
    private val store: AccountStore,
    private val roomA: RoomId,
    private val roomB: RoomId,
) {
    private class Session(val room: RoomId, val lifecycle: LifecycleRegistry, val job: Job, val vm: TimelineViewModel) {
        var pending = 0
    }

    private val sessions = mutableListOf<Session>()
    private var current: Session? = null
    private var visible by mutableStateOf(true)
    private var journal by mutableStateOf("Running…")
    private var failure by mutableStateOf<String?>(null)
    private var foreground = true
    private var encryptedRoom by mutableStateOf<RoomId?>(null)

    suspend fun foreground(active: Boolean) {
        foreground = active
        val clients = messenger.di.get<MatrixClients>()
        clients.value.values.forEach { if (active) it.startSync() else it.stopSync() }
        current?.let { if (active) it.lifecycle.resume() else it.lifecycle.stop() }
        println("PLATFORM_GATE SYNC ${if (active) "started" else "stopped"} accounts=${clients.value.size}")
    }

    @Composable
    fun Content() {
        val account by store.state.collectAsState()
        Column {
            Text(journal, Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()))
            failure?.let { Text("FAIL: $it") }
            Row {
                Button(onClick = { store.selectRoom(roomA.full) }) { Text("A") }
                Button(onClick = { store.selectRoom(roomB.full) }) { Text("B") }
                encryptedRoom?.let { room ->
                    Button(onClick = { store.selectRoom(room.full) }) { Text("Encrypted") }
                }
                Button(onClick = { visible = false }) { Text("Dispose room") }
            }
            if (visible) account.selectedRoomId?.let { selected ->
                key(selected) {
                    val session = remember(selected) { open(RoomId(selected)) }
                    DisposableEffect(session) {
                        onDispose {
                            session.lifecycle.destroy()
                            if (current === session) current = null
                        }
                    }
                    Timeline(session)
                }
            }
        }
        LaunchedEffect(Unit) {
            try { runScenario() } catch (error: Throwable) {
                failure = error.toString()
                println("PLATFORM_GATE FAIL scenario $error")
            }
        }
    }

    private fun open(room: RoomId): Session {
        val lifecycle = LifecycleRegistry()
        val job = SupervisorJob()
        val context = MatrixClientViewModelContextImpl(
            messenger.di, DefaultComponentContext(lifecycle), client.userId,
            Dispatchers.Default + job, "platform-gate:${room.full}",
        )
        val vm = TimelineViewModelFactory.create(
            context, room, onBack = {}, onOpenRoomSettings = {}, onOpenUserProfile = {},
            onOpenMention = { _, _ -> }, onOpenMetadata = {},
        )
        if (foreground) lifecycle.resume()
        return Session(room, lifecycle, job, vm).also { sessions += it; current = it }
    }

    @Composable
    private fun Timeline(session: Session) {
        val elements by session.vm.elements.collectAsState()
        val scroll by session.vm.scrollTo.collectAsState()
        val uiScope = rememberCoroutineScope()
        val input = session.vm.inputAreaViewModel
        val text by input.textField.collectAsState()
        val sendEnabled by input.isSendEnabled.collectAsState()
        LaunchedEffect(session) {
            session.pending++
            try { while (isActive) session.vm.loadAfter() } finally { session.pending-- }
        }
        LaunchedEffect(scroll) { scroll?.let { session.vm.finishedScrollTo(it) } }
        Row {
            Button(onClick = { uiScope.launch { session.vm.loadBefore() } }) { Text("Older") }
            Button(onClick = {
                uiScope.launch { HttpClient().use { it.post("$PLATFORM_GATE_ENDPOINT/_gate/hold-next-send") } }
            }) { Text("Hold next send") }
        }
        TextField(value = text.text, onValueChange = { input.textField.update(it) })
        Button(onClick = { input.sendMessage() }, enabled = sendEnabled) { Text("Send") }
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            elements.forEach { holder ->
                key(holder.key) {
                    val sender by holder.sender.collectAsState()
                    val element by holder.element.collectAsState()
                    val sent by holder.isSent.collectAsState()
                    val error by holder.sendError.collectAsState()
                    Text("${sender?.initials ?: "?"}  ${sender?.name.orEmpty()} ${holder.formattedTime}")
                    Text(when (val value = element) {
                        is RoomMessageTimelineElementViewModel<*> -> value.body
                        is EncryptedWaitTimelineElementViewModel -> "Unable to decrypt message: waiting for keys"
                        is EncryptedErrorTimelineElementViewModel -> "Unable to decrypt message: ${value.error}"
                        else -> "Loading event"
                    })
                    if (!sent) Text(if (error == null) "Sending" else "Failed: $error")
                }
            }
        }
    }

    private fun bodyCount(body: String) = current?.vm?.elements?.value?.count {
        (it.element.value as? RoomMessageTimelineElementViewModel<*>)?.body == body
    } ?: 0

    private suspend fun await(label: String, condition: () -> Boolean) {
        withTimeout(45_000) { while (!condition()) delay(25) }
        delay(150) // Allow the actual Compose scene to present the state being observed.
        println("PLATFORM_GATE OBSERVED $label")
    }

    private fun pass(label: String) {
        journal += "\nPASS $label"
        println("PLATFORM_GATE PASS $label")
    }

    private suspend fun runScenario() {
        await("initial body") { bodyCount("Platform A39") == 1 }
        val a = checkNotNull(current)
        check(bodyCount("Platform A0") == 0)
        pass("initial Messenger rendering")
        a.vm.loadBefore()
        await("paged body") { bodyCount("Platform A0") == 1 }
        pass("older paging")
        send(roomA, "Platform incoming")
        await("incoming body") { bodyCount("Platform incoming") == 1 }
        pass("incoming updates")
        HttpClient().use { it.post("$PLATFORM_GATE_ENDPOINT/_gate/hold-next-send") }
        a.vm.inputAreaViewModel.textField.update("Platform outgoing")
        a.vm.inputAreaViewModel.sendMessage()
        await("pending outbox") {
            a.vm.elements.value.any { it is OutboxElementHolderViewModel && !it.isSent.value }
        }
        pass("visible pending outbox")
        await("single confirmed event") {
            a.vm.elements.value.any { it is TimelineElementHolderViewModel && body(it) == "Platform outgoing" } &&
                bodyCount("Platform outgoing") == 1
        }
        pass("outbox reconciliation")
        await("pending forward work") { a.pending == 1 }
        store.selectRoom(roomB.full)
        await("B selection") { bodyCount("Platform B") == 1 }
        await("A destruction") { a.pending == 0 && a.job.children.none() }
        val frozen = a.vm.elements.value
        send(roomA, "Late A")
        send(roomB, "Platform B incoming")
        await("B incoming") { bodyCount("Platform B incoming") == 1 }
        check(a.vm.elements.value == frozen)
        check(store.state.value.selectedRoomId == roomB.full)
        pass("selection A→B and obsolete cleanup")
        val encrypted = prepareEncrypted()
        encryptedRoom = encrypted
        store.selectRoom(encrypted.full)
        await("missing key holder") {
            current?.vm?.elements?.value?.any { it.element.value is EncryptedWaitTimelineElementViewModel } == true
        }
        pass("undecryptable message retained")
        val encryptedSession = checkNotNull(current)
        encryptedSession.vm.inputAreaViewModel.textField.update("Platform encrypted readable")
        encryptedSession.vm.inputAreaViewModel.sendMessage()
        await("encrypted confirmation") {
            current?.vm?.elements?.value?.any {
                it is TimelineElementHolderViewModel && body(it) == "Platform encrypted readable"
            } == true && bodyCount("Platform encrypted readable") == 1
        }
        val holder = encryptedSession.vm.elements.value.filterIsInstance<TimelineElementHolderViewModel>()
            .single { body(it) == "Platform encrypted readable" }
        check(client.api.room.getEvent(encrypted, holder.eventId).getOrThrow().content is EncryptedMessageEventContent)
        pass("real encrypted content readable")
        store.selectRoom(roomB.full)
        await("encrypted wait disposed") { encryptedSession.job.children.none() && encryptedSession.pending == 0 }
        pass("decryption room cleanup")
        val b = checkNotNull(current)
        visible = false
        await("all room work destroyed") { current == null && sessions.all { it.job.children.none() && it.pending == 0 } }
        val frozenB = b.vm.elements.value
        val late = send(roomB, "After room destruction")
        client.room.getById(roomB).first { it?.lastEventId == late }
        check(b.vm.elements.value == frozenB)
        pass("destroyed room remains frozen")
        visible = true
        store.selectRoom(encrypted.full)
        journal += "\nWORKFLOW PASS — exercise host background/resume next"
        println("PLATFORM_GATE WORKFLOW PASS")
    }

    private fun body(holder: BaseTimelineElementHolderViewModel) =
        (holder.element.value as? RoomMessageTimelineElementViewModel<*>)?.body

    private suspend fun send(room: RoomId, body: String) = client.api.room.sendMessageEvent(
        room, RoomMessageEventContent.TextBased.Text(body),
    ).getOrThrow()

    private suspend fun prepareEncrypted(): RoomId {
        val clients = messenger.di.get<MatrixClients>()
        val bob = login(clients, "bob", "bobpass")
        bob.startSync()
        val room = bob.api.room.createRoom(name = "Platform encrypted", initialState = listOf(
            InitialStateEvent(EncryptionEventContent(), ""),
            InitialStateEvent(HistoryVisibilityEventContent(HistoryVisibilityEventContent.HistoryVisibility.SHARED), ""),
        )).getOrThrow()
        bob.room.getById(room).first { it != null }
        val transaction = bob.room.sendMessage(room) { text("Platform missing-key history") }
        val event = bob.room.getOutbox(room, transaction).first { it?.eventId != null }!!.eventId!!
        check(bob.api.room.getEvent(room, event).getOrThrow().content is EncryptedMessageEventContent)
        bob.stopSync()
        bob.api.room.inviteUser(room, client.userId).getOrThrow()
        client.api.room.joinRoom(room).getOrThrow()
        store.state.first { it.rooms.any { channel -> channel.id == room.full } }
        return room
    }

    suspend fun close() {
        scope.cancel()
        sessions.forEach { it.lifecycle.destroy(); it.job.cancel() }
        multi.closeSuspending()
        println("PLATFORM_GATE HOST cleanup complete")
    }

    companion object {
        suspend fun create(multi: MatrixMultiMessenger): PlatformMessengerGate {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            try {
                return withTimeout(120_000) {
                    val messenger = multi.singleModeMatrixMessenger().first()
                    val clients = messenger.di.get<MatrixClients>()
                    clients.isInitialized.first { it }
                    val client = login(clients, "alice", "alicepass")
                    val a = client.api.room.createRoom(name = "Platform A").getOrThrow()
                    val b = client.api.room.createRoom(name = "Platform B").getOrThrow()
                    repeat(40) { sendIndex ->
                        client.api.room.sendMessageEvent(a, RoomMessageEventContent.TextBased.Text("Platform A$sendIndex")).getOrThrow()
                    }
                    client.api.room.sendMessageEvent(b, RoomMessageEventContent.TextBased.Text("Platform B")).getOrThrow()
                    client.startSync()
                    val source = TrixnityMatrixSource(client, messenger.di.get()) { Result.success(Unit) }
                    val store = AccountStore(source, scope)
                    store.state.first { it.rooms.any { room -> room.id == a.full } && it.rooms.any { room -> room.id == b.full } }
                    store.selectRoom(a.full)
                    PlatformMessengerGate(multi, messenger, client, scope, store, a, b)
                }
            } catch (failure: Throwable) {
                scope.cancel()
                withContext(NonCancellable) { multi.closeSuspending() }
                throw failure
            }
        }

        private suspend fun login(clients: MatrixClients, name: String, password: String): MatrixClient {
            val auth = MatrixClientAuthProviderData.classicLoginWithPassword(
                baseUrl = Url(PLATFORM_GATE_ENDPOINT), identifier = IdentifierType.User(name), password = password,
            ).getOrThrow()
            check(clients.create(auth) == MatrixClients.CreateResult.Success)
            return clients.first { it.values.any { client -> client.userId.full == "@$name:localhost" } }
                .values.single { it.userId.full == "@$name:localhost" }
        }
    }
}
