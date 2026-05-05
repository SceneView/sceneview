package io.github.sceneview.node

import android.graphics.Bitmap
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size

/**
 * A node that always faces the camera (billboard behaviour).
 *
 * A [BillboardNode] is an [ImageNode] (flat quad with a bitmap texture) that rotates toward the
 * camera every frame. Pass a [Bitmap] to display on the quad; call [setBitmap] to update it at
 * any time.
 *
 * Usage inside a [io.github.sceneview.SceneScope]:
 * ```kotlin
 * SceneView(onFrame = { cameraPos = cameraNode.worldPosition }) {
 *     BillboardNode(
 *         materialLoader = materialLoader,
 *         bitmap = myBitmap,
 *         widthMeters = 0.5f,
 *         heightMeters = 0.25f,
 *         cameraPositionProvider = { cameraPos }
 *     )
 * }
 * ```
 *
 * @param materialLoader         MaterialLoader used to create the image material instance.
 * @param bitmap                 The bitmap texture to display on the quad.
 * @param widthMeters            Width of the quad in world-space meters. Pass `null` to derive from
 *                               the bitmap's aspect ratio (longer edge = 1 unit).
 * @param heightMeters           Height of the quad in world-space meters. Pass `null` to derive
 *                               from the bitmap's aspect ratio.
 * @param cameraPositionProvider Lambda invoked every frame to obtain the current camera world
 *                               position. The node rotates to face this position.
 */
open class BillboardNode(
    materialLoader: MaterialLoader,
    bitmap: Bitmap,
    widthMeters: Float? = null,
    heightMeters: Float? = null,
    private val cameraPositionProvider: (() -> Position)? = null
) : ImageNode(
    materialLoader = materialLoader,
    bitmap = bitmap,
    size = if (widthMeters != null && heightMeters != null) Size(widthMeters, heightMeters) else null
) {

    init {
        // Keep the node facing the camera every frame.
        // kotlin-math `lookTowards(direction)` builds Mat4(right, up, -direction, eye), so
        // local +Z maps to -direction in world space. To make local +Z (the plane's front
        // face, with correct UVs) point toward the camera, we pass `worldPosition - camPos`
        // — local +Z then aligns with `camPos - worldPosition`, i.e. faces the camera.
        // `lookAt(camPos)` would do the opposite: -Z toward camera, +Z away → mirrored UVs.
        // `lookTowards` normalizes its argument internally, so no explicit normalize() needed.
        onFrame = { _ ->
            cameraPositionProvider?.invoke()?.let { camPos ->
                val direction = worldPosition - camPos
                val lengthSq =
                    direction.x * direction.x +
                        direction.y * direction.y +
                        direction.z * direction.z
                // `lengthSq > epsilon` rejects both the zero vector AND any NaN component
                // (NaN comparisons always return false), avoiding `normalize(0,0,0) → NaN`.
                if (lengthSq > 1e-12f) {
                    lookTowards(lookDirection = direction)
                }
            }
        }
    }

    /**
     * Convenience setter — updates the displayed bitmap and (optionally) re-sizes the quad.
     */
    fun updateBitmap(
        newBitmap: Bitmap,
        widthMeters: Float? = null,
        heightMeters: Float? = null
    ) {
        bitmap = newBitmap
        if (widthMeters != null && heightMeters != null) {
            updateGeometry(size = Size(widthMeters, heightMeters))
        }
    }
}
