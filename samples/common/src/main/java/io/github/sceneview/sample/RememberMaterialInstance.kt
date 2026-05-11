package io.github.sceneview.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.google.android.filament.MaterialInstance
import io.github.sceneview.loaders.MaterialLoader

/**
 * Creates a colored PBR [MaterialInstance] tied to the composition's
 * lifecycle. Equivalent to `materialLoader.createColorInstance(color)` but
 * automatically calls `destroyMaterialInstance(...)` in a
 * [DisposableEffect] `onDispose` when the composition leaves or any of the
 * keys change.
 *
 * Why this exists
 * ===============
 * Filament's `MaterialLoader.createColorInstance(...)` allocates a JNI
 * `MaterialInstance` handle (plus a small uniform buffer). Each handle
 * leaks GPU + JNI memory if it's never destroyed. Across the 11 demos that
 * pre-#937 allocated colored instances with no disposal, a single
 * navigation cycle (home → demo list → demo → home) leaked ~26 handles.
 *
 * Mirroring the `rememberModelInstance` / `rememberEngine` /
 * `rememberMaterialLoader` pattern, this helper inverts the contract: the
 * composable owns the resource lifecycle, not the demo.
 *
 * Usage
 * =====
 * ```kotlin
 * val sphereMaterial = rememberMaterialInstance(materialLoader,
 *     SceneViewColors.Primary)
 * ```
 *
 * `materialLoader` and the `key` are observed — a change to either swaps
 * the instance (destroying the old one) so re-keyable previews work
 * without leaks.
 *
 * @param materialLoader the [MaterialLoader] to allocate against. Usually
 *   obtained from `rememberMaterialLoader(engine)`.
 * @param color the PBR colour applied to the instance.
 * @param metallic 0..1, default 0.4 — same default as
 *   `MaterialLoader.createColorInstance`.
 * @param roughness 0..1, default 0.4 — same default.
 * @param reflectance 0..1, default 0.5 — same default.
 */
@Composable
fun rememberMaterialInstance(
    materialLoader: MaterialLoader,
    color: Color,
    metallic: Float = 0.4f,
    roughness: Float = 0.4f,
    reflectance: Float = 0.5f,
): MaterialInstance {
    val instance = remember(materialLoader, color, metallic, roughness, reflectance) {
        materialLoader.createColorInstance(color, metallic, roughness, reflectance)
    }
    DisposableEffect(instance) {
        onDispose { materialLoader.destroyMaterialInstance(instance) }
    }
    return instance
}

/**
 * Unlit variant of [rememberMaterialInstance]. Same disposal contract,
 * different shader path — see `MaterialLoader.createUnlitColorInstance`.
 *
 * Picks `unlit` materials for overlays / UI billboards where you DON'T
 * want the colour to react to scene lighting (e.g. plane visualisation,
 * debug grids, AR placement reticles).
 */
@Composable
fun rememberUnlitMaterialInstance(
    materialLoader: MaterialLoader,
    color: Color,
): MaterialInstance {
    val instance = remember(materialLoader, color) {
        materialLoader.createUnlitColorInstance(color)
    }
    DisposableEffect(instance) {
        onDispose { materialLoader.destroyMaterialInstance(instance) }
    }
    return instance
}
