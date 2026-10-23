package dev.phillipslabs.comcord

import androidx.compose.ui.window.ComposeUIViewController
import de.connect2x.trixnity.messenger.createRoot
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create
import de.connect2x.trixnity.messenger.multi.singleModeMatrixMessenger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import platform.UIKit.UIViewController

// Keep the factory name used by the Swift application entry point.
@Suppress("ktlint:standard:function-naming", "FunctionNaming")
fun MainViewController(): UIViewController {
    val matrixMessenger =
        runBlocking {
            MatrixMultiMessenger
                .create {
                    comcordMatrixConfig()
                }.singleModeMatrixMessenger()
                .first()
        }
    val rootViewModel = matrixMessenger.createRoot()

    return ComposeUIViewController {
        App(rootViewModel, matrixMessenger)
    }
}
