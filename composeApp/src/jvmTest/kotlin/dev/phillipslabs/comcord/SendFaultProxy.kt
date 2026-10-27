package dev.phillipslabs.comcord

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Fault injection at the HTTP boundary; SDK outbox and all Matrix behavior remain real. */
class SendFaultProxy(
    private val upstream: String,
) : AutoCloseable {
    private val executor = Executors.newCachedThreadPool()
    private val client = HttpClient.newBuilder().build()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    @Volatile private var held: CountDownLatch? = null

    @Volatile var sendArrived = false
        private set

    @Volatile private var failSend = false
    val url: String get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.executor = executor
        server.createContext("/") { exchange ->
            try {
                if (exchange.requestURI.rawPath.contains("/send/")) {
                    sendArrived = true
                    held?.await(30, TimeUnit.SECONDS)
                    if (failSend) {
                        val bytes = """{"errcode":"M_FORBIDDEN","error":"Controlled gate send failure"}""".toByteArray()
                        exchange.responseHeaders.add("Content-Type", "application/json")
                        exchange.sendResponseHeaders(403, bytes.size.toLong())
                        exchange.responseBody.use { it.write(bytes) }
                        return@createContext
                    }
                }
                val request =
                    HttpRequest
                        .newBuilder(URI(upstream + exchange.requestURI.toASCIIString()))
                        .method(
                            exchange.requestMethod,
                            HttpRequest.BodyPublishers.ofByteArray(exchange.requestBody.readBytes()),
                        )
                exchange.requestHeaders.forEach { (key, values) ->
                    if (key.lowercase() !in
                        setOf("host", "connection", "content-length", "upgrade", "http2-settings")
                    ) {
                        values.forEach { request.header(key, it) }
                    }
                }
                val response = client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray())
                response.headers().map().forEach { (key, values) ->
                    if (key.lowercase() !in setOf("transfer-encoding", "content-length", "connection")) {
                        values.forEach { exchange.responseHeaders.add(key, it) }
                    }
                }
                exchange.sendResponseHeaders(response.statusCode(), response.body().size.toLong())
                exchange.responseBody.use { it.write(response.body()) }
            } finally {
                exchange.close()
            }
        }
        server.start()
    }

    fun holdNextSend() {
        sendArrived = false
        held = CountDownLatch(1)
    }

    fun releaseSend() {
        held?.countDown()
        held = null
    }

    fun failSends(enabled: Boolean) {
        failSend = enabled
    }

    override fun close() {
        releaseSend()
        server.stop(0)
        executor.shutdownNow()
    }
}
