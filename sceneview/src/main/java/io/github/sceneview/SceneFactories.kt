package io.github.sceneview

import android.content.Context
import android.opengl.EGLContext
import com.google.android.filament.ColorGrading
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.Filament
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Skybox
import com.google.android.filament.ToneMapper
import com.google.android.filament.View
import com.google.android.filament.View.AntiAliasing
import com.google.android.filament.View.QualityLevel
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.Utils
import io.github.sceneview.collision.CollisionSystem
import io.github.sceneview.environment.Environment
import io.github.sceneview.gesture.CameraGestureDetector
import io.github.sceneview.loaders.EnvironmentLoader
import io.github.sceneview.managers.color
import io.github.sceneview.math.Position
import io.github.sceneview.math.colorOf
import io.github.sceneview.math.toColor
import io.github.sceneview.node.CameraNode
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.ViewNode
import io.github.sceneview.utils.OpenGL
import io.github.sceneview.utils.readBuffer

// Initialize Filament once (triggered when this file's class is first loaded)
private val filamentInit: Unit = run {
    Gltfio.init()
    Filament.init()
    Utils.init()
}

const val DEFAULT_MAIN_LIGHT_COLOR_TEMPERATURE = 6_500.0f

/**
 * Main directional light intensity (lux).
 *
 * Lowered from the previous photographic value of `100_000` (full noon sun) to `10_000` —
 * closer to RealityKit's default sun on iOS — so default Filament renders no longer look
 * washed out / blown out. Combined with the secondary fill light
 * ([DEFAULT_FILL_LIGHT_COLOR_INTENSITY]), this yields a balanced 3-point–style key+fill
 * setup out of the box. See audit `project_plan_v1_hybrid_2026-05-10`.
 */
const val DEFAULT_MAIN_LIGHT_COLOR_INTENSITY = 10_000.0f
val DEFAULT_MAIN_LIGHT_COLOR = Colors.cct(DEFAULT_MAIN_LIGHT_COLOR_TEMPERATURE).toColor()
val DEFAULT_MAIN_LIGHT_INTENSITY = DEFAULT_MAIN_LIGHT_COLOR_INTENSITY

/**
 * Secondary "fill" directional light — softens the shadows cast by the main light.
 *
 * Color temperature matches the main light (neutral 6500 K). Intensity is 30 % of the main
 * light, matching the ratio used by RealityKit's default scene lighting (sun ~1000, fill ~300).
 */
const val DEFAULT_FILL_LIGHT_COLOR_TEMPERATURE = 6_500.0f
const val DEFAULT_FILL_LIGHT_COLOR_INTENSITY = 3_000.0f
val DEFAULT_FILL_LIGHT_COLOR = Colors.cct(DEFAULT_FILL_LIGHT_COLOR_TEMPERATURE).toColor()
val DEFAULT_FILL_LIGHT_INTENSITY = DEFAULT_FILL_LIGHT_COLOR_INTENSITY

val DEFAULT_OBJECT_POSITION = Position(0.0f, 0.0f, -4.0f)

/**
 * Default `IndirectLight` intensity (lux).
 *
 * Lowered from Filament's hard-coded `30_000` default — too bright after the v4.1.0
 * main+fill rebalancing (10k+3k direct + 30k IBL = ambient dominated everything,
 * shadows looked weak, key-vs-fill ratio invisible). 10k matches the main light so
 * direct + indirect are roughly balanced (≈ 60/40), giving the carefully-tuned 3-point
 * setup actual visible contrast. See [#1075](https://github.com/sceneview/sceneview/issues/1075).
 *
 * Cross-platform parity note: iOS RealityKit uses `IBLComponent.intensityExponent = 0`
 * which exposes-out at ≈1000 lux equivalent. Android stays at 10× that for now (Filament
 * doesn't have an exposure-relative IBL knob); the absolute values diverge but the
 * key:IBL ratio matches.
 */
const val DEFAULT_IBL_INTENSITY = 10_000.0f

fun createEglContext(): EGLContext {
    filamentInit  // ensure init
    return OpenGL.createEglContext()
}

fun createEngine(eglContext: EGLContext): Engine = Engine.create(eglContext)

fun createScene(engine: Engine): com.google.android.filament.Scene = engine.createScene()

