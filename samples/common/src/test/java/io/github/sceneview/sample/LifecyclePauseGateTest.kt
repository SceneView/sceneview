package io.github.sceneview.sample

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for [LifecyclePauseGate] — the state-preserving lifecycle
 * primitive behind [LifecyclePausingLaunchedEffect].
 *
 * Pins the contract that #974 depends on: a loop calling
 * [LifecyclePauseGate.awaitResumed] **parks in place** while the host activity
 * is backgrounded and **resumes from the same point** (no restart, no state
 * reset) on foreground. This is what guarantees `AnimationDemo`'s cinematic
 * camera does not teleport on Alt-Tab.
 *
 * Pure-JVM — the gate is plain coroutine/flow logic with no Android dependency.
 */
class LifecyclePauseGateTest {

    private fun gate(resumed: Boolean) =
        LifecyclePauseGate(MutableStateFlow(resumed))

    @Test
    fun `awaitResumed returns immediately when foregrounded`() = runBlocking {
        val gate = gate(resumed = true)
        // Must not suspend — a generous timeout still completes instantly.
        withTimeout(1_000) { gate.awaitResumed() }
        assertTrue(gate.isResumed)
    }

    @Test
    fun `awaitResumed parks while backgrounded and resumes on foreground`() = runBlocking {
        val resumed = MutableStateFlow(false)
        val gate = LifecyclePauseGate(resumed)

        val passedCheckpoint = CompletableDeferred<Unit>()
        val job = launch {
            gate.awaitResumed()
            passedCheckpoint.complete(Unit)
        }

        // Let the coroutine reach the gate and park.
        yield()
        yield()
        assertFalse(
            "coroutine must stay parked while backgrounded",
            passedCheckpoint.isCompleted,
        )

        // Foreground → the gate must release the parked coroutine.
        gate.setResumed(true)
        withTimeout(1_000) { passedCheckpoint.await() }
        assertTrue(passedCheckpoint.isCompleted)
        job.join()
    }

    @Test
    fun `loop state is preserved across a background-foreground cycle`() = runBlocking {
        // Models AnimationDemo's cinematic loop: a counter that must NOT reset
        // when the app is backgrounded mid-loop. With a restart-from-top helper
        // (LifecycleAwareLaunchedEffect) this counter would snap back to 0 —
        // exactly the #936 teleport bug. LifecyclePauseGate preserves it.
        //
        // Single-threaded `runBlocking` makes the interleaving deterministic:
        // the loop only advances when this test coroutine yields control.
        val resumed = MutableStateFlow(true)
        val gate = LifecyclePauseGate(resumed)

        var iterations = 0
        val job = launch {
            while (iterations < 5) {
                gate.awaitResumed()
                iterations++
                // Suspend after each step so the test coroutine can interleave
                // a lifecycle change between iterations.
                yield()
            }
        }

        // Let the loop run a couple of iterations, then background it.
        yield()
        yield()
        val countAtBackground = iterations
        assertTrue("loop should have advanced while foregrounded", countAtBackground > 0)
        assertTrue("loop should not have finished yet", countAtBackground < 5)
        gate.setResumed(false)

        // Give the loop every chance to (incorrectly) keep advancing while parked.
        repeat(20) { yield() }
        assertEquals(
            "loop must not advance while backgrounded",
            countAtBackground,
            iterations,
        )

        // Foreground — loop resumes from where it parked, counter intact.
        gate.setResumed(true)
        withTimeout(1_000) { job.join() }
        assertEquals("loop must complete all 5 iterations", 5, iterations)
        assertTrue(
            "loop must have resumed from the parked count, not restarted",
            iterations > countAtBackground,
        )
    }

    @Test
    fun `isResumed reflects the latest lifecycle state`() {
        val resumed = MutableStateFlow(true)
        val gate = LifecyclePauseGate(resumed)
        assertTrue(gate.isResumed)
        gate.setResumed(false)
        assertFalse(gate.isResumed)
        gate.setResumed(true)
        assertTrue(gate.isResumed)
    }
}
