package dev.phillipslabs.comcord

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.ConnectionSpec

fun createJvmComcordHttpEngine(): HttpClientEngine =
    OkHttp.create {
        config {
            connectionSpecs(listOf(ConnectionSpec.CLEARTEXT, ConnectionSpec.MODERN_TLS))
        }
    }
