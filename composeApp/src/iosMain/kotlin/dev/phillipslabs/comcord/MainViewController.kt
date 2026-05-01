package dev.phillipslabs.comcord

import androidx.compose.ui.window.ComposeUIViewController
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create
import de.connect2x.trixnity.messenger.multi.singleModeMatrixMessenger
import dev.phillipslabs.comcord.bootstrap.createComcordAppBootstrap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import platform.UIKit.UIViewController

@Suppress("ktlint:standard:function-naming")
fun MainViewController(): UIViewController {
    configureComcordLogging()
    val logger = comcordLogger("ios.main")
    logger.info { "Starting Comcord iOS target" }

    val rootComponent =
        runBlocking {
            createComcordAppBootstrap {
                MatrixMultiMessenger
                    .create {
                        comcordMatrixConfig()
                    }.singleModeMatrixMessenger()
                    .first()
            }
        }

    return ComposeUIViewController {
        App(rootComponent)
    }
}
