package dev.phillipslabs.comcord.validation

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.clientserverapi.client.ClassicMatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.clientserverapi.model.room.CreateRoom
import de.connect2x.trixnity.clientserverapi.model.room.DirectoryVisibility
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.messenger.MatrixClients
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create
import de.connect2x.trixnity.messenger.multi.singleModeMatrixMessenger
import dev.phillipslabs.comcord.ComcordMatrixSettings
import dev.phillipslabs.comcord.applyComcordMatrixConfig
import dev.phillipslabs.comcord.bootstrap.ComcordRootComponent
import dev.phillipslabs.comcord.bootstrap.createComcordAppBootstrap
import dev.phillipslabs.comcord.createJvmComcordHttpEngine
import dev.phillipslabs.comcord.spaces.SpaceDirectoryStore
import dev.phillipslabs.comcord.timeline.MatrixTimelineRepository
import dev.phillipslabs.comcord.timeline.TimelineMessage
import dev.phillipslabs.comcord.timeline.TimelineStore
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class MatrixMockServerIntegrationTest {
    @Test
    fun `comcord bootstrap can log in and observe real mock timeline`() =
        runBlocking(Dispatchers.Swing) {
            val environment = withContext(Dispatchers.IO) { MatrixMockTestEnvironment.start() }
            var rootComponent: ComcordRootComponent? = null
            try {
                val fixtureClient =
                    MatrixMockFixtureClient(
                        baseUrl = environment.baseUrl,
                        username = environment.username,
                        password = environment.password,
                    )

                rootComponent =
                    createComcordAppBootstrap {
                        MatrixMultiMessenger
                            .create {
                                applyComcordMatrixConfig(
                                    ComcordMatrixSettings(
                                        appId = "dev.phillipslabs.comcord.validation.${UUID.randomUUID()}",
                                        httpClientEngine = createJvmComcordHttpEngine(),
                                    ),
                                )
                            }.singleModeMatrixMessenger()
                            .first()
                    }

                val createResult =
                    rootComponent.matrixMessenger.di
                        .get<MatrixClients>()
                        .create(fixtureClient.loginForAuthData())
                assertEquals(MatrixClients.CreateResult.Success, createResult)

                val activeSession =
                    rootComponent.sessionStore.state
                        .first { it.activeSession != null }
                        .activeSession
                assertNotNull(activeSession)
                assertEquals(environment.userId, activeSession.userId.full)

                activeSession.client.startSync()
                withTimeout(20.seconds) {
                    activeSession.client.syncState.first { it == SyncState.RUNNING }
                }
                val fixture = activeSession.client.createFixtureRoom()

                val directoryStore = SpaceDirectoryStore(activeSession.client)
                val directoryState =
                    withTimeout(45.seconds) {
                        directoryStore.observe().first { state ->
                            state.roomsBySpace.values
                                .flatten()
                                .any { it.id == fixture.roomId }
                        }
                    }
                val roomSummary =
                    directoryState.roomsBySpace.values
                        .flatten()
                        .firstOrNull { it.id == fixture.roomId }
                assertNotNull(roomSummary)

                val timelineStore = TimelineStore(MatrixTimelineRepository(activeSession.client))
                val timelineState =
                    withTimeout(45.seconds) {
                        timelineStore.observe(roomSummary, activeSession.userId.full).first { state ->
                            state.items.filterIsInstance<TimelineMessage>().any { it.body == fixture.messageBody }
                        }
                    }
                assertTrue(timelineState.isReady)
                assertTrue(
                    timelineState.items.filterIsInstance<TimelineMessage>().any { message ->
                        message.body == fixture.messageBody && message.isOwn
                    },
                )
            } finally {
                rootComponent?.let { component ->
                    withTimeout(10.seconds) {
                        component.matrixMessenger.closeSuspending()
                    }
                }
                withContext(Dispatchers.IO) {
                    environment.close()
                }
            }
        }
}

private data class MatrixFixture(
    val roomId: String,
    val messageBody: String,
)

private suspend fun MatrixClient.createFixtureRoom(): MatrixFixture {
    val roomName = "Comcord Validation ${UUID.randomUUID()}"
    val roomId =
        api.room
            .createRoom(
                visibility = DirectoryVisibility.PRIVATE,
                name = roomName,
                preset = CreateRoom.Request.Preset.PRIVATE,
            ).getOrThrow()
    val messageBody = "validation-${UUID.randomUUID()}"
    api.room
        .sendMessageEvent(
            roomId = roomId,
            eventContent = RoomMessageEventContent.TextBased.Text(messageBody),
        ).getOrThrow()
    return MatrixFixture(
        roomId = roomId.full,
        messageBody = messageBody,
    )
}

