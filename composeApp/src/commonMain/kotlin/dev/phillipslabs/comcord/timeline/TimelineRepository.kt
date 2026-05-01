package dev.phillipslabs.comcord.timeline

import dev.phillipslabs.comcord.rooms.RoomSummary
import kotlinx.coroutines.flow.Flow

interface TimelineRepository {
    fun observeRoom(room: RoomSummary): Flow<List<TimelineEventRecord>>

    suspend fun sendMessage(
        room: RoomSummary,
        senderId: String,
        senderName: String,
        body: String,
    ): Result<Unit>

    suspend fun retryMessage(
        room: RoomSummary,
        retryKey: String,
    ): Result<Unit>
}
