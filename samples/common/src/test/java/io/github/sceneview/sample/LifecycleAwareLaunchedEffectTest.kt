package io.github.sceneview.sample

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Regression tests for [LifecycleAwareLaunchedEffect].
 *
 * The helper is a one-liner — `LaunchedEffect { lifecycle.repeatOnLifecycle(
 * STARTED) { block() } }`. Its entire contract is therefore the behaviour of
 * `repeatOnLifecycle(STARTED)`: cancel `block` on `onStop`, re-run it from the
 * top on `onStart`. These tests drive that exact core against a real
 * [TestLifecycleOwner] (from `androidx.lifecycle:lifecycle-runtime-testing`),
 * so they fail if a future edit changes the target state or drops the
 * `repeatOnLifecycle` wrapper.
 *
 * A `@Composable` cannot be invoked from a plain JUnit test and
 * `:samples:common` ships no `compose-ui-test` runtime, so the `LaunchedEffect`
 * shell is pinned separately by [LifecycleAwareLaunchedEffectSourceTest]. The
 * behavioural part — the lifecycle binding the audit (#936) actually cares
 * about — is exercised for real here.
 *
 * Acceptance (#972): "the lifecycle restart-from-top test must explicitly
 * assert 'block ran twice across one onStop/onStart cycle'."
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LifecycleAwareLaunchedEffectTest {

    // `TestLifecycleOwner`'s state transitions hop through `Dispatchers.Main`
    // (the `LifecycleRegistry` dispatches observer callbacks there), and
    // `repeatOnLifecycle` itself does `withContext(Dispatchers.Main.immediate)`.
    // Installing one shared test dispatcher as Main keeps every transition on
    // the same single-threaded scheduler the test bodies use, so the
    // interleaving stays deterministic.
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun installMain() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `block runs while STARTED`() = runTest(mainDispatcher) {
        val owner = TestLifecycleOwner(Lifecycle.State.CREATED, mainDispatcher)
        var started = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                started = true
                awaitCancellation()
            }
        }

        assertFalse("block must not run before the lifecycle is STARTED", started)
        owner.currentState = Lifecycle.State.STARTED
        assertTrue("block must run once the lifecycle reaches STARTED", started)

        owner.currentState = Lifecycle.State.DESTROYED
        job.join()
    }

    @Test
    fun `block is cancelled on onStop`() = runTest(mainDispatcher) {
        val owner = TestLifecycleOwner(Lifecycle.State.STARTED, mainDispatcher)
        var cancelled = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    awaitCancellation()
                } finally {
                    // Cancellation here == the background-drain fix from #936:
                    // a `while(true)` loop stops burning CPU on `onStop`.
                    cancelled = true
                }
            }
        }

        assertFalse("block must still be active while STARTED", cancelled)
        owner.currentState = Lifecycle.State.CREATED // onStop
        assertTrue("block must be cancelled when the lifecycle drops below STARTED", cancelled)

        owner.currentState = Lifecycle.State.DESTROYED
        job.join()
    }

    @Test
    fun `block runs twice across one onStop onStart cycle`() = runTest(mainDispatcher) {
        // The honest UX trade-off the helper's KDoc documents: the body is
        // RESTARTED FROM THE TOP on every foreground, not resumed in place.
        // #972 demands this be asserted explicitly so the doc stays honest.
        val owner = TestLifecycleOwner(Lifecycle.State.STARTED, mainDispatcher)
        var runCount = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Incrementing at the TOP of the block is the point: a
                // restart-from-top helper re-executes this line every onStart.
                runCount++
                awaitCancellation()
            }
        }

        assertEquals("block should have run exactly once after first START", 1, runCount)

        owner.currentState = Lifecycle.State.CREATED // background (onStop)
        assertEquals("backgrounding must not re-run the block", 1, runCount)

        owner.currentState = Lifecycle.State.STARTED // foreground (onStart)
        assertEquals(
            "block must RE-RUN FROM THE TOP on onStart — this is the " +
                "documented restart-from-top trade-off of " +
                "LifecycleAwareLaunchedEffect (#936). If it should resume in " +
                "place, the demo must use LifecyclePausingLaunchedEffect.",
            2,
            runCount,
        )

        owner.currentState = Lifecycle.State.DESTROYED
        job.join()
    }

    @Test
    fun `block does not run again after DESTROYED`() = runTest(mainDispatcher) {
        val owner = TestLifecycleOwner(Lifecycle.State.STARTED, mainDispatcher)
        var runCount = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                runCount++
                awaitCancellation()
            }
        }
        assertEquals(1, runCount)

        owner.currentState = Lifecycle.State.DESTROYED
        job.join()
        assertEquals("repeatOnLifecycle must complete — and never re-run — after DESTROYED", 1, runCount)
    }
}

