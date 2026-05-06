package io.github.sceneview.gesture

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sign

internal fun pinchZoomDelta(
    prevSeparation: Float,
    currSeparation: Float,
    speed: Float,
    damping: Float,
): Float {
    val delta = prevSeparation - currSeparation
    val absDelta = abs(delta)
    val damped = if (absDelta > 1f) {
        sign(delta) * exp(ln(absDelta) * damping)
    } else {
        delta
    }
    return damped * speed
}

internal fun nextFov(
    currentFov: Double,
    prevSeparation: Float,
    currSeparation: Float,
    range: ClosedFloatingPointRange<Float>,
    speed: Float,
): Double {
    val delta = (prevSeparation - currSeparation) * speed
    return (currentFov + delta).coerceIn(
        range.start.toDouble(),
        range.endInclusive.toDouble(),
    )
}
