package dev.phillipslabs.comcord

import androidx.compose.ui.window.ComposeUIViewController
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import platform.UIKit.willMoveToParentViewController
import platform.UIKit.removeFromParentViewController

/** Experiment-only controller; actual UIKit appearance and application notifications drive its lifetime. */
@OptIn(ExperimentalForeignApi::class)
@Suppress("ktlint:standard:function-naming", "FunctionNaming")
fun MainViewController(): UIViewController = PlatformGateContainer()

@OptIn(ExperimentalForeignApi::class)
private class PlatformGateContainer : UIViewController(null, null) {
    override fun viewDidLoad() {
        super.viewDidLoad()
        val child = PlatformGateController()
        addChildViewController(child)
        view.addSubview(child.view)
        child.view.setFrame(view.bounds)
        child.didMoveToParentViewController(this)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PlatformGateController : UIViewController(null, null) {
    private val host = PlatformGateHost()
    private val center = NSNotificationCenter.defaultCenter
    private val active = center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { host.resume() }
    private val stopped = center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) { host.stop() }

    override fun viewDidLoad() {
        super.viewDidLoad()
        val compose = ComposeUIViewController {
            host.Content(
                factory = { MatrixMultiMessenger.create { platformGateConfiguration() } },
                onClose = {
                    willMoveToParentViewController(null)
                    view.removeFromSuperview()
                    removeFromParentViewController()
                    dispose()
                },
            )
        }
        addChildViewController(compose)
        view.addSubview(compose.view)
        compose.view.setFrame(view.bounds)
        compose.didMoveToParentViewController(this)
        host.resume()
    }

    private fun dispose() {
        center.removeObserver(active)
        center.removeObserver(stopped)
        host.destroy()
    }

    override fun viewDidDisappear(animated: Boolean) {
        super.viewDidDisappear(animated)
        if (isBeingDismissed() || parentViewController == null) {
            dispose()
        }
    }
}
