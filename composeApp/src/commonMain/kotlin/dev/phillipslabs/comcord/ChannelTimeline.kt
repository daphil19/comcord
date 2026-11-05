package dev.phillipslabs.comcord

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.messenger.MatrixMessenger
import de.connect2x.trixnity.messenger.viewmodel.MatrixClientViewModelContextImpl
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.TimelineViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.TimelineViewModelFactory
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.BaseTimelineElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.TimelineElementViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.message.EncryptedErrorTimelineElementViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.message.EncryptedWaitTimelineElementViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.message.RoomMessageTimelineElementViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Selection and foreground composition bound the SDK room lifetime; no timeline state lives in AccountStore. */
@Composable
internal fun ChannelTimeline(
    messenger: MatrixMessenger,
    accountId: String,
    roomId: String,
    modifier: Modifier = Modifier,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    var attempt by remember { mutableIntStateOf(0) }
    key(attempt) {
        val scope = rememberCoroutineScope()
        val lifecycle = remember { LifecycleRegistry() }
        val vm =
            remember {
                (messenger.di.getOrNull<TimelineViewModelFactory>() ?: TimelineViewModelFactory).create(
                    MatrixClientViewModelContextImpl(
                        messenger.di,
                        DefaultComponentContext(lifecycle),
                        UserId(accountId),
                        dispatcher + checkNotNull(scope.coroutineContext[Job]),
                        "timeline:$accountId:$roomId",
                    ),
                    RoomId(roomId),
                    onBack = {},
                    onOpenRoomSettings = {},
                    onOpenUserProfile = {},
                    onOpenMention = { _, _ -> },
                    onOpenMetadata = {},
                )
            }
        DisposableEffect(vm) {
            lifecycle.resume()
            onDispose { lifecycle.destroy() }
        }
        MessageTimeline(vm, onRetry = { attempt++ }, modifier = modifier)
    }
}

/** Manual viewport contract, as validated by #21. Paging jobs belong to this composition. */
@Composable
private fun MessageTimeline(
    vm: TimelineViewModel,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val elements by vm.elements.collectAsState()
    // The reading surface displays messages; membership and room configuration stay in the directory UI.
    val displayElements =
        elements.filter {
            val element by it.element.collectAsState()
            element == null || element is TimelineElementViewModel.Message<*>
        }
    val canLoadBefore by vm.canLoadBefore.collectAsState()
    val sdkError by vm.error.collectAsState()
    val scrollTo by vm.scrollTo.collectAsState()
    val list = rememberLazyListState()
    var loadError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(vm, list) {
        observeTimelinePaging(
            vm,
            snapshotFlow {
                (
                    list.layoutInfo.visibleItemsInfo
                        .firstOrNull()
                        ?.index ?: Int.MAX_VALUE
                ) <= 1
            },
            onError = { loadError = it },
        )
    }
    LaunchedEffect(scrollTo, displayElements) {
        val target = scrollTo ?: return@LaunchedEffect
        val index = displayElements.indexOfFirst { it.key == target }
        if (index >= 0) {
            list.scrollToItem(index + 1)
            vm.finishedScrollTo(target)
        } else if (elements.any { it.key == target }) {
            list.scrollToItem(displayElements.size)
            vm.finishedScrollTo(target)
        }
    }
    Column(modifier) {
        Button(onClick = vm::jumpToEndOfTimeline) { Text("Latest messages") }
        val error = loadError ?: sdkError
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetry) { Text("Retry messages") }
        }
        if (displayElements.isEmpty()) {
            Text(if (canLoadBefore == false) "No messages yet" else "Loading messages…")
            Button(onClick = onRetry) { Text("Retry messages") }
        }
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().testTag("timeline"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "history-boundary") {
                Text(if (canLoadBefore == false) "Start of conversation" else "Scroll up for older messages")
            }
            items(displayElements, key = { it.key }) { holder -> TimelineMessage(holder) }
        }
    }
}

/** UI-owned viewport triggers; all timeline data and paging operations remain in Messenger. */
@Suppress("TooGenericExceptionCaught")
private suspend fun observeTimelinePaging(
    vm: TimelineViewModel,
    atStart: Flow<Boolean>,
    onError: (String) -> Unit,
) = coroutineScope {
    launch {
        try {
            while (isActive) {
                vm.loadAfter()
                // A completed load need not have made progress (offline or a transient gap).
                delay(PAGING_RETRY_DELAY_MS)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            onError("Could not load messages. Check your connection and retry.")
        }
    }
    combine(atStart, vm.canLoadBefore, vm.elements) { atTop, canLoad, elements ->
        if (atTop && canLoad == true) elements.firstOrNull()?.key else null
    }.distinctUntilChanged().collect { first ->
        if (first != null) {
            try {
                vm.loadBefore()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                onError("Could not load older messages. Check your connection and retry.")
            }
        }
    }
}

@Composable
private fun TimelineMessage(holder: BaseTimelineElementHolderViewModel) {
    val element by holder.element.collectAsState()
    if (element == TimelineElementViewModel.Empty) return
    val sender by holder.sender.collectAsState()
    val noImage = remember { MutableStateFlow<ByteArray?>(null) }
    val bytes by (sender?.image ?: noImage).collectAsState()
    val avatar = remember(bytes) { bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (avatar == null) {
                Text(sender?.initials ?: "?")
            } else {
                Image(
                    avatar,
                    "${sender?.name.orEmpty()} avatar",
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Column(Modifier.padding(bottom = 4.dp)) {
            Text("${sender?.name.orEmpty()} ${holder.formattedTime}", style = MaterialTheme.typography.labelMedium)
            Text(
                when (val value = element) {
                    is RoomMessageTimelineElementViewModel<*> -> value.body
                    is EncryptedWaitTimelineElementViewModel -> "Unable to decrypt message: waiting for keys"
                    is EncryptedErrorTimelineElementViewModel -> "Unable to decrypt message: ${value.error}"
                    null -> "Loading message…"
                    else -> "Unsupported message"
                },
            )
        }
    }
}

private const val PAGING_RETRY_DELAY_MS = 250L
