package dev.phillipslabs.comcord

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import de.connect2x.trixnity.core.model.events.m.room.PowerLevelsEventContent
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Image
import org.junit.Rule
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MemberListSmokeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun selectedChannelRolesAndToggleKeepTheLiveTimeline() =
        runBlocking {
            MessengerTimelineGate.start().use { fixture ->
                fixture.client.api.room
                    .sendStateEvent(
                        fixture.roomB,
                        PowerLevelsEventContent(users = mapOf(fixture.client.userId to 75), usersDefault = 10),
                    ).getOrThrow()
                val timelines = TimelineProbe(fixture.messenger)
                compose.setContent { fixture.ProductionContent() }
                compose.waitUntil(30_000) {
                    compose.onAllNodes(hasText("Gate A message 39")).fetchSemanticsNodes().isNotEmpty()
                }
                val original = timelines.sessions.single().vm
                val fullWidth =
                    compose
                        .onNodeWithTag("timeline")
                        .fetchSemanticsNode()
                        .boundsInRoot.width
                compose.onNodeWithText("Show Member List").performClick()
                compose.waitUntil(30_000) {
                    compose.onAllNodes(hasText("Admin")).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText("@alice:localhost").assertIsDisplayed()
                assertTrue(
                    compose
                        .onNodeWithTag("timeline")
                        .fetchSemanticsNode()
                        .boundsInRoot.width < fullWidth,
                )
                screenshot()
                compose.onNodeWithText("Hide Member List").performClick()
                compose.onNodeWithText("Admin").assertDoesNotExist()
                assertEquals(
                    fullWidth,
                    compose
                        .onNodeWithTag("timeline")
                        .fetchSemanticsNode()
                        .boundsInRoot.width,
                )
                assertSame(original, timelines.sessions.single().vm)
                compose.onNodeWithText("Show Member List").performClick()
                assertSame(original, timelines.sessions.single().vm)
                compose.onNodeWithText("#Gate B").performClick()
                compose.waitUntil(30_000) {
                    compose.onAllNodes(hasText("Role 75")).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText("Admin").assertDoesNotExist()
                compose.onNodeWithText("@alice:localhost").assertIsDisplayed()
                assertEquals(2, timelines.sessions.size)
            }
        }

    private fun screenshot() {
        val destination = File("build/feature-evidence/10/member-list.png")
        destination.parentFile.mkdirs()
        val bitmap = compose.onRoot().captureToImage().asSkiaBitmap()
        destination.writeBytes(checkNotNull(Image.makeFromBitmap(bitmap).encodeToData()).bytes)
    }
}
