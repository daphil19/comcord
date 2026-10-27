package dev.phillipslabs.comcord

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Each instance owns a new container, random host port, and disposable database. */
class SynapseContainer : AutoCloseable {
    private val data = Files.createTempDirectory("comcord-synapse-").toFile()
    private var containerId: String? = null
    lateinit var url: String
        private set

    fun start(): SynapseContainer {
        try {
            writeConfiguration()
            containerId =
                docker(
                    "run",
                    "--detach",
                    "--publish",
                    "127.0.0.1::8008",
                    "--volume",
                    "${data.absolutePath}:/data",
                    "ghcr.io/element-hq/synapse:v1.149.1",
                )
            containerId = checkNotNull(containerId).lineSequence().last()
            val port = docker("port", checkNotNull(containerId), "8008/tcp").substringAfterLast(':').trim()
            url = "http://127.0.0.1:$port"
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
            var ready = false
            while (System.nanoTime() < deadline && !ready) {
                ready =
                    runCatching {
                        client
                            .send(
                                HttpRequest
                                    .newBuilder(
                                        URI("$url/_matrix/client/versions"),
                                    ).timeout(Duration.ofSeconds(2))
                                    .build(),
                                HttpResponse.BodyHandlers.discarding(),
                            ).statusCode() == 200
                    }.getOrDefault(false)
                if (!ready) Thread.sleep(250)
            }
            check(ready) { "Synapse did not become ready:\n${docker("logs", checkNotNull(containerId))}" }
            docker(
                "exec",
                checkNotNull(containerId),
                "register_new_matrix_user",
                "-u",
                "alice",
                "-p",
                "alicepass",
                "--no-admin",
                "-c",
                "/data/homeserver.yaml",
                "http://localhost:8008",
            )
            return this
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    private fun writeConfiguration() {
        data.resolve("homeserver.yaml").writeText(
            """
            server_name: localhost
            report_stats: false
            pid_file: /data/synapse.pid
            listeners:
              - port: 8008
                tls: false
                type: http
                resources:
                  - names: [client]
            database:
              name: sqlite3
              args:
                database: /data/homeserver.db
            media_store_path: /data/media
            signing_key_path: /data/localhost.signing.key
            registration_shared_secret: smoke-test-secret
            macaroon_secret_key: smoke-test-macaroon
            suppress_key_server_warning: true
            # History fixtures send several pages without waiting for production rate limits.
            rc_message:
              per_second: 100
              burst_count: 1000
            """.trimIndent(),
        )
    }

    override fun close() {
        try {
            containerId?.let { docker("rm", "--force", it) }
        } finally {
            data.deleteRecursively()
        }
    }

    private fun docker(vararg arguments: String): String {
        val output = Files.createTempFile("comcord-docker-", ".log").toFile()
        try {
            val process =
                ProcessBuilder(
                    listOf("docker") + arguments,
                ).redirectErrorStream(true).redirectOutput(output).start()
            if (!process.waitFor(180, TimeUnit.SECONDS)) {
                process.destroyForcibly().waitFor()
                error("Docker command timed out: ${arguments.first()}")
            }
            check(process.exitValue() == 0) { "Docker ${arguments.first()} failed:\n${output.readText()}" }
            return output.readText().trim()
        } finally {
            output.delete()
        }
    }
}
