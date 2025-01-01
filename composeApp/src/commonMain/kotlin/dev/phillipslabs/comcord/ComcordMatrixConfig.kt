package dev.phillipslabs.comcord

import de.connect2x.trixnity.messenger.MatrixMessengerConfiguration
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*

// TODO this is better than a function right?
val comcordMatrixConfig =
    MatrixMessengerConfiguration(
        // TODO anything else to override?
        appName = "Comcord",
        appId = "dev.phillipslabs.comcord",
        httpClientEngine = CIO.create(),
        defaultHomeServer = "https://matrix.org",
    )
