package io.github.sceneview.ar

import com.google.android.filament.Engine
import com.google.android.filament.utils.KTX1Loader
import io.github.sceneview.ar.camera.ARCameraStream
import io.github.sceneview.ar.node.ARCameraNode
import io.github.sceneview.createEnvironment
import io.github.sceneview.loaders.MaterialLoader
import java.nio.Buffer

// ── AR-specific factory functions ────────────────────────────────────────────────────────────────
//
// Only factories that depend on ARCore types live here. Filament-only factories (including
// createARView) are in SceneFactories.kt in the base `sceneview` module.
// Corresponding remember* hooks are in ARScene.kt.
//
// AR-specific factory composables. These live in `arsceneview` to keep the ARCore dependency
// out of the lightweight `sceneview` module.
//

/**
 * Creates an [ARCameraNode] with default AR-appropriate exposure settings.
 *
 * The default exposure (aperture 16, shutter speed 1/125s, ISO 100) is tuned to match
 * ARCore's light estimation output so that virtual objects blend naturally with the
 * real-world camera feed.
 */
fun createARCameraNode(engine: Engine): ARCameraNode = ARDefaultCameraNode(engine)

/**
 * Creates an [ARCameraStream] that renders the device camera feed as the scene background.
 *
 * @param materialLoader The [MaterialLoader] used to build the camera background material.
 */
fun createARCameraStream(materialLoader: MaterialLoader) = ARCameraStream(materialLoader)

/**
 * Creates an AR-optimised [io.github.sceneview.environment.Environment] with no skybox
 * (transparent background so the camera feed shows through) and an optional IBL baseline.
 *
 * Pass [iblBuffer] (e.g. read from `assets/environments/neutral/neutral_ibl.ktx`) to give
 * PBR materials something sensible to reflect in the first frames before ARCore's
 * `ENVIRONMENTAL_HDR` light estimate stabilises (#1063). ARCore replaces the IBL each frame
 * in [ARScene]'s update loop once the estimate is available; without this baseline metals
 * show up jet-black until ARCore has had several frames of camera motion to learn the
 * environment.
 *
 * Prefer the [rememberAREnvironment] composable, which reads the bundled
 * `environments/neutral/neutral_ibl.ktx` automatically.
 */
fun createAREnvironment(
    engine: Engine,
    iblBuffer: Buffer? = null,
) = createEnvironment(
    engine = engine,
    isOpaque = true,
    indirectLight = iblBuffer?.let { KTX1Loader.createIndirectLight(engine, it).indirectLight },
    skybox = null,
)

/**
 * Default AR camera node with exposure tuned for ARCore light estimation.
 */
class ARDefaultCameraNode(engine: Engine) : ARCameraNode(engine) {
    init {
        setExposure(16.0f, 1.0f / 125.0f, 100.0f)
    }
}
