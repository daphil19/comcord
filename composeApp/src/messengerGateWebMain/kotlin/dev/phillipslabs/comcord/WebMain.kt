package dev.phillipslabs.comcord

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create
import web.dom.document
import web.events.Event
import web.events.EventType
import web.events.addEventHandler
import web.window.window

@OptIn(ExperimentalComposeUiApi::class)
suspend fun main() {
    val host = PlatformGateHost()
    lateinit var shutdown: () -> Unit
    val removeVisibility = document.addEventHandler(EventType<Event>("visibilitychange"), { _: Event ->
        if (document.hidden) host.stop() else host.resume()
    })
    val removePageHide = window.addEventHandler(EventType<Event>("pagehide"), { _: Event -> shutdown() })
    shutdown = {
        removeVisibility()
        removePageHide()
        host.destroy()
    }
    host.resume()
    ComposeViewport {
        host.Content(
            factory = { MatrixMultiMessenger.create { platformGateConfiguration() } },
            onClose = shutdown,
        )
    }
}
