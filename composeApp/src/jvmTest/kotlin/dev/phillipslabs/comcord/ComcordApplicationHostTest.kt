package dev.phillipslabs.comcord

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ComcordApplicationHostTest {
    @Test
    fun bootstrapTimeoutOffersRecoveryInsteadOfEscapingAsCancellation() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val host = ComcordApplicationHost()
            try {
                host.initialize { awaitCancellation() }
                assertIs<ComcordApplicationState.Failure>(host.state.value)
            } finally {
                host.closeSuspending()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun closingDuringStartupCancelsInitialization() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val host = ComcordApplicationHost()
            val started = CompletableDeferred<Unit>()
            val initialization =
                launch {
                    host.initialize {
                        started.complete(Unit)
                        awaitCancellation()
                    }
                }
            try {
                started.await()
                val closing = launch { host.closeSuspending() }
                testScheduler.runCurrent()
                assertEquals(ComcordApplicationState.Closed, host.state.value)
                assertTrue(initialization.isCancelled, "Closing must cancel unfinished startup")
                assertTrue(closing.isCompleted, "Closing must not wait for startup to finish")
            } finally {
                initialization.cancelAndJoin()
                host.closeSuspending()
                Dispatchers.resetMain()
            }
        }
}
