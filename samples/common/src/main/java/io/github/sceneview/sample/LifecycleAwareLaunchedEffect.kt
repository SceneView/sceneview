package io.github.sceneview.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * Drop-in replacement for [LaunchedEffect] whose coroutine is automatically
 * paused while the host activity is backgrounded and resumed when it comes
 * back to the foreground.
 *
 * Why this exists
 * ===============
 * Plain `LaunchedEffect(keys) { while (true) { … } }` keeps running as long
 * as the composable is in the composition. Compose disposes the effect on
 * unmount, but **not** on activity backgrounding — so a `while (true)` loop
 * that drives an animation, polls a transform, or ticks a debug overlay
 * keeps burning CPU + GPU even when the user has the home screen open.
 *
 * Audit #936 measured ~5-10 mW of background drain per visible demo on a
 * Pixel 7a from this exact pattern. Wrapping the loop in
 * [Lifecycle.repeatOnLifecycle] with [Lifecycle.State.STARTED] cancels the
 * inner coroutine on every `onStop` and re-runs it on every `onStart`.
 *
 * Migration
 * =========
 * Replace:
 * ```kotlin
 * LaunchedEffect(mode) {
 *     while (true) {
 *         camera.animateTo(…)
 *     }
 * }
 * ```
 * with:
 * ```kotlin
 * LifecycleAwareLaunchedEffect(mode) {
 *     while (true) {
 *         camera.animateTo(…)
 *     }
 * }
 * ```
 *
 * The `block` body runs in the same coroutine scope shape as
 * `LaunchedEffect` (suspending lambda receiver = `CoroutineScope`), so
 * `delay`, `animateTo`, `snapTo`, `withContext`, etc. all compose unchanged.
 *
 * Keys behave exactly like `LaunchedEffect`'s — a change to any key cancels
 * the current coroutine (even if it's currently *paused* by the lifecycle)
 * and starts a new one.
 *
 * **Important UX caveat** — on every `onStop` → `onStart` cycle the body
 * is cancelled and **re-runs from the top**. For loops that capture
 * snapshot state in their first statements (e.g. `yawAnim.snapTo(0f)`
 * before a `while(true) { animateTo(...) }`), this teleports the user
 * back to the initial state on every Alt-Tab. Migrate ONLY loops that
 * (a) tolerate restart-from-top OR (b) read their starting value from
 * existing Compose state. For loops that must NOT lose their progress
 * across a background/foreground cycle — such as the cinematic camera
 * loops in `AnimationDemo` — use [LifecyclePausingLaunchedEffect]
 * instead, which suspends the coroutine in place rather than restarting
 * it. See #936 review and #974.
 *
 * @param keys arbitrary observation keys; a change to any key cancels and
 *   re-launches the effect, same as plain [LaunchedEffect].
 * @param block suspending body to run while the host lifecycle is at least
 *   [Lifecycle.State.STARTED]. Cancelled on every `onStop`, re-launched on
 *   every `onStart` until [Lifecycle.State.DESTROYED].
 */
@Composable
fun LifecycleAwareLaunchedEffect(
    vararg keys: Any?,
    block: suspend CoroutineScope.() -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(*keys) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            block()
        }
    }
}

/**
 * Lifecycle-bound suspension checkpoint handed to the body of a
 * [LifecyclePausingLaunchedEffect].
 *
 * A loop calls [awaitResumed] at a safe boundary — typically right before
 * each `animateTo` / `delay` step. While the host activity is foregrounded
 * the call returns immediately and is effectively free; while it is
 * backgrounded the call **suspends the running coroutine in place** until the
 * activity comes back to the foreground.
 *
 * Because the coroutine is suspended rather than cancelled, every piece of
 * loop-local state — `Animatable.value`, loop counters, the `when` arm in
 * progress — survives the background/foreground cycle untouched. This is the
 * difference from [LifecycleAwareLaunchedEffect], whose `repeatOnLifecycle`
 * cancels and **re-runs the block from the top** on every `onStart`.
 */
class LifecyclePauseGate internal constructor(
    private val resumed: MutableStateFlow<Boolean>,
) {
    internal fun setResumed(value: Boolean) {
        resumed.value = value
    }

    /** True while the host activity is at least [Lifecycle.State.STARTED]. */
    val isResumed: Boolean get() = resumed.value

    /**
     * Suspends until the host activity is at least [Lifecycle.State.STARTED].
     * Returns immediately (no dispatch) when already foregrounded.
     */
    suspend fun awaitResumed() {
        if (!resumed.value) {
            resumed.first { it }
        }
    }
}

/**
 * State-preserving lifecycle-aware variant of [LaunchedEffect].
 *
 * Unlike [LifecycleAwareLaunchedEffect] — which wraps the body in
 * [Lifecycle.repeatOnLifecycle] and therefore **cancels and restarts the body
 * from the top** on every `onStop` → `onStart` — this helper runs [block]
 * exactly once and never cancels it for lifecycle reasons (only a key change
 * or leaving the composition cancels it, same as plain [LaunchedEffect]).
 *
 * Lifecycle pausing is **cooperative**: the body receives a
 * [LifecyclePauseGate] and calls [LifecyclePauseGate.awaitResumed] at its own
 * safe checkpoints. While backgrounded that call parks the coroutine; all
 * loop-local state (e.g. `Animatable` values) is preserved, so on foreground
 * the loop resumes exactly where it left off — **no teleport, no reset.**
 *
 * Why this exists
 * ===============
 * `AnimationDemo`'s cinematic camera loops each begin a `when (cameraMode)`
 * arm with `xAnim.snapTo(initialValue)` to canonicalize state. With
 * [LifecycleAwareLaunchedEffect] a user who Alt-Tabs mid-orbit would see the
 * camera snap back to yaw=0 on return (#936 review → migration reverted in
 * `de692709`). This helper closes that gap: the loop drops a single
 * `gate.awaitResumed()` before each tween and the camera neither teleports
 * nor keeps burning CPU in the background.
 *
 * Usage
 * =====
 * ```kotlin
 * LifecyclePausingLaunchedEffect(cameraMode) { gate ->
 *     while (true) {
 *         gate.awaitResumed()          // parks here while backgrounded
 *         yawAnim.animateTo(360f, tween(10_000))
 *     }
 * }
 * ```
 *
 * @param keys arbitrary observation keys; a change to any key cancels and
 *   re-launches the effect, same as plain [LaunchedEffect].
 * @param block suspending body; receives the [LifecyclePauseGate] it must poll
 *   at its own checkpoints. Runs once and is not restarted on lifecycle events.
 */
@Composable
fun LifecyclePausingLaunchedEffect(
    vararg keys: Any?,
    block: suspend CoroutineScope.(gate: LifecyclePauseGate) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Seed with the lifecycle's *current* state so a launch while already
    // backgrounded (e.g. config-change race) parks immediately instead of
    // running a frame of animation off-screen.
    val resumed = remember {
        MutableStateFlow(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    val gate = remember(resumed) { LifecyclePauseGate(resumed) }

    // Keep `resumed` in sync with the host lifecycle. repeatOnLifecycle's body
    // is only active while STARTED, so entering it == foreground and its
    // cancellation (finally) == background. This observer coroutine is cheap
    // and independent of `keys`, so it survives key changes untouched.
    LaunchedEffect(gate, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            gate.setResumed(true)
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                gate.setResumed(false)
            }
        }
    }

    LaunchedEffect(*keys) {
        block(gate)
    }
}
