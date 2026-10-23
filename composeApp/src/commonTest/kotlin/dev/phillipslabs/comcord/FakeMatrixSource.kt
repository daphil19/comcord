package dev.phillipslabs.comcord

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeMatrixSource(
    override val accountId: String,
) : MatrixSource {
    private val mutableDirectory = MutableStateFlow(MatrixDirectory())
    override val directory = mutableDirectory.asStateFlow()
    private val mutableHiddenViewIds = MutableStateFlow<Set<String>>(emptySet())
    override val hiddenViewIds = mutableHiddenViewIds.asStateFlow()

    override suspend fun setViewHidden(
        id: String,
        hidden: Boolean,
    ) {
        mutableHiddenViewIds.value = if (hidden) hiddenViewIds.value + id else hiddenViewIds.value - id
    }

    override suspend fun loadAvatar(url: String): ByteArray? = null

    var loggedOut = false
        private set

    fun emitSnapshot(directory: MatrixDirectory) {
        mutableDirectory.value = directory
    }

    override suspend fun logout(): Result<Unit> {
        loggedOut = true
        return Result.success(Unit)
    }
}
