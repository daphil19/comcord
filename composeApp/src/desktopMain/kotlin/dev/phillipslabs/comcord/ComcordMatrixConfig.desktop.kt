package dev.phillipslabs.comcord

import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*

actual val httpEngine: HttpClientEngine = CIO.create()
