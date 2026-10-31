// Register the single-use daemon before settings, dependency resolution, or compilation can stall.
val runId = requireNotNull(System.getenv("COMCORD_SMOKE_RUN_ID"))
val runDirectory = File(requireNotNull(System.getenv("COMCORD_SMOKE_RUN_DIR")))
require(runId.matches(Regex("[0-9a-f]{32}")) && runDirectory.name == runId && runDirectory.isDirectory)
val process = ProcessHandle.current()
val startedAt = process.info().startInstant().get().epochSecond
val registry = runDirectory.resolve("jvms").apply { mkdirs() }
registry.resolve("${process.pid()}.json").writeText(
    """{"pid":${process.pid()},"started_at":$startedAt,"run_id":"$runId"}""",
)
