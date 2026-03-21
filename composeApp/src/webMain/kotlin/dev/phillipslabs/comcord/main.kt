package dev.phillipslabs.comcord

import androidx.compose.material.Text
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import de.connect2x.trixnity.messenger.createRoot
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create
import de.connect2x.trixnity.messenger.multi.singleModeMatrixMessenger
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalComposeUiApi::class)
 fun main() {
    ComposeViewport {
        Text("Hello, World!")
    }
//    val matrixMessenger =
//        MatrixMultiMessenger
//            .create { comcordMatrixConfig() }
//            .singleModeMatrixMessenger()
//            .first()
//    val rootViewModel = matrixMessenger.createRoot()
//
//    ComposeViewport {
//        App(rootViewModel, matrixMessenger)
//    }
}
