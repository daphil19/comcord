package dev.phillipslabs.comcord

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create
import de.connect2x.trixnity.messenger.multi.singleModeMatrixMessenger
import dev.phillipslabs.comcord.bootstrap.createComcordAppBootstrap
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalComposeUiApi::class)
suspend fun main() {
    configureComcordLogging()
    val logger = comcordLogger("web.main")
    logger.info { "Starting Comcord web target" }

    val rootComponent =
        createComcordAppBootstrap {
            MatrixMultiMessenger
                .create { comcordMatrixConfig() }
                .singleModeMatrixMessenger()
                .first()
        }

    ComposeViewport {
        App(rootComponent)
    }
}
