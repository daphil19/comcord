package dev.phillipslabs.comcord

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.ComposeUIViewController
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIViewController

// The controller owns the application host, independently of Account/room UI.
@OptIn(ExperimentalForeignApi::class)
@Suppress("ktlint:standard:function-naming", "FunctionNaming")
fun MainViewController(): UIViewController {
    val host = ComcordApplicationHost()
    return ComposeUIViewController {
        DisposableEffect(host) {
            val center = NSNotificationCenter.defaultCenter
            val active =
                center.addObserverForName(
                    UIApplicationDidBecomeActiveNotification,
                    null,
                    NSOperationQueue.mainQueue,
                ) { host.resume() }
            val stopped =
                center.addObserverForName(
                    UIApplicationDidEnterBackgroundNotification,
                    null,
                    NSOperationQueue.mainQueue,
                ) { host.stop() }
            if (UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive) {
                host.resume()
            }
            onDispose {
                center.removeObserver(active)
                center.removeObserver(stopped)
                host.destroy()
            }
        }
        host.Content(factory = { MatrixMultiMessenger.create { comcordMatrixConfig(host) } })
    }
}