fun createView(engine: Engine): View = engine.createView().apply {
    renderQuality = renderQuality.apply {
        hdrColorBuffer = QualityLevel.MEDIUM
    }
    dynamicResolutionOptions = dynamicResolutionOptions.apply {
        enabled = false
        homogeneousScaling = true
        quality = QualityLevel.MEDIUM
    }
    multiSampleAntiAliasingOptions = multiSampleAntiAliasingOptions.apply {
        enabled = false
    }
    antiAliasing = AntiAliasing.FXAA
    // SSAO on by default — adds visible grounding under geometry crevices (toy_car, helmet)
    // without artifacts on diffuse-only models. Validated 2026-05-11 on Pixel_7a GPU host.
    ambientOcclusionOptions = ambientOcclusionOptions.apply {
        enabled = true
    }
    // Subtle bloom on by default — strength 0.10 lifts metallic/emissive highlights
    // (satin chrome, light filaments) but is invisible on diffuse-only assets, so it costs
    // nothing on plain models. Push higher only for cinematic scenes.
    bloomOptions = bloomOptions.apply {
        enabled = true
        strength = 0.1f
    }
    // Keep Filmic tone mapper as default. ACES was tested on 2026-05-11 and shifts PBR
    // hero shots (DamagedHelmet) toward a cooler/desaturated film grade — fine for cinema
    // but not the SDK's job to impose. Users can opt into ACES via `view.colorGrading`.
    colorGrading = ColorGrading.Builder()
        .toneMapper(ToneMapper.Filmic())
        .build(engine)
    // Shadows on by default — matches RealityKit on iOS and produces a more grounded look
    // out of the box. Disable via `View.setShadowingEnabled(false)` when not needed.
    setShadowingEnabled(true)
}

/**
 * Creates a [View] tuned for AR (ARScene).
 *
 * **Note:** This factory lives in the `sceneview` module (not `arsceneview`) because it only
 * depends on Filament — no ARCore types are involved. The `arsceneview` module calls it via
 * [rememberARView]. This avoids duplicating Filament View configuration across modules.
 *
 * The key difference from [createView] is the tone mapper: AR uses [ToneMapper.Linear] (identity)
 * instead of [ToneMapper.Filmic].
 *
 * The AR camera stream material applies `inverseTonemapSRGB()` in its fragment shader to convert
 * the camera image from sRGB gamma space into Filament's linear working space. If a non-linear
 * tone mapper (e.g. Filmic) is then applied as a post-process step, the camera feed gets
 * additionally curved — resulting in oversaturation, high contrast, and vignetting (issue #657).
 *
 * With [ToneMapper.Linear] the post-process step is a passthrough, so the full pipeline for the
 * camera background becomes:
 *
 *   camera sRGB → inverseTonemapSRGB → linear → ToneMapper.Linear (passthrough) → sRGB output
 *                                                                              = original image ✓
 *
 * Shadows are enabled by default because AR users commonly want 3D content to cast shadows on
 * detected planes. Disable via `View.setShadowingEnabled(false)` when not needed.
 */
fun createARView(engine: Engine): View = engine.createView().apply {
    renderQuality = renderQuality.apply {
        hdrColorBuffer = QualityLevel.MEDIUM
    }
    dynamicResolutionOptions = dynamicResolutionOptions.apply {
        enabled = false
        homogeneousScaling = true
        quality = QualityLevel.MEDIUM
    }
    multiSampleAntiAliasingOptions = multiSampleAntiAliasingOptions.apply {
        enabled = false
    }
    antiAliasing = AntiAliasing.FXAA
    ambientOcclusionOptions = ambientOcclusionOptions.apply {
        enabled = false
    }
    // Linear tone mapper: passthrough, preserves the camera background unchanged.
    // See KDoc above for the full explanation.
    colorGrading = ColorGrading.Builder()
        .toneMapper(ToneMapper.Linear())
        .build(engine)
    // Shadows on by default for AR: models casting shadows onto detected planes.
    setShadowingEnabled(true)
}

fun createRenderer(engine: Engine): Renderer = engine.createRenderer()

fun createCameraNode(engine: Engine): CameraNode = DefaultCameraNode(engine)

fun createMainLightNode(engine: Engine): LightNode = DefaultLightNode(engine)

/**
 * Creates the secondary "fill" directional light.
 *
 * Pairs with [createMainLightNode] to produce a soft key+fill setup similar to the default
 * RealityKit lighting on iOS. The fill light is offset from the main light direction so it
 * lifts the unlit side of objects without flattening them, and does not cast shadows
 * (only the main light contributes shadows by default).
 */
