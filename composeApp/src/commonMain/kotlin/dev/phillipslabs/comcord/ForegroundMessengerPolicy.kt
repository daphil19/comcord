package dev.phillipslabs.comcord

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnStart
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessengerConfiguration
import de.connect2x.trixnity.messenger.viewmodel.MainViewModel
import de.connect2x.trixnity.messenger.viewmodel.MainViewModelFactory
import de.connect2x.trixnity.messenger.viewmodel.RootViewModel
import de.connect2x.trixnity.messenger.viewmodel.RootViewModelFactory
import de.connect2x.trixnity.messenger.viewmodel.RootViewModelImpl
import de.connect2x.trixnity.messenger.viewmodel.ViewModelContext
import de.connect2x.trixnity.messenger.viewmodel.initialsync.RunInitialSync
import de.connect2x.trixnity.messenger.viewmodel.initialsync.RunInitialSyncImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.dsl.module

/** Comcord permits foreground sync; Messenger's default initial sync also permits background execution. */
fun MatrixMultiMessengerConfiguration.comcordMatrixConfig(host: ComcordApplicationHost) {
    comcordMatrixConfig()
    messengerConfiguration {
        modulesFactories += {
            module {
                single<RunInitialSync> {
                    object : RunInitialSync {
                        override suspend fun invoke(matrixClient: MatrixClient): Boolean =
                            coroutineScope {
                                val result = CompletableDeferred<Boolean>()
                                val invocation =
                                    launch {
                                        host.foreground.collectLatest { active ->
                                            if (active) {
                                                val success = RunInitialSyncImpl(matrixClient)
                                                currentCoroutineContext().ensureActive()
                                                if (host.foreground.value) result.complete(success)
                                            }
                                        }
                                    }
                                try {
                                    result.await()
                                } finally {
                                    invocation.cancelAndJoin()
                                }
                            }
                    }
                }
                single<MainViewModelFactory> { ForegroundMainViewModelFactory }
                single<RootViewModelFactory> { MainThreadRootViewModelFactory }
            }
        }
    }
}

private object ForegroundMainViewModelFactory : MainViewModelFactory {
    override fun create(
        viewModelContext: ViewModelContext,
        onCreateNewAccount: () -> Unit,
        onRemoveAccount: (UserId) -> Unit,
    ): MainViewModel {
        val delegate = MainViewModelFactory.create(viewModelContext, onCreateNewAccount, onRemoveAccount)
        return object : MainViewModel by delegate {
            override fun start() {
                viewModelContext.lifecycle.doOnStart(isOneTime = true) { delegate.start() }
            }
        }
    }
}

/** VM routing and host events share Main; application clients and SDK workers keep their default dispatchers. */
private object MainThreadRootViewModelFactory : RootViewModelFactory {
    override fun create(
        componentContext: ComponentContext,
        di: Koin,
    ): RootViewModel = RootViewModelImpl(componentContext, di, Dispatchers.Main.immediate)
}
