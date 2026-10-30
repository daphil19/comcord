package dev.phillipslabs.comcord

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create
import web.dom.document
import web.events.Event
import web.events.EventType
import web.events.addEventHandler
import web.history.PageTransitionEvent
import web.window.window

@OptIn(ExperimentalComposeUiApi::class)
suspend fun main() {
    val host = ComcordApplicationHost()
    val removeVisibility =
        document.addEventHandler(EventType<Event>("visibilitychange"), { _: Event ->
            if (document.hidden) host.stop() else host.resume()
        })
    // A cached page can return through pageshow. Keep its application lifetime,
    // but suspend foreground work whenever it leaves the visible host.
    lateinit var removeHide: () -> Unit
    val removeShow =
        window.addEventHandler(EventType<PageTransitionEvent>("pageshow"), { _: PageTransitionEvent ->
            if (!document.hidden) host.resume()
        })
    removeHide =
        window.addEventHandler(EventType<PageTransitionEvent>("pagehide"), { event: PageTransitionEvent ->
            host.stop()
            if (!event.persisted) {
                removeVisibility()
                removeShow()
                removeHide()
                // Browsers may terminate before asynchronous disk cleanup completes.
                host.destroy()
            }
        })
    if (!document.hidden) host.resume()
    ComposeViewport {
        host.Content(factory = { MatrixMultiMessenger.create { comcordMatrixConfig(host) } })
    }
}
