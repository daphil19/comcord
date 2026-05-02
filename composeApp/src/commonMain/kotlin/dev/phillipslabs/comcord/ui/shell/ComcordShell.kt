package dev.phillipslabs.comcord.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.phillipslabs.comcord.ALL_ROOMS_SPACE_ID
import dev.phillipslabs.comcord.account.AccountSessionState
import dev.phillipslabs.comcord.account.collectAsStateCompat
import dev.phillipslabs.comcord.bootstrap.ComcordRootComponent
import dev.phillipslabs.comcord.design.ComcordColors
import dev.phillipslabs.comcord.design.WindowSizeClass
import dev.phillipslabs.comcord.design.rememberWindowSizeClass
import dev.phillipslabs.comcord.rooms.RoomListStore
import dev.phillipslabs.comcord.rooms.SpaceSummary
import dev.phillipslabs.comcord.spaces.SpaceDirectoryState
import dev.phillipslabs.comcord.spaces.SpaceDirectoryStore
import dev.phillipslabs.comcord.timeline.ComposerStore
import dev.phillipslabs.comcord.timeline.MatrixTimelineRepository
import dev.phillipslabs.comcord.timeline.TimelineLoadState
import dev.phillipslabs.comcord.timeline.TimelineState
import dev.phillipslabs.comcord.timeline.TimelineStore
import kotlinx.coroutines.launch

@Composable
fun ComcordShell(
    rootComponent: ComcordRootComponent,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val sessionState by rootComponent.sessionStore.state.collectAsStateCompat(
        AccountSessionState(emptyList(), null),
    )
    val activeSession = sessionState.activeSession

    if (activeSession == null) {
        Column(
            modifier = modifier.fillMaxSize().background(ComcordColors.AppBackground).padding(24.dp),
        ) {
            Text("No active account", color = ComcordColors.TextStrong)
        }
        return
    }

    val accountId = activeSession.userId.full
    val directoryStore = remember(activeSession.client) { SpaceDirectoryStore(activeSession.client) }
    val directoryFlow = remember(directoryStore) { directoryStore.observe() }
    val directoryState by directoryFlow.collectAsStateCompat(
        SpaceDirectoryState(
            spaces = listOf(SpaceSummary(ALL_ROOMS_SPACE_ID, "All Rooms")),
            roomsBySpace = mapOf(ALL_ROOMS_SPACE_ID to emptyList()),
            syncStateLabel = "Starting sync",
        ),
    )
    val navigationFlow =
        remember(rootComponent.appNavigator, accountId) {
            rootComponent.appNavigator.observe(accountId)
        }
    val navState by navigationFlow.collectAsStateCompat(
        rootComponent.appNavigator.currentState(accountId),
    )
    val roomListStore = remember { RoomListStore() }
    val roomListState = roomListStore.build(navState.selectedSpaceId, directoryState)
    val availableRoomIds =
        directoryState.roomsBySpace.values
            .flatten()
            .map { it.id }
            .toSet()
    val activeSpaceRoomIds = directoryState.roomsBySpace[navState.selectedSpaceId].orEmpty().map { it.id }

    LaunchedEffect(directoryState.spaces, availableRoomIds) {
        rootComponent.appNavigator.reconcile(
            accountId = accountId,
            availableSpaceIds = directoryState.spaces.map { it.id }.toSet(),
            availableRoomIds = availableRoomIds,
        )
    }
    LaunchedEffect(navState.selectedSpaceId, activeSpaceRoomIds) {
        rootComponent.appNavigator.ensureRoomSelected(accountId, activeSpaceRoomIds)
    }

    val selectedRoom =
        directoryState.roomsBySpace.values
            .flatten()
            .firstOrNull { it.id == navState.selectedRoomId }
    val timelineRepository = remember(activeSession.client) { MatrixTimelineRepository(activeSession.client) }
    val timelineStore = remember(timelineRepository) { TimelineStore(timelineRepository) }
    val composerStore = remember(timelineRepository) { ComposerStore(timelineRepository) }
    val timelineFlow =
        remember(timelineStore, selectedRoom, accountId) {
            timelineStore.observe(selectedRoom, accountId)
        }
    val timelineState by timelineFlow.collectAsStateCompat(TimelineState())
    val composerState by composerStore.state.collectAsStateCompat()

    LaunchedEffect(accountId, selectedRoom?.id) {
        composerStore.setContext(accountId, selectedRoom)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(ComcordColors.AppBackground)) {
        val windowSizeClass = rememberWindowSizeClass(maxWidth)
        Row(modifier = Modifier.fillMaxSize()) {
            LeftRail(
                compact = windowSizeClass == WindowSizeClass.Compact,
                spaces = directoryState.spaces,
                selectedSpaceId = navState.selectedSpaceId,
                onSelectSpace = { rootComponent.appNavigator.selectSpace(accountId, it) },
                onLogout = {
                    scope.launch {
                        rootComponent.sessionStore.logout(activeSession.userId)
                    }
                },
            )
            if (windowSizeClass != WindowSizeClass.Compact) {
                RoomSidebar(
                    syncStateLabel = directoryState.syncStateLabel,
                    roomListState = roomListState,
                    selectedRoomId = navState.selectedRoomId,
                    onSelectRoom = { rootComponent.appNavigator.selectRoom(accountId, it.id) },
                )
            }
            Column(modifier = Modifier.fillMaxSize().background(ComcordColors.SurfaceSecondary)) {
                RoomTopBar(
                    selectedRoom = selectedRoom,
                    subtitle =
                        when (timelineState.loadState) {
                            TimelineLoadState.Ready -> timelineState.roomSubtitle
                            TimelineLoadState.Loading -> "Loading conversation..."
                            TimelineLoadState.Error -> "Timeline unavailable"
                            TimelineLoadState.NoRoomSelected -> "Choose a conversation from the current space."
                        },
                )
                RoomTimelinePane(
                    compact = windowSizeClass == WindowSizeClass.Compact,
                    roomListState = roomListState,
                    selectedRoomId = navState.selectedRoomId,
                    timelineState = timelineState,
                    composerState = composerState,
                    syncStateLabel = directoryState.syncStateLabel,
                    onSelectRoom = { rootComponent.appNavigator.selectRoom(accountId, it) },
                    onDraftChange = composerStore::updateDraft,
                    onSendMessage = {
                        scope.launch {
                            composerStore.sendMessage(
                                senderId = accountId,
                                senderName = accountId,
                            )
                        }
                    },
                    onRetryMessage = { retryKey ->
                        selectedRoom?.let { room ->
                            scope.launch {
                                timelineStore.retryMessage(room, retryKey)
                            }
                        }
                    },
                )
            }
        }
    }
}
