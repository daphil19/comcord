package dev.phillipslabs.comcord

import io.ktor.client.engine.*
import io.ktor.client.engine.darwin.*

actual val httpEngine: HttpClientEngine = Darwin.create()
