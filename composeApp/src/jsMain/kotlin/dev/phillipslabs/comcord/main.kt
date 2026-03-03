package dev.phillipslabs.comcord

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import de.connect2x.trixnity.messenger.createRoot
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create
import de.connect2x.trixnity.messenger.multi.singleModeMatrixMessenger
import kotlinx.browser.document
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalComposeUiApi::class)
suspend fun main() {
    val rootViewModel =
        MatrixMultiMessenger
            .create { comcordMatrixConfig() }
            .singleModeMatrixMessenger()
            .first()
            .createRoot()

    ComposeViewport(document.body!!) {
        App(rootViewModel)
    }
}
