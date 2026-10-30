package dev.phillipslabs.comcord

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import de.connect2x.trixnity.messenger.MatrixMessenger
import de.connect2x.trixnity.messenger.createRoot
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.singleModeMatrixMessenger
import de.connect2x.trixnity.messenger.viewmodel.RootViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

sealed interface ComcordApplicationState {
    data object Loading : ComcordApplicationState

    data class Ready(
        val messenger: MatrixMessenger,
        val rootViewModel: RootViewModel,
    ) : ComcordApplicationState

    data class Failure(
        val message: String,
    ) : ComcordApplicationState

    data object Closed : ComcordApplicationState
}

private const val INITIALIZATION_TIMEOUT_MS = 60_000L

/** Application clients outlive the shell composition; the native host owns this lifetime. */
class ComcordApplicationHost(
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    private val mutableState = MutableStateFlow<ComcordApplicationState>(ComcordApplicationState.Loading)
    val state: StateFlow<ComcordApplicationState> = mutableState.asStateFlow()
    private val mutex = Mutex()
    private val cleanupScope = CoroutineScope(mainDispatcher + SupervisorJob())
    private var initializer: Job? = null
    private var multi: MatrixMultiMessenger? = null
    private var lifecycle: LifecycleRegistry? = null
    private val mutableForeground = MutableStateFlow(false)
    val foreground: StateFlow<Boolean> = mutableForeground.asStateFlow()
    private var destroyed = false

    // SDK/platform bootstrap can fail through storage, crypto or platform exceptions.
    // This boundary turns them into a retryable screen while preserving cancellation.
    @Suppress("TooGenericExceptionCaught")
    suspend fun initialize(factory: suspend () -> MatrixMultiMessenger) =
        withContext(mainDispatcher) {
            mutex.withLock {
                if (destroyed) return@withLock
                mutableState.value = ComcordApplicationState.Loading
                initializer = currentCoroutineContext()[Job]
                var created: MatrixMultiMessenger? = null
                try {
                    release()
                    val messenger =
                        withTimeout(INITIALIZATION_TIMEOUT_MS) {
                            created = factory()
                            checkNotNull(created).singleModeMatrixMessenger().first()
                        }
                    // Storage may restore while hidden; creating the SDK root starts workflows.
                    // Waiting for the host is not a bootstrap timeout and is canceled on close.
                    foreground.first { it }
                    run {
                        val rootLifecycle = LifecycleRegistry()
                        lifecycle = rootLifecycle
                        multi = created
                        val root = messenger.createRoot(DefaultComponentContext(rootLifecycle))
                        rootLifecycle.resume()
                        mutableState.value = ComcordApplicationState.Ready(messenger, root)
                    }
                } catch (failure: Exception) {
                    // Retain the original failure if cleanup itself fails as well.
                    try {
                        if (multi == null) multi = created
                        release()
                    } catch (cleanupFailure: Exception) {
                        failure.addSuppressed(cleanupFailure)
                    }
                    if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
                    if (!destroyed) {
                        mutableState.value =
                            ComcordApplicationState.Failure(
                                "Could not open local Account storage. " +
                                    "Retry, or close other Comcord windows and try again.",
                            )
                    }
                } finally {
                    initializer = null
                }
            }
        }

    fun resume() {
        if (destroyed) return
        mutableForeground.value = true
        lifecycle?.resume()
    }

    fun stop() {
        if (destroyed) return
        mutableForeground.value = false
        lifecycle?.stop()
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        mutableState.value = ComcordApplicationState.Closed
        initializer?.cancel()
        cleanupScope.launch { closeSuspending() }
    }

    suspend fun closeSuspending() =
        withContext(NonCancellable + mainDispatcher) {
            destroyed = true
            mutableState.value = ComcordApplicationState.Closed
            initializer?.cancel()
            try {
                mutex.withLock { release() }
            } finally {
                cleanupScope.cancel()
            }
        }

    private suspend fun release() =
        withContext(NonCancellable) {
            val closingLifecycle = lifecycle
            val closing = multi
            lifecycle = null
            multi = null
            try {
                closingLifecycle?.destroy()
            } finally {
                closing?.closeSuspending()
            }
        }

    @Composable
    fun Content(
        factory: suspend () -> MatrixMultiMessenger,
        modifier: Modifier = Modifier,
    ) {
        val application by state.collectAsState()
        val scope = rememberCoroutineScope()
        val currentFactory by rememberUpdatedState(factory)
        LaunchedEffect(this) { if (state.value == ComcordApplicationState.Loading) initialize(currentFactory) }
        when (val current = application) {
            is ComcordApplicationState.Ready -> {
                App(
                    current.rootViewModel,
                    current.messenger,
                    modifier = modifier,
                    onRetryInitialization = { scope.launch { initialize(currentFactory) } },
                )
            }

            else -> {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Surface(
                        modifier = modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                when (current) {
                                    is ComcordApplicationState.Failure -> current.message
                                    ComcordApplicationState.Closed -> "Comcord closed"
                                    else -> "Opening Comcord…"
                                },
                            )
                            if (current is ComcordApplicationState.Failure) {
                                Button(onClick = {
                                    scope.launch { initialize(currentFactory) }
                                }) { Text("Retry") }
                            }
                        }
                    }
                }
            }
        }
    }
}
