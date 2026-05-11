package io.github.sceneview.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope

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
 * existing Compose state. Camera cinematic loops in `AnimationDemo` are
 * the documented counter-example and stayed on plain `LaunchedEffect`.
 * See #936 review.
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
