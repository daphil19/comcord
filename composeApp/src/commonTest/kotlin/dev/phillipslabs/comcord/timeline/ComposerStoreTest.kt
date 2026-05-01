package dev.phillipslabs.comcord.timeline

import dev.phillipslabs.comcord.rooms.RoomKind
import dev.phillipslabs.comcord.rooms.RoomSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposerStoreTest {
    private val alice = "@alice:example.com"
    private val bob = "@bob:example.com"
    private val general = RoomSummary(id = "!room:example.com", name = "General", kind = RoomKind.Room)
    private val random = RoomSummary(id = "!other:example.com", name = "Random", kind = RoomKind.Room)

    @Test
    fun `send message clears draft on success`() = runTest {
        val repository = RecordingTimelineRepository()
        val store = ComposerStore(repository)
        store.setContext(alice, general)
        store.updateDraft("Hello world")

        store.sendMessage(senderId = "@alice:example.com", senderName = "Alice")

        assertEquals("", store.state.value.draft)
        assertFalse(store.state.value.isSending)
        assertEquals("Hello world", repository.lastMessageBody)
    }

    @Test
    fun `send message keeps draft on failure`() = runTest {
        val repository = RecordingTimelineRepository(shouldFail = true)
        val store = ComposerStore(repository)
        store.setContext(alice, general)
        store.updateDraft("Will fail")

        store.sendMessage(senderId = "@alice:example.com", senderName = "Alice")

        assertEquals("Will fail", store.state.value.draft)
        assertTrue(store.state.value.errorMessage?.contains("Unable") == true)
    }

    @Test
    fun `drafts are restored per account and room`() = runTest {
        val repository = RecordingTimelineRepository()
        val store = ComposerStore(repository)

        store.setContext(alice, general)
        store.updateDraft("Alice general")
        store.setContext(alice, random)
        store.updateDraft("Alice random")
        store.setContext(bob, general)
        store.updateDraft("Bob general")

        store.setContext(alice, general)
        assertEquals("Alice general", store.state.value.draft)

        store.setContext(alice, random)
        assertEquals("Alice random", store.state.value.draft)

        store.setContext(bob, general)
        assertEquals("Bob general", store.state.value.draft)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `send success does not clear newly selected room draft`() = runTest {
        val sendResult = CompletableDeferred<Result<Unit>>()
        val repository = DeferredTimelineRepository(sendResult)
        val store = ComposerStore(repository)
        store.setContext(alice, general)
        store.updateDraft("General message")

        val sendJob = launch { store.sendMessage(senderId = alice, senderName = "Alice") }
        runCurrent()

        store.setContext(alice, random)
        store.updateDraft("Random message")
        sendResult.complete(Result.success(Unit))
        sendJob.join()

        assertEquals("Random message", store.state.value.draft)
        assertFalse(store.state.value.isSending)
        assertEquals(null, store.state.value.errorMessage)

        store.setContext(alice, general)
        assertEquals("", store.state.value.draft)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `send failure does not show error in newly selected room`() = runTest {
        val sendResult = CompletableDeferred<Result<Unit>>()
        val repository = DeferredTimelineRepository(sendResult)
        val store = ComposerStore(repository)
        store.setContext(alice, general)
        store.updateDraft("General message")

        val sendJob = launch { store.sendMessage(senderId = alice, senderName = "Alice") }
        runCurrent()

        store.setContext(alice, random)
        store.updateDraft("Random message")
        sendResult.complete(Result.failure(IllegalStateException("Unable to send")))
        sendJob.join()

        assertEquals("Random message", store.state.value.draft)
        assertFalse(store.state.value.isSending)
        assertEquals(null, store.state.value.errorMessage)

        store.setContext(alice, general)
        assertEquals("General message", store.state.value.draft)
    }

    private class RecordingTimelineRepository(
        private val shouldFail: Boolean = false,
    ) : TimelineRepository {
        var lastMessageBody: String? = null

        override fun observeRoom(room: RoomSummary) = emptyFlow<List<TimelineEventRecord>>()

        override suspend fun sendMessage(
            room: RoomSummary,
            senderId: String,
            senderName: String,
            body: String,
        ): Result<Unit> {
            if (shouldFail) return Result.failure(IllegalStateException("Unable to send"))
            lastMessageBody = body
            return Result.success(Unit)
        }

        override suspend fun retryMessage(
            room: RoomSummary,
            retryKey: String,
        ) = Result.success(Unit)
    }

    private class DeferredTimelineRepository(
        private val sendResult: CompletableDeferred<Result<Unit>>,
    ) : TimelineRepository {
        override fun observeRoom(room: RoomSummary) = emptyFlow<List<TimelineEventRecord>>()

        override suspend fun sendMessage(
            room: RoomSummary,
            senderId: String,
            senderName: String,
            body: String,
        ): Result<Unit> = sendResult.await()

        override suspend fun retryMessage(
            room: RoomSummary,
            retryKey: String,
        ) = Result.success(Unit)
    }
}
