package dev.phillipslabs.comcord

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.message.EncryptedWaitTimelineElementViewModel
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Image
import org.junit.Rule
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessengerOutboxGateSmokeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun messageRemainsVisibleWhileSendingAndReconcilesOnceConfirmed() =
        runBlocking {
            MessengerTimelineGate.start(gatedSends = true).use { fixture ->
                compose.setContent { fixture.Content() }
                compose.waitUntil(30_000) { fixture.hasBody("Gate A message 39") }
                fixture.sendGate!!.holdNextSend()
                compose.onNodeWithTag("composer").performTextInput("Outbox gate message")
                compose.onNodeWithText("Send").performClick()
                compose.waitUntil(10_000) { fixture.hasBody("Outbox gate message") }
                compose.onNodeWithTag("timeline").performScrollToNode(hasText("Outbox gate message"))
                compose.onNodeWithText("Outbox gate message").assertIsDisplayed()
                compose.onNodeWithText("Sending").assertIsDisplayed()
                screenshot("sending")
                fixture.sendGate.releaseSend()
                compose.waitUntil(20_000) {
                    fixture.confirmedBodyCount("Outbox gate message") == 1 &&
                        fixture.bodyCount("Outbox gate message") == 1
                }
                compose.onNodeWithTag("timeline").performScrollToNode(hasText("Outbox gate message"))
                compose.onNodeWithText("Outbox gate message").assertIsDisplayed()
                assertTrue(
                    fixture.current!!
                        .vm.elements.value
                        .none { it.sendError.value != null },
                )
                screenshot("confirmed")
                Unit
            }
        }

    @Test
    fun failedMessageCanBeRetriedWithoutDuplicatingItsConfirmedEvent() =
        runBlocking {
            MessengerTimelineGate.start(gatedSends = true).use { fixture ->
                compose.setContent { fixture.Content() }
                compose.waitUntil(30_000) { fixture.hasBody("Gate A message 39") }
                val boundary = checkNotNull(fixture.sendGate)
                boundary.failSends(true)
                compose.onNodeWithTag("composer").performTextInput("Retry gate message")
                compose.onNodeWithText("Send").performClick()
                compose.waitUntil(20_000) {
                    fixture.current!!
                        .vm.elements.value
                        .any { it.sendError.value != null }
                }
                compose.onNodeWithTag("timeline").performScrollToNode(hasText("Retry gate message"))
                compose.onNodeWithText("Retry gate message").assertIsDisplayed()
                compose.onNodeWithText("Failed:", substring = true).assertIsDisplayed()
                screenshot("failed")
                boundary.failSends(false)
                compose.onNodeWithText("Retry").performClick()
                compose.waitUntil(20_000) {
                    fixture.confirmedBodyCount("Retry gate message") == 1 &&
                        fixture.bodyCount("Retry gate message") == 1
                }
                compose.onNodeWithText("Retry gate message").assertIsDisplayed()
                assertTrue(
                    fixture.current!!
                        .vm.elements.value
                        .none { it.sendError.value != null },
                )
                screenshot("retried")
                Unit
            }
        }

    @Test
    fun encryptedHistoryShowsContentWithKeysAndPlaceholderWithoutKeys() =
        runBlocking {
            MessengerTimelineGate.start().use { fixture ->
                fixture.prepareEncryptedChannel()
                compose.setContent { fixture.Content() }
                compose.onNodeWithText("Encrypted Channel").performClick()
                compose.waitUntil(30_000) { fixture.hasUndecryptable() }
                compose
                    .onNodeWithTag(
                        "timeline",
                    ).performScrollToNode(hasText("Unable to decrypt message", substring = true))
                compose.onNodeWithText("Unable to decrypt message", substring = true).assertIsDisplayed()
                screenshot("undecryptable")
                compose.onNodeWithTag("composer").performTextInput("Readable encrypted gate message")
                compose.onNodeWithText("Send").performClick()
                compose.waitUntil(30_000) { fixture.confirmedBodyCount("Readable encrypted gate message") == 1 }
                compose.onNodeWithTag("timeline").performScrollToNode(hasText("Readable encrypted gate message"))
                compose.onNodeWithText("Readable encrypted gate message").assertIsDisplayed()
                screenshot("encrypted-readable")
                fixture.assertEncryptedOnHomeserver("Readable encrypted gate message")
                // Bob's historical session still has no keys; its actual SDK decryption work is active.
                val decrypting = checkNotNull(fixture.current)
                assertTrue(
                    decrypting.vm.elements.value
                        .any { it.element.value is EncryptedWaitTimelineElementViewModel },
                )
                compose.onNodeWithText("Channel B").performClick()
                compose.waitUntil(10_000) { decrypting.pendingLoads.get() == 0 && decrypting.job.children.none() }
                val frozen = decrypting.vm.elements.value
                compose.waitUntil(10_000) { fixture.hasBody("Gate B message") }
                assertEquals(frozen, decrypting.vm.elements.value)
                compose.onNodeWithText("Encrypted Channel").performClick()
                compose.waitUntil(10_000) { fixture.hasUndecryptable() }
                val reopened = checkNotNull(fixture.current)
                compose.onNodeWithText("Destroy fixture").performClick()
                compose.waitUntil(10_000) {
                    fixture.current == null && reopened.job.children.none() &&
                        reopened.pendingLoads.get() == 0
                }
                Unit
            }
        }

    @Test
    fun switchingAndDestroyingDuringSendDisposeObsoleteRoomWork() =
        runBlocking {
            MessengerTimelineGate.start(gatedSends = true).use { fixture ->
                compose.setContent { fixture.Content() }
                compose.waitUntil(30_000) { fixture.hasBody("Gate A message 39") }
                val boundary = checkNotNull(fixture.sendGate)
                boundary.holdNextSend()
                compose.onNodeWithTag("composer").performTextInput("Send active at switch")
                compose.onNodeWithText("Send").performClick()
                compose.waitUntil(10_000) { boundary.sendArrived && fixture.hasBody("Send active at switch") }
                val first = checkNotNull(fixture.current)
                compose.onNodeWithText("Channel B").performClick()
                compose.waitUntil(10_000) { first.pendingLoads.get() == 0 && first.job.children.none() }
                compose.waitUntil(10_000) { fixture.hasBody("Gate B message") }
                val frozen = first.vm.elements.value
                boundary.releaseSend()
                fixture.awaitSyncedBody(fixture.roomA, "Send active at switch")
                assertEquals(fixture.roomB.full, fixture.store.state.value.selectedRoomId)
                assertEquals(frozen, first.vm.elements.value)
                boundary.holdNextSend()
                compose.onNodeWithTag("composer").performTextInput("Send active at destruction")
                compose.onNodeWithText("Send").performClick()
                compose.waitUntil(10_000) { boundary.sendArrived && fixture.hasBody("Send active at destruction") }
                val second = checkNotNull(fixture.current)
                compose.onNodeWithText("Destroy fixture").performClick()
                compose.waitUntil(10_000) {
                    fixture.current == null && second.pendingLoads.get() == 0 &&
                        second.job.children.none()
                }
                val frozenB = second.vm.elements.value
                boundary.releaseSend()
                fixture.awaitSyncedBody(fixture.roomB, "Send active at destruction")
                assertEquals(frozenB, second.vm.elements.value)
                assertTrue(fixture.sessions.all { it.job.children.none() })
                Unit
            }
        }

    private fun screenshot(name: String) {
        val directory = File("build/gate-evidence/22").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asSkiaBitmap()
        File(directory, "$name.png").writeBytes(checkNotNull(Image.makeFromBitmap(bitmap).encodeToData()).bytes)
    }
}
