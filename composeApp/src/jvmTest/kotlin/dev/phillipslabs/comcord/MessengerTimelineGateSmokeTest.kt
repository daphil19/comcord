package dev.phillipslabs.comcord

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.TimelineElementHolderViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Image
import org.junit.Rule
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Experimental #21 fixture: no production timeline is replaced. */
class MessengerTimelineGateSmokeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun messengerOwnsRenderingPagingAndRoomLifetime() =
        runBlocking {
            MessengerTimelineGate.start().use { fixture ->
                compose.setContent { fixture.Content() }
                compose.waitUntil(30_000) { fixture.hasBody("Gate A message 39") }
                compose.onNodeWithText("Gate A message 39").assertIsDisplayed()
                val first = checkNotNull(fixture.current)
                val displayed =
                    first.vm.elements.value
                        .last()
                assertTrue(displayed.formattedTime.isNotBlank())
                compose.waitUntil(10_000) { displayed.sender.value != null }
                val sender = checkNotNull(displayed.sender.value)
                compose.onAllNodesWithText("${sender.name} ${displayed.formattedTime}").onFirst().assertIsDisplayed()
                compose.onAllNodesWithText(sender.initials).onFirst().assertIsDisplayed()
                screenshot("initial")
                assertNull(first.vm.error.value)

                assertPagedAndIncoming(fixture, first)

                // Manual loadAfter is suspended waiting for future events when A is left.
                compose.waitUntil(10_000) { first.pendingLoads.get() == 1 }
                compose.onNodeWithText("Channel B").performClick()
                compose.waitUntil(30_000) { fixture.hasBody("Gate B message") }
                compose.onNodeWithText("Gate B message").assertIsDisplayed()
                compose.waitUntil(10_000) { first.pendingLoads.get() == 0 && first.job.children.none() }
                assertEquals(fixture.roomB.full, fixture.store.state.value.selectedRoomId)
                val frozen = first.vm.elements.value
                fixture.send(fixture.roomA, "Late A must never replace B")
                val incomingB = fixture.send(fixture.roomB, "Gate B incoming")
                compose.waitUntil(10_000) {
                    fixture.current?.vm?.elements?.value?.any {
                        it is TimelineElementHolderViewModel &&
                            it.eventId == incomingB
                    } ==
                        true
                }
                compose.onNodeWithTag("timeline").performScrollToNode(hasText("Gate B incoming"))
                compose.onNodeWithText("Gate B incoming").assertIsDisplayed()
                assertEquals(frozen, first.vm.elements.value)
                assertTrue(!fixture.hasBody("Late A must never replace B"))
                screenshot("switched")

                val second = checkNotNull(fixture.current)
                compose.waitUntil(10_000) { second.pendingLoads.get() == 1 }
                compose.onNodeWithText("Destroy fixture").performClick()
                compose.waitUntil(10_000) {
                    fixture.current == null &&
                        fixture.sessions.all { it.pendingLoads.get() == 0 && it.job.children.none() }
                }
                val frozenB = second.vm.elements.value
                val afterDestroy = fixture.send(fixture.roomB, "After destruction")
                // Await real sync independently before checking that disposed VMs stay frozen.
                kotlinx.coroutines.withTimeout(10_000) {
                    fixture.client.room
                        .getById(fixture.roomB)
                        .first { it?.lastEventId == afterDestroy }
                }
                assertEquals(frozenB, second.vm.elements.value)
                Unit
            }
        }

    private suspend fun assertPagedAndIncoming(
        fixture: MessengerTimelineGate,
        first: MessengerTimelineGate.Session,
    ) {
        // This event was outside initial sync history and must be paged from Synapse.
        assertTrue(!fixture.hasBody("Gate A message 0"))
        repeat(8) {
            val count = first.vm.elements.value.size
            compose.onNodeWithText("Older messages").performClick()
            compose.waitUntil(10_000) {
                first.vm.elements.value.size > count ||
                    first.vm.canLoadBefore.value == false
            }
        }
        compose.onNodeWithTag("timeline").performScrollToNode(hasText("Gate A message 0"))
        compose.onNodeWithText("Gate A message 0").assertIsDisplayed()
        screenshot("paged")

        val incomingA = fixture.send(fixture.roomA, "Gate A incoming")
        compose.waitUntil(10_000) {
            first.vm.elements.value.any {
                it is TimelineElementHolderViewModel &&
                    it.eventId == incomingA
            }
        }
        compose.onNodeWithTag("timeline").performScrollToNode(hasText("Gate A incoming"))
        compose.onNodeWithText("Gate A incoming").assertIsDisplayed()
        screenshot("incoming")
    }

    private fun screenshot(name: String) {
        val directory = File("build/gate-evidence/21").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asSkiaBitmap()
        File(directory, "$name.png").writeBytes(checkNotNull(Image.makeFromBitmap(bitmap).encodeToData()).bytes)
    }
}
