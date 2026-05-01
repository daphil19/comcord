package dev.phillipslabs.comcord.navigation

import dev.phillipslabs.comcord.ALL_ROOMS_SPACE_ID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppNavigatorTest {
    private val alice = "@alice:example.com"
    private val bob = "@bob:example.com"

    @Test
    fun `select room keeps stable room id`() {
        val navigator = AppNavigator()

        navigator.selectSpace(alice, "space-a")
        navigator.selectRoom(alice, "!room:example.com")

        assertEquals("space-a", navigator.currentState(alice).selectedSpaceId)
        assertEquals("!room:example.com", navigator.currentState(alice).selectedRoomId)
    }

    @Test
    fun `reconcile resets missing selection`() {
        val navigator =
            AppNavigator(
                mapOf(
                    alice to
                        AppNavigationState(
                            selectedSpaceId = "missing-space",
                            selectedRoomId = "!missing:example.com",
                        ),
                ),
            )

        navigator.reconcile(
            accountId = alice,
            availableSpaceIds = setOf(ALL_ROOMS_SPACE_ID, "space-a"),
            availableRoomIds = setOf("!room:example.com"),
        )

        assertEquals(ALL_ROOMS_SPACE_ID, navigator.currentState(alice).selectedSpaceId)
        assertNull(navigator.currentState(alice).selectedRoomId)
    }

    @Test
    fun `select space clears selected room for next space`() {
        val navigator =
            AppNavigator(
                mapOf(
                    alice to
                        AppNavigationState(
                            selectedSpaceId = ALL_ROOMS_SPACE_ID,
                            selectedRoomId = "!room:example.com",
                        ),
                ),
            )

        navigator.selectSpace(alice, "space-a")

        assertEquals("space-a", navigator.currentState(alice).selectedSpaceId)
        assertNull(navigator.currentState(alice).selectedRoomId)
    }

    @Test
    fun `ensure room selected chooses first room when none selected`() {
        val navigator = AppNavigator()

        navigator.ensureRoomSelected(alice, listOf("!first:example.com", "!second:example.com"))

        assertEquals("!first:example.com", navigator.currentState(alice).selectedRoomId)
    }

    @Test
    fun `account selections stay isolated`() {
        val navigator = AppNavigator()

        navigator.selectSpace(alice, "space-a")
        navigator.selectRoom(alice, "!alpha:example.com")
        navigator.selectSpace(bob, "space-b")
        navigator.selectRoom(bob, "!beta:example.com")

        assertEquals("space-a", navigator.currentState(alice).selectedSpaceId)
        assertEquals("!alpha:example.com", navigator.currentState(alice).selectedRoomId)
        assertEquals("space-b", navigator.currentState(bob).selectedSpaceId)
        assertEquals("!beta:example.com", navigator.currentState(bob).selectedRoomId)
    }
}
