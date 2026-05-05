package io.github.sceneview.demo.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import io.github.sceneview.SceneScope
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation

/**
 * Blender-style 3D axes gizmo at the origin (0, 0, 0).
 *
 * Renders three thin coloured cylinders aligned with the world axes:
 * - **X** in red (`+X` direction)
 * - **Y** in green (`+Y` direction)
 * - **Z** in blue (`+Z` direction)
 *
 * Useful as a static reference in editing/debug demos so the user always knows where
 * the world origin is and how their gestures map onto axes. Cheap to draw — three
 * low-poly cylinders with unlit-style colour materials.
 *
 * Each cylinder is built along its native +Y axis with the requested half-length on
 * each side of the centre, then rotated so it aligns with the target axis. The X
 * cylinder is rotated 90° around Z to lie along +X; the Z cylinder is rotated 90°
 * around X to lie along +Z; the Y cylinder is left unrotated.
 *
 * Drop into any [SceneScope] content block:
 * ```kotlin
 * SceneView(...) {
 *     Axes3DNode(materialLoader = materialLoader)
 *     // your content
 * }
 * ```
 *
 * @param materialLoader The scene's [MaterialLoader] — required to mint the three axis materials.
 * @param length         Total length of each axis cylinder in metres. The axis extends from
 *                       `-length/2` to `+length/2` along its direction. Default `0.5f`.
 * @param thickness      Cylinder radius in metres. Keep small (~1% of [length]) so the axes
 *                       don't visually dominate the scene. Default `0.005f`.
 * @param colorX         Colour of the X axis. Default red.
 * @param colorY         Colour of the Y axis. Default green.
 * @param colorZ         Colour of the Z axis. Default blue.
 */
@Composable
fun SceneScope.Axes3DNode(
    materialLoader: MaterialLoader,
    length: Float = 0.5f,
    thickness: Float = 0.005f,
    colorX: Color = Color.Red,
    colorY: Color = Color.Green,
    colorZ: Color = Color.Blue,
) {
    // Materials are minted once per (loader, colour) pair — `remember` ensures we don't
    // leak a fresh MaterialInstance on every recomposition.
    val materialX = remember(materialLoader, colorX) {
        materialLoader.createColorInstance(color = colorX)
    }
    val materialY = remember(materialLoader, colorY) {
        materialLoader.createColorInstance(color = colorY)
    }
    val materialZ = remember(materialLoader, colorZ) {
        materialLoader.createColorInstance(color = colorZ)
    }

    // X axis — cylinder lies along native +Y, rotate -90° around Z to point along +X.
    CylinderNode(
        radius = thickness,
        height = length,
        materialInstance = materialX,
        rotation = Rotation(x = 0f, y = 0f, z = -90f),
        position = Position(x = 0f, y = 0f, z = 0f),
    )
    // Y axis — cylinder native orientation already lies along +Y, no rotation needed.
    CylinderNode(
        radius = thickness,
        height = length,
        materialInstance = materialY,
        position = Position(x = 0f, y = 0f, z = 0f),
    )
    // Z axis — cylinder lies along native +Y, rotate +90° around X to point along +Z.
    CylinderNode(
        radius = thickness,
        height = length,
        materialInstance = materialZ,
        rotation = Rotation(x = 90f, y = 0f, z = 0f),
        position = Position(x = 0f, y = 0f, z = 0f),
    )
}