private class MatrixMockFixtureClient(
    private val baseUrl: String,
    private val username: String,
    private val password: String,
    private val httpClient: HttpClient =
        HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build(),
) {
    suspend fun loginForAuthData(): ClassicMatrixClientAuthProviderData =
        withContext(Dispatchers.IO) {
            ClassicMatrixClientAuthProviderData(
                baseUrl = Url(baseUrl),
                accessToken = login(),
                accessTokenExpiresInMs = null,
                refreshToken = null,
            )
        }

    private fun login(): String {
        val body =
            """
            {
              "type": "m.login.password",
              "identifier": {
                "type": "m.id.user",
                "user": "$username"
              },
              "password": "$password"
            }
            """.trimIndent()

        val response =
            executeJsonRequest(
                path = "/_matrix/client/v3/login",
                method = "POST",
                body = body,
            )
        return response.requireJsonString("access_token")
    }

    private fun executeJsonRequest(
        path: String,
        method: String,
        body: String? = null,
    ): String {
        val requestBuilder =
            HttpRequest
                .newBuilder()
                .uri(URI.create("$baseUrl$path"))
                .header("Content-Type", "application/json")

        when {
            body != null -> requestBuilder.method(method, HttpRequest.BodyPublishers.ofString(body))
            else -> requestBuilder.method(method, HttpRequest.BodyPublishers.noBody())
        }

        val response =
            httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() in 200..299) {
            "Matrix mock request failed: $method $path -> ${response.statusCode()} ${response.body()}"
        }
        return response.body()
    }
}

private class MatrixMockTestEnvironment private constructor(
    private val tempDir: Path,
    private val network: Network,
    private val synapse: GenericContainer<*>,
    val username: String,
    val password: String,
) : AutoCloseable {
    val baseUrl: String
        get() = "http://${synapse.host}:${synapse.getMappedPort(8008)}"
    val userId: String
        get() = "@$username:localhost"

    override fun close() {
        synapse.stop()
        network.close()
        tempDir.toFile().deleteRecursively()
    }

    companion object {
        private val synapseImage = DockerImageName.parse("ghcr.io/element-hq/synapse:v1.149.1")

        fun start(): MatrixMockTestEnvironment {
            val tempDir = Files.createTempDirectory("comcord-matrix-mock-")
            val username = "comcord${UUID.randomUUID().toString().replace("-", "")}"
            val password = "comcordpass"
            prepareFixtureDirectory(tempDir, username, password)

            val network = Network.newNetwork()
            val synapse =
                GenericContainer(synapseImage)
                    .withNetwork(network)
                    .withNetworkAliases("synapse")
                    .withExposedPorts(8008)
                    .withFileSystemBind(tempDir.resolve("data").toString(), "/data", BindMode.READ_WRITE)
                    .withFileSystemBind(tempDir.resolve("config").toString(), "/config", BindMode.READ_ONLY)
                    .waitingFor(
                        Wait
                            .forHttp("/_matrix/client/versions")
                            .forPort(8008)
                            .forStatusCode(200),
                    )

            synapse.start()
            seedUsers(tempDir, network)
            return MatrixMockTestEnvironment(tempDir, network, synapse, username, password)
        }

        private fun seedUsers(
            tempDir: Path,
            network: Network,
        ) {
            GenericContainer(synapseImage)
                .withNetwork(network)
                .withFileSystemBind(tempDir.resolve("data").toString(), "/data", BindMode.READ_WRITE)
                .withFileSystemBind(tempDir.resolve("seed").toString(), "/seed", BindMode.READ_ONLY)
                .withCreateContainerCmdModifier { command ->
                    command.withEntrypoint("/bin/sh", "/seed/seed-users.sh")
                }.withStartupCheckStrategy(OneShotStartupCheckStrategy())
                .waitingFor(Wait.forLogMessage(".*User seeding complete\\.\n", 1))
                .start()
        }

        private fun prepareFixtureDirectory(
            tempDir: Path,
            username: String,
            password: String,
        ) {
            Files.createDirectories(tempDir.resolve("data"))
            Files.createDirectories(tempDir.resolve("config"))
            Files.createDirectories(tempDir.resolve("seed"))
            copyFixtureFile("docker/matrix-mock/homeserver.yaml", tempDir.resolve("data/homeserver.yaml"))
            Files.writeString(
                tempDir.resolve("data/homeserver.yaml"),
                """

                rc_message:
                  per_second: 1000.0
                  burst_count: 1000.0
                rc_registration:
                  per_second: 1000.0
                  burst_count: 1000.0
                rc_login:
                  address:
                    per_second: 1000.0
                    burst_count: 1000.0
                  account:
                    per_second: 1000.0
                    burst_count: 1000.0
                  failed_attempts:
                    per_second: 1000.0
                    burst_count: 1000.0
                """.trimIndent(),
                StandardOpenOption.APPEND,
            )
            copyFixtureFile("docker/matrix-mock/log.config", tempDir.resolve("config/log.config"))
            copyFixtureFile("docker/matrix-mock/seed-users.sh", tempDir.resolve("seed/seed-users.sh"))
            Files.writeString(tempDir.resolve("seed/users.csv"), "$username:$password:admin\n")
        }

        private fun copyFixtureFile(
            sourceRelativePath: String,
            destination: Path,
        ) {
            Files.copy(
                repoRoot().resolve(sourceRelativePath),
                destination,
            )
        }

        private fun repoRoot(): Path =
            generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { current ->
                current.parent
            }.firstOrNull { candidate ->
                Files.exists(candidate.resolve("docker/matrix-mock/homeserver.yaml"))
            } ?: error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }
}

private fun String.requireJsonString(field: String): String =
    Regex(""""$field"\s*:\s*"([^"]+)"""")
        .find(this)
        ?.groupValues
        ?.get(1)
        ?: error("Missing JSON string field '$field' in: $this")
