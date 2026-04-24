package io.github.sceneview.demo

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Overlay that covers the 3D viewport while [loading] is true. Shows a centred spinner and
 * the [label] underneath so users know *why* the viewport is black. Fades out automatically
 * when [loading] flips to false (Compose removes the Box from the tree).
 *
 * Drop this inside a SceneView's content block OR over the whole Box that contains the
 * SceneView — the scrim is semi-transparent so the first rendered frame shows through.
 */
@Composable
fun LoadingScrim(loading: Boolean, label: String = "Loading…") {
    if (!loading) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f))
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(44.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Idle auto-orbit state for a camera that sweeps slowly around a target.
 *
 * Returns a [OrbitState] whose [yaw][OrbitState.yaw] advances from 0° to 360° in
 * [durationMillis] and resets. Converts to a `Position` on a circle of radius
 * [radius] at height [yHeight]. Wire this into a SceneView with
 * `cameraManipulator = rememberCameraManipulator(orbitHomePosition = state.toPosition())`
 * OR directly into a `CameraNode.position` via SideEffect.
 *
 * When [DemoSettings.qaMode] is `true` the orbit freezes at [staticYaw] so screenshot
 * captures are deterministic.
 *
 * @param durationMillis One full sweep in ms. 16 s feels natural at phone scale.
 * @param radius Orbit radius in metres.
 * @param yHeight Camera y offset (positive = above the target).
 * @param staticYaw Yaw angle to freeze at in QA mode (degrees). Default 45° gives a
 *                  clean 3/4 hero view.
 */
@Composable
fun rememberAutoOrbit(
    durationMillis: Int = 16_000,
    radius: Float = 2.5f,
    yHeight: Float = 0.8f,
    staticYaw: Float = 45f,
): OrbitState {
    val transition = rememberInfiniteTransition(label = "auto-orbit")
    val animatedYaw by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
        label = "orbit-yaw",
    )
    val yaw = if (DemoSettings.qaMode) staticYaw else animatedYaw
    return OrbitState(yaw = yaw, radius = radius, yHeight = yHeight)
}

/**
 * Pause the hero auto-rotate as soon as the user touches the viewport — they're
 * interacting and a spinning model fights their gestures. State persists across
 * recompositions so it stays paused for the rest of the demo session.
 *
 * Wire `onPause` into the SceneView's `onGestureListener`:
 *
 * ```kotlin
 * val (yaw, onUserGesture) = rememberPausableHeroYaw(modelInstance != null)
 * onGestureListener = rememberOnGestureListener(
 *     onSingleTapUp = { _, _ -> onUserGesture() },
 *     onDown = { _, _ -> onUserGesture() },
 *     onScroll = { _, _, _, _, _ -> onUserGesture() },
 * )
 * ```
 *
 * Or just call `onUserGesture()` from `onTouchEvent` for the broadest coverage.
 */
data class HeroYawController(val yaw: Float, val onUserGesture: () -> Unit)

@Composable
fun rememberPausableHeroYaw(
    trigger: Boolean,
    durationMillis: Int = 20_000,
    staticYaw: Float = 45f,
): HeroYawController {
    val pausedState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val paused = pausedState.value
    val anim = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(trigger, DemoSettings.qaMode, paused) {
        if (trigger && !DemoSettings.qaMode && !paused) {
            // Resume from current yaw if previously paused — no snap.
            val currentYaw = anim.value % 360f
            anim.snapTo(currentYaw)
            // Animate to next 360° boundary, then loop full sweeps.
            anim.animateTo(
                targetValue = currentYaw + (360f - currentYaw),
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = ((360f - currentYaw) / 360f * durationMillis).toInt()
                        .coerceAtLeast(1),
                    easing = androidx.compose.animation.core.LinearEasing,
                ),
            )
            while (true) {
                anim.snapTo(0f)
                anim.animateTo(
                    targetValue = 360f,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = durationMillis,
                        easing = androidx.compose.animation.core.LinearEasing,
                    ),
                )
            }
        }
    }
    val yaw = if (DemoSettings.qaMode) staticYaw else anim.value
    return HeroYawController(yaw = yaw, onUserGesture = { pausedState.value = true })
}

/**
 * Smooth y-axis hero-rotation that starts from 0° **only after** [trigger] becomes true.
 *
 * Using a plain `rememberInfiniteTransition` for an auto-rotate creates a visible
 * "snap" the moment a heavy GLB finishes loading: the InfiniteTransition has been
 * ticking from the start of composition, so by the time the model's first frame
 * renders the yaw is already at e.g. 144° — the model appears at 0° for one frame
 * and then jumps to the current animated value. This helper avoids that by starting
 * an [androidx.compose.animation.core.Animatable] sweep from 0° **only when**
 * [trigger] flips to true (e.g. when modelInstance becomes non-null), so the model's
 * first frame and the first animated frame are at the same yaw.
 *
 * Returns [staticYaw] (default 45°) when [DemoSettings.qaMode] is on so screenshot
 * tests get deterministic output.
 *
 * @param trigger Animation starts when this flips to `true`. Pass `modelInstance != null`.
 * @param durationMillis One full sweep in ms.
 * @param staticYaw Yaw to use in QA mode (degrees).
 */
@Composable
fun rememberHeroYaw(
    trigger: Boolean,
    durationMillis: Int = 20_000,
    staticYaw: Float = 45f,
): Float {
    val anim = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(trigger, DemoSettings.qaMode) {
        if (trigger && !DemoSettings.qaMode) {
            // Loop forever: 0° → 360° in `durationMillis`, then snap back to 0° and repeat.
            while (true) {
                anim.snapTo(0f)
                anim.animateTo(
                    targetValue = 360f,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = durationMillis,
                        easing = androidx.compose.animation.core.LinearEasing,
                    ),
                )
            }
        }
    }
    return if (DemoSettings.qaMode) staticYaw else anim.value
}

/**
 * Position on a horizontal orbit around the origin. Call [toPosition] to get an
 * `(x, y, z)` triple that swings around +Y by [yaw] degrees.
 */
data class OrbitState(val yaw: Float, val radius: Float, val yHeight: Float) {
    fun toPosition(): Triple<Float, Float, Float> {
        val rad = Math.toRadians(yaw.toDouble()).toFloat()
        return Triple(
            /* x = */ sin(rad) * radius,
            /* y = */ yHeight,
            /* z = */ cos(rad) * radius,
        )
    }
}
