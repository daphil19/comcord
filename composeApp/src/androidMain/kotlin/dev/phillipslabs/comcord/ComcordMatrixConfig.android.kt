package dev.phillipslabs.comcord

import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*

actual val httpEngine: HttpClientEngine = OkHttp.create()