/**
 * Source-anchored pin for the `@Composable` shell of [LifecycleAwareLaunchedEffect]
 * that a plain JUnit test cannot exercise: that it still wraps the body in
 * `LaunchedEffect` + `repeatOnLifecycle(Lifecycle.State.STARTED)`. If a future
 * edit changes the target state (e.g. to `RESUMED`) or drops the wrapper, the
 * behavioural tests above no longer mirror production — this catches that.
 *
 * Same source-anchored convention as `:arsceneview`'s `ARCompletenessDefaultsTest`.
 */
class LifecycleAwareLaunchedEffectSourceTest {

    private val source: String by lazy {
        val f = File("src/main/java/io/github/sceneview/sample/LifecycleAwareLaunchedEffect.kt")
        assertTrue(
            "LifecycleAwareLaunchedEffect.kt not found at ${f.absolutePath}",
            f.exists(),
        )
        f.readText()
    }

    private fun body(fnSignature: String): String {
        val start = source.indexOf(fnSignature)
        assertTrue("could not find `$fnSignature` — was the helper renamed?", start >= 0)
        // Body of interest is short; the next `@Composable` (or EOF) bounds it.
        val next = source.indexOf("@Composable", start + fnSignature.length)
        return source.substring(start, if (next >= 0) next else source.length)
    }

    @Test
    fun `LifecycleAwareLaunchedEffect wraps the body in LaunchedEffect + repeatOnLifecycle STARTED`() {
        val body = body("fun LifecycleAwareLaunchedEffect(")
        assertTrue(
            "LifecycleAwareLaunchedEffect must run its body inside LaunchedEffect",
            body.contains("LaunchedEffect("),
        )
        assertTrue(
            "LifecycleAwareLaunchedEffect must gate the body on " +
                "repeatOnLifecycle(Lifecycle.State.STARTED) — changing the " +
                "target state breaks the background-drain fix (#936)",
            Regex("repeatOnLifecycle\\(\\s*Lifecycle\\.State\\.STARTED\\s*\\)")
                .containsMatchIn(body),
        )
    }

    @Test
    fun `LifecyclePausingLaunchedEffect runs its body without repeatOnLifecycle wrapping`() {
        // Pin the distinguishing contract of the state-preserving variant:
        // the body's own LaunchedEffect must NOT be wrapped in repeatOnLifecycle
        // (only the cheap observer coroutine is). Otherwise it would restart
        // from the top too — defeating its whole reason for existing (#974).
        val body = body("fun LifecyclePausingLaunchedEffect(")
        assertTrue(
            "LifecyclePausingLaunchedEffect must still invoke the body via LaunchedEffect(*keys)",
            Regex("LaunchedEffect\\(\\s*\\*keys\\s*\\)").containsMatchIn(body),
        )
        // The body-driving `LaunchedEffect(*keys)` block must call block(gate)
        // directly — not through repeatOnLifecycle.
        val keyedLaunch = body.indexOf("LaunchedEffect(*keys)")
        assertTrue("LaunchedEffect(*keys) not found in LifecyclePausingLaunchedEffect", keyedLaunch >= 0)
        val keyedBody = body.substring(keyedLaunch)
        assertFalse(
            "LifecyclePausingLaunchedEffect's body launch must NOT be wrapped " +
                "in repeatOnLifecycle — that would restart the body from the " +
                "top and reintroduce the #936 teleport bug it exists to fix",
            keyedBody.substringBefore("}").contains("repeatOnLifecycle"),
        )
    }
}
