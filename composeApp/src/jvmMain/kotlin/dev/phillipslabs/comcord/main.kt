@file:Suppress("ktlint:standard:filename")

package dev.phillipslabs.comcord

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import de.connect2x.trixnity.messenger.create
import de.connect2x.trixnity.messenger.createRoot
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create
import de.connect2x.trixnity.messenger.multi.singleModeMatrixMessenger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

fun main() {
    val matrixMessenger =
        runBlocking(Dispatchers.Main) {
            MatrixMultiMessenger
                .create { comcordMatrixConfig() }
                .singleModeMatrixMessenger()
                .first()
        }
    val rootViewModel = matrixMessenger.createRoot()

    // TODO we need to fix getting this on the main thread eventually! (all in runBlocking?)
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Comcord",
        ) {
            //            CoroutineScope(Dispatchers.Default).launch {

            //                withContext(Dispatchers.Main) {
            App(rootViewModel, matrixMessenger)
            //                }
            //            }
        }
    }
}
