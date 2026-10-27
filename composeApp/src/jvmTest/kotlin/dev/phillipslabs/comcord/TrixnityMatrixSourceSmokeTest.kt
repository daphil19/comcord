package dev.phillipslabs.comcord

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.classicLoginWithPassword
import de.connect2x.trixnity.clientserverapi.model.authentication.IdentifierType
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.DirectEventContent
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.events.m.space.ChildEventContent
import de.connect2x.trixnity.core.model.events.m.space.ParentEventContent
import de.connect2x.trixnity.messenger.MatrixClients
import de.connect2x.trixnity.messenger.MatrixMessengerAccountSettingsBase
import de.connect2x.trixnity.messenger.MatrixMessengerSettingsHolderImpl
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create
import de.connect2x.trixnity.messenger.multi.singleModeMatrixMessenger
import de.connect2x.trixnity.messenger.secrets.SecretByteArrays
import de.connect2x.trixnity.messenger.secrets.SecretByteArraysImpl
import de.connect2x.trixnity.messenger.settings.FileSystemSettingsStorage
import de.connect2x.trixnity.messenger.util.RootPath
import io.ktor.http.Url
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.dsl.module
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrixnityMatrixSourceSmokeTest {
    @Test
    fun passwordLoginSyncRoomListAndLogout() =
        runBlocking {
            SynapseContainer().start().use { synapse ->
                val storage = Files.createTempDirectory("comcord-messenger-").toFile()
                try {
                    val multi =
                        MatrixMultiMessenger.create {
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
                            val auth =
                                MatrixClientAuthProviderData
                                    .classicLoginWithPassword(
                                        baseUrl = Url(synapse.url),
                                        identifier = IdentifierType.User("alice"),
                                        password = "alicepass",
                                    ).getOrThrow()
                            assertEquals(MatrixClients.CreateResult.Success, clients.create(auth))
                            val client = clients.first { it.isNotEmpty() }.values.single()
                            val roomId =
                                client.api.room
                                    .createRoom(name = "Smoke Lobby")
                                    .getOrThrow()
                            val serverId = createHierarchy(client, roomId)
                            // There is no SDK MainViewModel in this fixture to start sync.
                            client.startSync()
                            val source = messenger.accountSources().first { it != null }!!
                            assertEquals("@alice:localhost", source.accountId)
                            assertHierarchy(source, serverId, roomId)
                            assertTimeline(client, source, roomId)
                            assertDirectMessages(client, source, serverId, roomId)
                            assertViewPersistence(client, storage)
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

    private suspend fun assertTimeline(
        client: MatrixClient,
        source: MatrixSource,
        roomId: RoomId,
    ) = coroutineScope {
        val bodies = (1..40).map { "Message $it" }
        for (body in bodies) {
            client.api.room
                .sendMessageEvent(roomId, RoomMessageEventContent.TextBased.Text(body))
                .getOrThrow()
        }
        val timeline = source.timeline(roomId.full)
        val observed = MutableStateFlow(TimelineSnapshot(isLoading = true))
        val collection = launch { timeline.snapshots.collect { observed.value = it } }
        try {
            val initial = observed.first { it.messages.lastOrNull()?.body == "Message 40" }
            assertTrue(initial.canLoadOlder)
            assertEquals("@alice:localhost", initial.messages.last().senderId)
            assertTrue(
                initial.messages
                    .last()
                    .senderName
                    .isNotBlank(),
            )
            assertTrue(initial.messages.last().timestamp > 0)
            while (observed.value.canLoadOlder) {
                val previous = observed.value
                timeline.loadOlder()
                observed.first { it != previous }
            }
            assertEquals(bodies, observed.value.messages.map { it.body })
            client.api.room
                .sendMessageEvent(roomId, RoomMessageEventContent.TextBased.Text("Incoming"))
                .getOrThrow()
            val incoming = observed.first { it.messages.lastOrNull()?.body == "Incoming" }
            assertEquals(bodies + "Incoming", incoming.messages.map { it.body })
        } finally {
            collection.cancelAndJoin()
        }
    }

    private suspend fun assertDirectMessages(
        client: MatrixClient,
        source: MatrixSource,
        serverId: RoomId,
        roomId: RoomId,
    ) {
        val otherPerson = UserId("@bob:localhost")
        val unknownRoom = RoomId("!unknown:localhost")
        client.api.user
            .setAccountData(
                DirectEventContent(mapOf(otherPerson to setOf(roomId, unknownRoom))),
                client.userId,
            ).getOrThrow()
        val direct = source.directory.first { it.directMessages.any { dm -> dm.id == roomId.full } }
        assertEquals("@bob:localhost", direct.directMessages.first { it.id == roomId.full }.name)
        assertTrue(direct.directMessages.any { it.id == unknownRoom.full && it.name == "@bob:localhost" })
        val projected = direct.projectDirectory()
        assertTrue(projected.channelsByServer.getValue(ALL_ROOMS_VIEW_ID).none { it.id == roomId.full })
        assertTrue(
            projected.categoriesByServer.getValue(serverId.full).flatMap { it.channels }.none {
                it.id ==
                    roomId.full
            },
        )
        client.api.user
            .setAccountData(DirectEventContent(emptyMap()), client.userId)
            .getOrThrow()
        val restored = source.directory.first { it.directMessages.isEmpty() }
        assertTrue(
            restored
                .projectDirectory()
                .channelsByServer
                .getValue(ALL_ROOMS_VIEW_ID)
                .any { it.id == roomId.full },
        )
    }

    private suspend fun assertViewPersistence(
        client: MatrixClient,
        storage: File,
    ) {
        val disk =
            FileSystemSettingsStorage(storage.resolve("view-settings.json").absolutePath.toPath(), FileSystem.SYSTEM)
        val settings = MatrixMessengerSettingsHolderImpl(disk)
        settings.init()
        settings.create(client.userId, MatrixMessengerAccountSettingsBase())
        val source = TrixnityMatrixSource(client, settings) { Result.success(Unit) }
        source.setViewHidden(DIRECT_MESSAGES_VIEW_ID, true)
        source.setViewHidden(ALL_ROOMS_VIEW_ID, true)
        val reloaded = MatrixMessengerSettingsHolderImpl(disk)
        reloaded.init()
        val restarted = TrixnityMatrixSource(client, reloaded) { Result.success(Unit) }
        assertEquals(setOf(DIRECT_MESSAGES_VIEW_ID, ALL_ROOMS_VIEW_ID), restarted.hiddenViewIds.first())
        restarted.setViewHidden(DIRECT_MESSAGES_VIEW_ID, false)
        assertEquals(setOf(ALL_ROOMS_VIEW_ID), restarted.hiddenViewIds.first())
    }

    private suspend fun createHierarchy(
        client: MatrixClient,
        roomId: RoomId,
    ): RoomId {
        val serverId =
            client.api.room
                .createRoom(
                    name = "Smoke Server",
                    creationContent = CreateEventContent(type = CreateEventContent.RoomType.Space),
                ).getOrThrow()
        val categoryId =
            client.api.room
                .createRoom(
                    name = "Smoke Category",
                    creationContent = CreateEventContent(type = CreateEventContent.RoomType.Space),
                ).getOrThrow()
        client.api.room
            .sendStateEvent(
                serverId,
                ChildEventContent(via = setOf("localhost")),
                categoryId.full,
            ).getOrThrow()
        client.api.room
            .sendStateEvent(
                categoryId,
                ChildEventContent(via = setOf("localhost")),
                roomId.full,
            ).getOrThrow()
        val parentOnly =
            client.api.room
                .createRoom(name = "Parent only")
                .getOrThrow()
        client.api.room
            .sendStateEvent(
                parentOnly,
                ParentEventContent(via = setOf("localhost")),
                serverId.full,
            ).getOrThrow()
        return serverId
    }

    private suspend fun assertHierarchy(
        source: MatrixSource,
        serverId: RoomId,
        roomId: RoomId,
    ) {
        val directory =
            source.directory
                .first { snapshot ->
                    val projected = snapshot.projectDirectory()
                    snapshot.channels.any { it.name == "Parent only" } &&
                        projected.categoriesByServer[serverId.full]?.singleOrNull()?.channels?.any {
                            it.id ==
                                roomId.full
                        } ==
                        true
                }.projectDirectory()
        assertEquals(listOf(serverId.full), directory.servers.map { it.id })
        assertEquals(emptyList(), directory.channelsByServer[serverId.full])
        assertEquals(
            "Smoke Lobby",
            directory.categoriesByServer
                .getValue(serverId.full)
                .single()
                .channels
                .single()
                .name,
        )
    }
}
