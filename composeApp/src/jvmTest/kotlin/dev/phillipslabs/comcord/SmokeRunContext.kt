package dev.phillipslabs.comcord

import java.io.File

/** Optional ownership metadata supplied only by the bounded repository runner. */
internal object SmokeRunContext {
    private val runId = System.getenv("COMCORD_SMOKE_RUN_ID")
    private val directory = System.getenv("COMCORD_SMOKE_RUN_DIR")?.let(::File)

    init {
        if (runId != null || directory != null) {
            require(runId?.matches(Regex("[0-9a-f]{32}")) == true && directory?.name == runId) {
                "Use scripts/run_matrix_smoke.py to supply matching smoke-run ownership metadata"
            }
        }
    }

    fun dockerLabels(): List<String> =
        runId?.let { listOf("--label", "dev.phillipslabs.comcord.smoke-run=$it") }.orEmpty()

    fun registerJvm() {
        val root = directory ?: return
        val process = ProcessHandle.current()
        val registry = root.resolve("jvms").apply { mkdirs() }
        val startedAt =
            process
                .info()
                .startInstant()
                .get()
                .epochSecond
        registry.resolve("${process.pid()}.json").writeText(
            """{"pid":${process.pid()},"started_at":$startedAt,"run_id":"$runId"}""",
        )
    }
}
