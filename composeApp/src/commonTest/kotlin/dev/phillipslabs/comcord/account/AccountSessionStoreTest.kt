package dev.phillipslabs.comcord.account

import de.connect2x.trixnity.core.model.UserId
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountSessionStoreTest {
    @Test
    fun `targeted logout keeps active account when logging out another session`() {
        val alice = UserId("@alice:example.com")
        val bob = UserId("@bob:example.com")

        val nextActive =
            selectActiveUserAfterLogout(
                currentUserIds = listOf(alice, bob),
                activeUserId = alice,
                loggedOutUserId = bob,
            )

        assertEquals(alice, nextActive)
    }

    @Test
    fun `targeted logout selects remaining account when active session logs out`() {
        val alice = UserId("@alice:example.com")
        val bob = UserId("@bob:example.com")

        val nextActive =
            selectActiveUserAfterLogout(
                currentUserIds = listOf(alice, bob),
                activeUserId = alice,
                loggedOutUserId = alice,
            )

        assertEquals(bob, nextActive)
    }
}
