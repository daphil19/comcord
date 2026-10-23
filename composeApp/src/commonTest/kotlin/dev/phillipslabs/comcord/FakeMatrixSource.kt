package dev.phillipslabs.comcord

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeMatrixSource(
    override val accountId: String,
) : MatrixSource {
    private val mutableDirectory = MutableStateFlow(MatrixDirectory())
    override val directory = mutableDirectory.asStateFlow()
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
