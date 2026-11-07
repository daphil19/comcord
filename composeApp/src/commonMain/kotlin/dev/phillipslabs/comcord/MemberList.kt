package dev.phillipslabs.comcord

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.flattenNotNull
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.room.getState
import de.connect2x.trixnity.client.store.membership
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.room.PowerLevelsEventContent
import de.connect2x.trixnity.messenger.MatrixClients
import de.connect2x.trixnity.messenger.MatrixMessenger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

private data class MemberListState(
    val groups: List<MemberRoleGroup> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

/** Only Comcord's Role grouping is owned here; members and power levels remain live SDK data. */
private fun observeMemberRoles(
    client: MatrixClient,
    roomId: RoomId,
): Flow<MemberListState> =
    flow {
        client.user.loadMembers(roomId)
        emitAll(
            combine(
                client.user.getAll(roomId).flattenNotNull(),
                client.room.getState<PowerLevelsEventContent>(roomId),
            ) { users, powerEvent ->
                val powers = powerEvent?.content
                val default = powers?.usersDefault ?: PowerLevelsEventContent.USERS_DEFAULT
                MemberListState(
                    groups =
                        groupMemberRoles(
                            users.values.filter { it.membership == Membership.JOIN }.map { user ->
                                ChannelMember(
                                    id = user.userId.full,
                                    name = user.name,
                                    powerLevel = powers?.users?.get(user.userId) ?: default,
                                )
                            },
                            defaultPowerLevel = default,
                        ),
                    loading = false,
                )
            },
        )
    }.catch { failure ->
        if (failure is CancellationException) throw failure
        emit(
            MemberListState(
                loading = false,
                error = "Could not load the Member List. Check your connection and retry.",
            ),
        )
    }

@Composable
internal fun MemberList(
    messenger: MatrixMessenger,
    accountId: String,
    roomId: String,
    modifier: Modifier = Modifier,
) {
    val client = messenger.di.get<MatrixClients>().value[UserId(accountId)] ?: return
    key(accountId, roomId) {
        var attempt by remember { mutableIntStateOf(0) }
        val members = remember(client, roomId, attempt) { observeMemberRoles(client, RoomId(roomId)) }
        val state by members.collectAsState(MemberListState())
        Column(
            modifier
                .fillMaxHeight()
                .width(240.dp)
                .padding(12.dp)
                .testTag("member-list"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Member List", style = MaterialTheme.typography.titleMedium)
            if (state.loading) Text("Loading members…")
            state.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error)
                Button(onClick = { attempt++ }) { Text("Retry members") }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.groups.forEach { group ->
                    item(key = "role:${group.name}") { Text(group.name, style = MaterialTheme.typography.titleSmall) }
                    items(group.members, key = { it.id }) { member ->
                        Column {
                            Text(member.name)
                            if (member.name != member.id) Text(member.id, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
