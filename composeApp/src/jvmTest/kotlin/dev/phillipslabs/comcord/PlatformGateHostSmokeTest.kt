package dev.phillipslabs.comcord

import org.junit.Assume.assumeTrue
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test

/** Opt-in disposable host for the experimental Android/browser/iOS gate. */
class PlatformGateHostSmokeTest {
    @Test
    fun hostDisposablePlatformHomeserver() {
        assumeTrue(System.getenv("COMCORD_GATE_HOST") == "1")
        val stop = File("/tmp/comcord-platform-stop")
        check(!stop.exists()) { "Remove the previous gate stop file before starting a new host" }
        SynapseContainer().start().use { synapse ->
            synapse.register("bob", "bobpass")
            SendFaultProxy(synapse.url, port = 18723, browserCors = true).use { proxy ->
                val ready = File("/tmp/comcord-platform-ready")
                try {
                    ready.writeText(proxy.url)
                    val deadline = System.nanoTime() + TimeUnit.HOURS.toNanos(2)
                    while (!stop.exists() && System.nanoTime() < deadline) {
                        Thread.sleep(250)
                    }
                    check(stop.exists()) { "Platform gate host exceeded its two-hour safety deadline" }
                } finally {
                    ready.delete()
                }
            }
        }
    }
}
