@file:JvmName("SceneviewMathKt")

package io.github.sceneview.math

import com.google.android.filament.Box

// Re-export all portable math from sceneview-core
// Type aliases, conversions, comparisons, transforms, etc. are all in sceneview-core commonMain.
// This file only contains Android/Filament/Compose-specific extensions.

/**
 * Build a Filament [Box] from SceneView's `Position` / `Size` types, saving
 * the `toFloatArray()` conversion at every call site. Equivalent to the
 * Filament constructor with two `FloatArray` triples but type-safe against
 * accidentally passing a position where a half-extent is expected.
 *
 * @param center centre of the AABB in local node coordinates.
 * @param halfExtent half-side lengths along x/y/z — i.e. half of the box's
 *   full width/height/depth, NOT the full extent. Mirrors Filament's
 *   convention.
 */
fun Box(center: Position, halfExtent: Size) = Box(center.toFloatArray(), halfExtent.toFloatArray())

/**
 * Type-safe accessor for [Box.center] as a `Position`. Reading allocates a
 * new `Position`; writing mutates the Filament box in place via
 * [Box.setCenter] (no allocation). Use when interoperating between SceneView
 * scene-graph math and raw Filament boxes returned from `Renderable.getAxisAlignedBoundingBox`.
 */
var Box.centerPosition: Position
    get() = center.toPosition()
    set(value) {
        setCenter(value.x, value.y, value.z)
    }

/**
 * Type-safe accessor for [Box.halfExtent] as a `Size`. Same allocation /
 * mutation profile as [centerPosition]. Note: this is the HALF-extent — the
 * full bounding-box width along x is `halfExtentSize.x * 2`.
 */
var Box.halfExtentSize: Size
    get() = halfExtent.toSize()
    set(value) {
        setHalfExtent(value.x, value.y, value.z)
    }

/**
 * Convert a Filament-format [Box] into the SceneView portable
 * [io.github.sceneview.collision.Box] used by the ray-cast / AABB
 * intersection helpers in `sceneview-core`. The portable box stores FULL
 * side lengths (not half-extents), so this multiplies by 2 — matching the
 * collision module's convention.
 */
fun Box.toVector3Box(): io.github.sceneview.collision.Box =
    io.github.sceneview.collision.Box(
        (halfExtentSize * 2.0f).toVector3(),
        centerPosition.toVector3()
    )

/**
 * Convert a Compose [androidx.compose.ui.graphics.Color] (sRGB, 0..1 channels)
 * into SceneView's internal `Color` representation. Components are passed
 * straight through — no gamma conversion happens here; use
 * [Color.toLinearSpace] (in `sceneview-core`) if the target material expects
 * linear-space RGB.
 */
fun colorOf(color: androidx.compose.ui.graphics.Color) = colorOf(
    r = color.red,
    g = color.green,
    b = color.blue,
    a = color.alpha
)

/**
 * Convert an Android packed-ARGB Int (e.g. `0xFF1A73E8` or
 * `Color.parseColor("#1A73E8")`) into SceneView's internal `Color`,
 * normalising each channel from 0..255 to 0..1. Same sRGB caveat as the
 * Compose overload.
 */
fun colorOf(color: Int) = colorOf(
    r = android.graphics.Color.red(color) / 255.0f,
    g = android.graphics.Color.green(color) / 255.0f,
    b = android.graphics.Color.blue(color) / 255.0f,
    a = android.graphics.Color.alpha(color) / 255.0f
)

// Color.toLinearSpace() is now in sceneview-core (io.github.sceneview.math.Color.kt)
// using the precise piecewise sRGB transfer function.
