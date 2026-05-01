@file:Suppress("ktlint:standard:filename")

package dev.phillipslabs.comcord

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create
import de.connect2x.trixnity.messenger.multi.singleModeMatrixMessenger
import dev.phillipslabs.comcord.bootstrap.createComcordAppBootstrap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

fun main() {
    configureComcordLogging()
    val logger = comcordLogger("jvm.main")
    logger.info { "Starting Comcord JVM target" }

    val rootComponent =
        runBlocking(Dispatchers.Main) {
            createComcordAppBootstrap {
                MatrixMultiMessenger
                    .create {
                        applyComcordMatrixConfig(
                            ComcordMatrixSettings(
                                httpClientEngine = createJvmComcordHttpEngine(),
                            ),
                        )
                    }
                    .singleModeMatrixMessenger()
                    .first()
            }
        }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Comcord",
        ) {
            App(rootComponent)
        }
    }
}