fun createFillLightNode(engine: Engine): LightNode = DefaultFillLightNode(engine)

fun createDefaultCameraManipulator(
    orbitHomePosition: Position? = null,
    targetPosition: Position? = null
) = CameraGestureDetector.DefaultCameraManipulator(
    orbitHomePosition = orbitHomePosition,
    targetPosition = targetPosition
)

fun createViewNodeManager(context: Context) = ViewNode.WindowManager(context)

fun createEnvironment(
    environmentLoader: EnvironmentLoader,
    isOpaque: Boolean = true
) = createEnvironment(
    engine = environmentLoader.engine,
    isOpaque = isOpaque,
    indirectLight = KTX1Loader.createIndirectLight(
        environmentLoader.engine,
        environmentLoader.context.assets.readBuffer("environments/neutral/neutral_ibl.ktx"),
    ).indirectLight?.also { it.intensity = DEFAULT_IBL_INTENSITY },
)

fun createEnvironment(
    engine: Engine,
    isOpaque: Boolean = true,
    indirectLight: IndirectLight? = null,
    skybox: Skybox? = Skybox.Builder()
        .color(colorOf(rgb = 0.0f, a = if (isOpaque) 1.0f else 0.0f).toFloatArray())
        .build(engine),
    sphericalHarmonics: List<Float>? = null
) = Environment(indirectLight, skybox, sphericalHarmonics)

fun createCollisionSystem(view: View) = CollisionSystem(view)

class DefaultCameraNode(engine: Engine) : CameraNode(engine) {
    init {
        transform = io.github.sceneview.math.Transform(position = Position(0.0f, 0.0f, 1.0f))
        // Neutral, less photographic exposure.
        // The previous setting (`f/16, 1/125 s, ISO 100` ≈ EV 15) is "sunny-16" — a real-world
        // outdoor exposure that makes Filament renders look much darker than the iOS
        // RealityKit defaults. Opening up the aperture, slowing the shutter and bumping
        // the ISO produces a brighter, more predictable baseline that matches the
        // RealityKit look out of the box. AR mirrors these values via
        // `ARDefaultCameraNode.DEFAULT_APERTURE/SHUTTER_SPEED/ISO`; the pin lives in
        // `SceneFactoriesTest.defaultExposureMatchesAR()`.
        setExposure(DEFAULT_APERTURE, DEFAULT_SHUTTER_SPEED, DEFAULT_ISO)
    }

    companion object {
        /** Aperture (f-stop). AR mirrors via `ARDefaultCameraNode.DEFAULT_APERTURE`. */
        const val DEFAULT_APERTURE = 12.0f

        /** Shutter speed (seconds). AR mirrors via `ARDefaultCameraNode.DEFAULT_SHUTTER_SPEED`. */
        const val DEFAULT_SHUTTER_SPEED = 1.0f / 200.0f

        /** ISO sensitivity. AR mirrors via `ARDefaultCameraNode.DEFAULT_ISO`. */
        const val DEFAULT_ISO = 200.0f
    }
}

class DefaultLightNode(engine: Engine) : LightNode(
    engine = engine,
    type = LightManager.Type.DIRECTIONAL,
    apply = {
        color(DEFAULT_MAIN_LIGHT_COLOR)
        intensity(DEFAULT_MAIN_LIGHT_COLOR_INTENSITY)
        direction(0.0f, -1.0f, 0.0f)
        castShadows(true)
    }
)

/**
 * Default secondary "fill" directional light.
 *
 * Direction is offset from the main light so the unlit side of objects gets a soft kick,
 * matching the RealityKit-style key+fill look. Does not cast shadows (only the main light
 * contributes shadows by default).
 */
class DefaultFillLightNode(engine: Engine) : LightNode(
    engine = engine,
    type = LightManager.Type.DIRECTIONAL,
    apply = {
        color(DEFAULT_FILL_LIGHT_COLOR)
        intensity(DEFAULT_FILL_LIGHT_COLOR_INTENSITY)
        // Offset direction: lights the side opposite to the main light from a slightly
        // higher angle. The main light points straight down (0, -1, 0); this fill comes
        // from upper-back-left to lift shadow-side faces without flattening the model.
        direction(0.5f, -0.5f, 0.5f)
        castShadows(false)
    }
)
