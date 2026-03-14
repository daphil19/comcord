package dev.phillipslabs.comcord

import de.connect2x.trixnity.messenger.multi.MatrixMultiMessengerConfiguration
import io.ktor.client.engine.HttpClientEngine

// TODO this is better than a function right?
val comcordMatrixConfig: MatrixMultiMessengerConfiguration.() -> Unit = {
    appName = "Comcord"
    appId = "dev.phillipslabs.comcord"
    httpClientEngine = httpEngine
}

expect val httpEngine: HttpClientEngine
