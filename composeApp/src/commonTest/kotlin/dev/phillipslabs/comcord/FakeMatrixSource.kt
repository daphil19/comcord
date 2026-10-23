package dev.phillipslabs.comcord

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeMatrixSource(
    override val accountId: String,
) : MatrixSource {
    private val mutableDirectory = MutableStateFlow(emptyRoomDirectory())
    override val directory = mutableDirectory.asStateFlow()
    var loggedOut = false
        private set

    fun emitDirectory(directory: RoomDirectoryState) {
        mutableDirectory.value = directory
    }

    override suspend fun logout(): Result<Unit> {
        loggedOut = true
        return Result.success(Unit)
    }
}
