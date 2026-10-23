@file:Suppress("ktlint:standard:filename")

package dev.phillipslabs.comcord

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import de.connect2x.trixnity.messenger.createRoot
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create
import de.connect2x.trixnity.messenger.multi.singleModeMatrixMessenger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

fun main() {
    val (matrixMessenger, rootViewModel) =
        runBlocking(Dispatchers.Main) {
            val messenger =
                MatrixMultiMessenger
                    .create { comcordMatrixConfig() }
                    .singleModeMatrixMessenger()
                    .first()
            messenger to messenger.createRoot()
        }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Comcord",
        ) {
            App(rootViewModel, matrixMessenger)
        }
    }
}
