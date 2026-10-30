@file:Suppress("ktlint:standard:filename")

package dev.phillipslabs.comcord

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create
import kotlinx.coroutines.launch

fun main() =
    application {
        val host = remember { ComcordApplicationHost() }
        val scope = rememberCoroutineScope()
        val windowState = rememberWindowState()
        LaunchedEffect(windowState.isMinimized) {
            if (windowState.isMinimized) host.stop() else host.resume()
        }
        Window(
            onCloseRequest = {
                scope.launch {
                    host.closeSuspending()
                    exitApplication()
                }
            },
            state = windowState,
            title = "Comcord",
        ) {
            host.Content(factory = { MatrixMultiMessenger.create { comcordMatrixConfig(host) } })
        }
    }
