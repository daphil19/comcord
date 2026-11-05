package dev.phillipslabs.comcord

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.arkivanov.essenty.lifecycle.Lifecycle
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.clientserverapi.model.media.Media
import de.connect2x.trixnity.clientserverapi.model.user.ProfileField
import de.connect2x.trixnity.core.model.events.m.DirectEventContent
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.message.RoomMessageTimelineElementViewModel
import io.ktor.http.ContentType
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Image
import org.junit.Rule
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Exercises the production shell with a real Messenger Account and isolated homeserver. */
class ChannelTimelineSmokeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun selectedChannelRendersHistoryAndIncomingMessages() =
        runBlocking {
            MessengerTimelineGate.start().use { fixture ->
                val probe = TimelineProbe(fixture.messenger)
                compose.setContent { fixture.ProductionContent() }
                compose.waitUntil(30_000) {
                    compose.onAllNodes(hasText("Gate A message 39")).fetchSemanticsNodes().isNotEmpty()
                }
                compose.waitUntil(30_000) {
                    compose.onNodeWithTag("timeline").performScrollToIndex(0)
                    compose.onAllNodes(hasText("Gate A message 0")).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag("timeline").performScrollToNode(hasText("Gate A message 0"))
                compose.onNodeWithText("Gate A message 0").assertIsDisplayed()
                val session = probe.sessions.single()
                assertHistoryPresentation(session)
                screenshot("history")
                compose.onNodeWithText("#Gate B").performClick()
                compose.waitUntil(30_000) {
                    compose.onAllNodes(hasText("Gate B message")).fetchSemanticsNodes().isNotEmpty()
                }
                compose.waitUntil(10_000) { session.context.lifecycle.state == Lifecycle.State.DESTROYED }
                compose.waitUntil(10_000) {
                    session.context.coroutineScope.coroutineContext[Job]
                        ?.isActive == false
                }
                val frozen = session.vm.elements.value
                fixture.send(fixture.roomA, "Late A")
                fixture.send(fixture.roomB, "Production incoming")
                compose.waitUntil(30_000) {
                    compose.onAllNodes(hasText("Production incoming")).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag("timeline").performScrollToNode(hasText("Production incoming"))
                compose.onNodeWithText("Production incoming").assertIsDisplayed()
                assertEquals(frozen, session.vm.elements.value)
                compose.onNodeWithText("Late A").assertDoesNotExist()
                screenshot("incoming")
                compose.onNodeWithText("#Gate A").performClick()
                compose.waitUntil(30_000) {
                    compose.onAllNodes(hasText("Late A")).fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals(3, probe.sessions.size)
                fixture.client.api.room
                    .leaveRoom(fixture.roomA)
                    .getOrThrow()
                fixture.store.state.first { it.selectedRoomId == null }
                compose.waitUntil(10_000) {
                    probe.sessions.all { it.context.lifecycle.state == Lifecycle.State.DESTROYED }
                }
                compose.onNodeWithTag("timeline").assertDoesNotExist()
            }
        }

    @Test
    fun foregroundTimelineLifetimeDoesNotEraseAccountNavigation() =
        runBlocking {
            MessengerTimelineGate.start().use { fixture ->
                val probe = TimelineProbe(fixture.messenger)
                compose.setContent { fixture.ProductionContent() }
                compose.waitUntil(30_000) {
                    probe.sessions
                        .firstOrNull()
                        ?.vm
                        ?.elements
                        ?.value
                        ?.isNotEmpty() == true
                }
                compose.runOnIdle { fixture.productionForeground = false }
                compose.waitUntil(10_000) {
                    probe.sessions
                        .single()
                        .context.coroutineScope.coroutineContext[Job]
                        ?.isActive == false
                }
                assertEquals(fixture.roomA.full, fixture.store.state.value.selectedRoomId)
                compose.onNodeWithTag("timeline").assertDoesNotExist()
                fixture.send(fixture.roomA, "While hidden")
                compose.runOnIdle { fixture.productionForeground = true }
                compose.waitUntil(30_000) {
                    compose.onAllNodes(hasText("While hidden")).fetchSemanticsNodes().isNotEmpty()
                }
                compose.runOnIdle { fixture.visible = false }
                compose.waitUntil(10_000) {
                    probe.sessions.all {
                        it.context.coroutineScope.coroutineContext[Job]
                            ?.isActive == false
                    }
                }
            }
        }

    @Test
    fun narrowScreenCanSelectAChannel() =
        runBlocking {
            MessengerTimelineGate.start().use { fixture ->
                compose.setContent {
                    Box(Modifier.requiredSize(480.dp, 720.dp)) { fixture.ProductionContent() }
                }
                compose.onNodeWithText("Channels and DMs").performClick()
                compose.onNodeWithText("#Gate B").performClick()
                compose.waitUntil(30_000) {
                    compose.onAllNodes(hasText("Gate B message")).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText("Gate B message").assertIsDisplayed()
                screenshot("compact")
            }
        }

    @Test
    fun leavingAChannelCancelsPendingHistoryAndNeverShowsLateData() =
        runBlocking {
            MessengerTimelineGate.start(gatedSends = true).use { fixture ->
                val probe = TimelineProbe(fixture.messenger)
                val proxy = checkNotNull(fixture.sendGate)
                proxy.historyGate.hold()
                compose.setContent { fixture.ProductionContent() }
                compose.waitUntil(30_000) {
                    compose.onNodeWithTag("timeline").performScrollToIndex(0)
                    proxy.historyGate.arrived
                }
                val old = probe.sessions.single()
                compose.onNodeWithText("#Gate B").performClick()
                compose.waitUntil(10_000) {
                    old.context.coroutineScope.coroutineContext[Job]
                        ?.isActive == false
                }
                val frozen = old.vm.elements.value
                proxy.historyGate.release()
                fixture.send(fixture.roomB, "After canceled history")
                compose.waitUntil(30_000) {
                    compose.onAllNodes(hasText("After canceled history")).fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals(frozen, old.vm.elements.value)
                compose.onNodeWithText("Gate A message 0").assertDoesNotExist()
            }
        }

    @Test
    fun directMessageUsesTheSameTimelineAndUploadedAvatar() =
        runBlocking {
            MessengerTimelineGate.start().use { fixture ->
                val probe = TimelineProbe(fixture.messenger)
                val avatar = uploadAvatar(fixture.client)
                fixture.client.api.user
                    .setProfileField(
                        fixture.client.userId,
                        ProfileField.AvatarUrl(avatar),
                    ).getOrThrow()
                fixture.client.api.user
                    .setAccountData(
                        DirectEventContent(
                            mapOf(fixture.client.userId to setOf(fixture.roomB)),
                        ),
                        fixture.client.userId,
                    ).getOrThrow()
                fixture.store.state.first {
                    it.directory.channelsByServer[DIRECT_MESSAGES_VIEW_ID].orEmpty().any { room ->
                        room.id ==
                            fixture.roomB.full
                    }
                }
                fixture.store.selectServer(DIRECT_MESSAGES_VIEW_ID)
                fixture.store.selectRoom(fixture.roomB.full)
                fixture.send(fixture.roomB, "DM avatar message")
                compose.setContent { fixture.ProductionContent() }
                compose.waitUntil(30_000) {
                    probe.sessions.firstOrNull()?.vm?.elements?.value?.any {
                        it.sender.value
                            ?.image
                            ?.value != null
                    } == true
                }
                compose.onNodeWithTag("timeline").performScrollToNode(hasText("DM avatar message"))
                compose.onNodeWithText("DM avatar message").assertIsDisplayed()
                screenshot("dm-avatar")
                val old = probe.sessions.single()
                fixture.client.api.room
                    .leaveRoom(fixture.roomB)
                    .getOrThrow()
                compose.waitUntil(10_000) {
                    old.context.coroutineScope.coroutineContext[Job]
                        ?.isActive == false
                }
                assertNull(fixture.store.state.value.selectedRoomId)
                compose.onNodeWithTag("timeline").assertDoesNotExist()
            }
        }

    @Test
    fun emptyChannelCanRetryAndReceiveItsFirstMessage() =
        runBlocking {
            MessengerTimelineGate.start().use { fixture ->
                val room =
                    fixture.client.api.room
                        .createRoom(name = "Empty Channel")
                        .getOrThrow()
                fixture.store.state.first { it.rooms.any { channel -> channel.id == room.full } }
                fixture.store.selectRoom(room.full)
                val probe = TimelineProbe(fixture.messenger)
                compose.setContent { fixture.ProductionContent() }
                compose.waitUntil(30_000) {
                    compose.onAllNodes(hasText("No messages yet")).fetchSemanticsNodes().isNotEmpty()
                }
                val old = probe.sessions.single()
                compose.onNodeWithText("Retry messages").performClick()
                compose.waitUntil(10_000) {
                    probe.sessions.size == 2 && old.context.coroutineScope.coroutineContext[Job]
                        ?.isActive == false
                }
                fixture.send(room, "First message")
                compose.waitUntil(30_000) {
                    compose.onAllNodes(hasText("First message")).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText("First message").assertIsDisplayed()
                Unit
            }
        }

    private suspend fun uploadAvatar(client: MatrixClient): String {
        val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color.CYAN
        graphics.fillRect(0, 0, 32, 32)
        graphics.dispose()
        val bytes =
            ByteArrayOutputStream().use { output ->
                ImageIO.write(image, "png", output)
                output.toByteArray()
            }
        return client.api.media
            .upload(
                Media(
                    ByteReadChannel(bytes),
                    bytes.size.toLong(),
                    ContentType.Image.PNG,
                    null,
                ),
            ).getOrThrow()
            .contentUri
    }

    private fun assertHistoryPresentation(session: TimelineProbe.Session) {
        val bodies =
            session.vm.elements.value.mapNotNull {
                (it.element.value as? RoomMessageTimelineElementViewModel<*>)?.body
            }
        assertTrue(bodies.indexOf("Gate A message 0") < bodies.indexOf("Gate A message 39"))
        val holder =
            session.vm.elements.value
                .first { it.sender.value != null }
        val sender = checkNotNull(holder.sender.value)
        compose.onAllNodes(hasText("${sender.name} ${holder.formattedTime}")).fetchSemanticsNodes().also {
            assertTrue(it.isNotEmpty())
        }
    }

    private fun screenshot(name: String) {
        val directory = File("build/timeline-6-evidence").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asSkiaBitmap()
        File(directory, "$name.png").writeBytes(checkNotNull(Image.makeFromBitmap(bitmap).encodeToData()).bytes)
    }
}
