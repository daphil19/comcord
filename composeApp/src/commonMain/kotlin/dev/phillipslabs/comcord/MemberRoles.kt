package dev.phillipslabs.comcord

/** Comcord's one-Role-per-member projection, independent of Server power levels. */
data class ChannelMember(
    val id: String,
    val name: String,
    val powerLevel: Long,
)

data class MemberRoleGroup(
    val name: String,
    val members: List<ChannelMember>,
)

internal fun groupMemberRoles(
    members: List<ChannelMember>,
    defaultPowerLevel: Long,
): List<MemberRoleGroup> =
    members
        .distinctBy { it.id }
        .groupBy { it.powerLevel }
        .entries
        .sortedByDescending { it.key }
        .map { (level, people) ->
            MemberRoleGroup(
                name =
                    when (level) {
                        100L -> "Admin"
                        50L -> "Moderator"
                        defaultPowerLevel -> "Member"
                        else -> "Role $level"
                    },
                members = people.sortedWith(compareBy<ChannelMember> { it.name }.thenBy { it.id }),
            )
        }
