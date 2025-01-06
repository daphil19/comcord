package dev.phillipslabs.comcord

import androidx.compose.ui.window.ComposeUIViewController
import de.connect2x.trixnity.messenger.createRoot
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create
import de.connect2x.trixnity.messenger.multi.singleModeMatrixMessenger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import platform.UIKit.UIViewController

@Suppress("ktlint:standard:function-naming")
fun MainViewController(): UIViewController {
    val rootViewModel =
        runBlocking {
            MatrixMultiMessenger
                .create {
                    comcordMatrixConfig()
                }.singleModeMatrixMessenger()
                .first()
                .createRoot()
        }

    return ComposeUIViewController {
        App(rootViewModel)
    }
}
