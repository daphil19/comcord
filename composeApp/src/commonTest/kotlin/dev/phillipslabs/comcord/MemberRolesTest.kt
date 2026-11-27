package dev.phillipslabs.comcord

import kotlin.test.Test
import kotlin.test.assertEquals

class MemberRolesTest {
    @Test
    fun channelDefaultAndCustomRolesEachHaveOneGroup() {
        val groups =
            groupMemberRoles(
                listOf(
                    ChannelMember("@member", "Member", 10),
                    ChannelMember("@custom", "Custom", 75),
                    ChannelMember("@admin", "Admin", 100),
                    ChannelMember("@moderator", "Moderator", 50),
                ),
                defaultPowerLevel = 10,
            )

        assertEquals(listOf("Admin", "Role 75", "Moderator", "Member"), groups.map { it.name })
        assertEquals(listOf("@admin", "@custom", "@moderator", "@member"), groups.flatMap { it.members }.map { it.id })
    }
}
