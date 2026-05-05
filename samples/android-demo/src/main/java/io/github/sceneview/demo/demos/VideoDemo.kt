package io.github.sceneview.demo.demos

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.lookAt
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.DemoSettings
import io.github.sceneview.gesture.CameraGestureDetector
import io.github.sceneview.math.Position
import io.github.sceneview.math.Transform
import io.github.sceneview.environment.rememberHDREnvironment
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Demonstrates [VideoNode] — a flat quad inside a 3D scene that plays a streaming video
 * with audio.
 *
 * This demo offers three creative twists on top of the basic VideoNode:
 *
 *  - **Surface mode picker** — switch between a flat plane (default), a chrome
 *    sculpture-cube behind the screen for stylised reflections, and a polished
 *    reflective floor below the screen so the video bounces off the ground.
 *  - **Cinematic camera** — toggle off the slow orbit and the camera replays a
 *    keyframed sequence (wide → close-up → side angle → wide) with eased Spring
 *    transitions, like a music-video edit.
 *  - **Mute toggle** — start muted by default (auto-play with sound is rude on
 *    mobile); tap the speaker icon to unmute.
 *
 * Source: Big Buck Bunny — © Blender Foundation, CC-BY 3.0.
 */
@Composable
fun VideoDemo(onBack: () -> Unit) {
    var isPlaying by remember { mutableStateOf(true) }
    var isReady by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(true) }
    var surfaceMode by remember { mutableStateOf(SurfaceMode.PLANE) }
    var cinematic by remember { mutableStateOf(true) }

    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)

    // HDR environment for IBL — the studio_warm probe gives metallic surfaces a
    // believable reflection (warm key + cool fill); skybox stays off so the
    // video reads against neutral black.
    val hdrEnvironment = rememberHDREnvironment(
        environmentLoader,
        "environments/studio_warm_2k.hdr",
        createSkybox = false,
    )
    val fallbackEnvironment = rememberEnvironment(environmentLoader)
    val activeEnvironment = hdrEnvironment ?: fallbackEnvironment

    // Manual MediaPlayer — `rememberMediaPlayer` only accepts asset paths, but we want
    // streaming so the APK stays slim. `prepareAsync` keeps the main thread responsive
    // while the network buffer fills; the quad shows black until `setOnPreparedListener`
    // fires.
    val player = remember {
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            isLooping = true
            // Start muted — auto-play with sound surprises users on mobile.
            setVolume(0f, 0f)
            setDataSource(
                // Big Buck Bunny by Blender Foundation (CC-BY 3.0). The previously-used
                // commondatastorage.googleapis.com mirror started returning 403 in 2026,
                // so we point at the W3Schools mirror — same content, ~788 kB MP4 with
                // audio, well-known and stable.
                "https://www.w3schools.com/html/mov_bbb.mp4"
            )
            setOnPreparedListener {
                isReady = true
                start()
            }
            prepareAsync()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    // Sync mute state to player — re-applied whenever isMuted toggles.
    LaunchedEffect(isMuted, isReady) {
        if (isReady) {
            val v = if (isMuted) 0f else 1f
            player.setVolume(v, v)
        }
    }

    // Cinematic camera: animates the camera between named pose keyframes
    // (wide / close-up / side / top) with eased transitions. When `cinematic`
    // is off, falls back to the existing slow-orbit behaviour.
    val cinematicManipulator = rememberCinematicCameraManipulator(
        trigger = isReady,
        cinematic = cinematic,
    )

    DemoScaffold(
        title = "Video",
        onBack = onBack,
        controls = {
            Text("Playback", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(onClick = {
                    isPlaying = !isPlaying
                    if (isReady) {
                        if (isPlaying) player.start() else player.pause()
                    }
                }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }
                IconButton(onClick = { isMuted = !isMuted }) {
                    Icon(
                        if (isMuted) Icons.AutoMirrored.Filled.VolumeOff
                        else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Unmute" else "Mute"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Surface", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SurfaceMode.values().forEach { mode ->
                    FilterChip(
                        selected = surfaceMode == mode,
                        onClick = { surfaceMode = mode },
                        label = { Text(mode.label) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Camera", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = cinematic,
                    onClick = { cinematic = true },
                    label = { Text("Cinematic") },
                )
                FilterChip(
                    selected = !cinematic,
                    onClick = { cinematic = false },
                    label = { Text("Orbit") },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Big Buck Bunny — © Blender Foundation (CC-BY 3.0)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            environment = activeEnvironment,
            cameraManipulator = cinematicManipulator,
        ) {
            // VideoNode auto-sizes the quad to the streaming video's aspect ratio.
            // The first frames render black until prepareAsync completes.
            VideoNode(player = player)

            // Decorative geometry that complements the video plane. We don't put the
            // video on the cube/sphere directly — only one VideoNode can be bound to
            // a MediaPlayer's SurfaceTexture at a time — instead we surround the
            // screen with chrome shapes that pick up the IBL reflections and the
            // warm tones from the studio_warm probe, giving each surface mode a
            // distinct vibe.
            when (surfaceMode) {
                SurfaceMode.PLANE -> {
                    // No companion geometry — the original "video on a plane" demo.
                }

                SurfaceMode.CUBE -> {
                    // Chrome sculpture sitting *behind* the screen. As the
                    // cinematic camera sweeps around, the cube bounces the
                    // environment HDR back at the viewer with subtle warm
                    // highlights that frame the video.
                    val chromeMaterial = remember(materialLoader) {
                        materialLoader.createColorInstance(
                            color = androidx.compose.ui.graphics.Color(0.85f, 0.85f, 0.88f, 1f),
                            metallic = 1f,
                            roughness = 0.18f,
                            reflectance = 0.7f,
                        )
                    }
                    CubeNode(
                        size = io.github.sceneview.math.Size(0.6f, 0.6f, 0.6f),
                        materialInstance = chromeMaterial,
                        position = Position(x = 0f, y = -0.05f, z = -0.6f),
                        rotation = io.github.sceneview.math.Rotation(x = 0f, y = 35f, z = 15f),
                    )
                }

                SurfaceMode.REFLECTIVE_FLOOR -> {
                    // Polished black floor below the screen — high metallic +
                    // very low roughness gives a near-mirror reflection of the
                    // video and the studio_warm IBL.
                    val floorMaterial = remember(materialLoader) {
                        materialLoader.createColorInstance(
                            color = androidx.compose.ui.graphics.Color(0.05f, 0.05f, 0.06f, 1f),
                            metallic = 1f,
                            roughness = 0.08f,
                            reflectance = 0.9f,
                        )
                    }
                    PlaneNode(
                        size = io.github.sceneview.math.Size(4f, 0.001f, 4f),
                        materialInstance = floorMaterial,
                        position = Position(x = 0f, y = -0.45f, z = 0f),
                    )
                }
            }
        }
    }
}

/** Surface variations the user can switch between. */
private enum class SurfaceMode(val label: String) {
    PLANE("Plane"),
    CUBE("Cube"),
    REFLECTIVE_FLOOR("Reflective"),
}

/**
 * Camera pose keyframe — `eye` is the camera position, `target` is the look-at
 * point. The cinematic sequence interpolates linearly between these in world
 * space using a smooth easing curve, which produces a natural dolly motion
 * (much closer to a real camera op than orbit-only).
 */
private data class CameraPose(val eye: Position, val target: Position)

private val CINEMATIC_POSES = listOf(
    // Wide hero shot — directly in front, slight up-tilt.
    CameraPose(eye = Position(0f, 0.3f, 2.4f), target = Position(0f, 0f, 0f)),
    // Push in: close-up that fills the screen with the video.
    CameraPose(eye = Position(0f, 0.05f, 1.1f), target = Position(0f, 0f, 0f)),
    // Side angle — sliding camera revealing the scene depth.
    CameraPose(eye = Position(1.6f, 0.2f, 1.2f), target = Position(0f, 0f, 0f)),
    // Top-down 3/4 — a brief overhead, then we loop back to wide.
    CameraPose(eye = Position(-0.6f, 1.2f, 1.6f), target = Position(0f, 0f, 0f)),
)

/**
 * Hold each pose for ~3.5 s and crossfade for ~2 s — full loop = ~22 s,
 * same ballpark as a music-video cut. Holds are achieved by using the same
 * pose on both sides of a tween (so `animateTo` has nothing to interpolate
 * for that segment).
 */
private const val POSE_HOLD_MS = 3_500
private const val POSE_TRANSITION_MS = 2_000

/**
 * A [CameraGestureDetector.CameraManipulator] that plays a keyframed cinematic
 * sequence when [cinematicProvider] returns `true`, and falls back to a slow
 * idle orbit otherwise. The first user gesture hands control off to a stock
 * [CameraGestureDetector.DefaultCameraManipulator] anchored at the current
 * pose — same hand-off semantics as `HeroOrbitCameraManipulator`.
 */
private class CinematicCameraManipulator(
    private val eyeProvider: () -> Position,
    private val targetProvider: () -> Position,
) : CameraGestureDetector.CameraManipulator {

    private var fallback: CameraGestureDetector.DefaultCameraManipulator? = null
    private var viewportW = 1
    private var viewportH = 1

    private fun cinematicTransform(): Transform {
        val eye = eyeProvider()
        val target = targetProvider()
        val mat = lookAt(eye = eye, target = target, up = Float3(0f, 1f, 0f))
        return Transform(mat)
    }

    private fun ensureFallback() {
        if (fallback == null) {
            fallback = CameraGestureDetector.DefaultCameraManipulator(
                orbitHomePosition = eyeProvider(),
                targetPosition = targetProvider(),
            ).also { it.setViewport(viewportW, viewportH) }
        }
    }

    override fun setViewport(width: Int, height: Int) {
        viewportW = width.coerceAtLeast(1)
        viewportH = height.coerceAtLeast(1)
        fallback?.setViewport(viewportW, viewportH)
    }

    override fun getTransform(): Transform =
        fallback?.getTransform() ?: cinematicTransform()

    override fun grabBegin(x: Int, y: Int, strafe: Boolean) {
        ensureFallback(); fallback?.grabBegin(x, y, strafe)
    }

    override fun grabUpdate(x: Int, y: Int) {
        fallback?.grabUpdate(x, y)
    }

    override fun grabEnd() {
        fallback?.grabEnd()
    }

    override fun scrollBegin(x: Int, y: Int, separation: Float) {
        ensureFallback(); fallback?.scrollBegin(x, y, separation)
    }

    override fun scrollUpdate(x: Int, y: Int, prevSeparation: Float, currSeparation: Float) {
        fallback?.scrollUpdate(x, y, prevSeparation, currSeparation)
    }

    override fun scrollEnd() {
        fallback?.scrollEnd()
    }

    override fun update(deltaTime: Float) {
        fallback?.update(deltaTime)
    }
}

/**
 * Drive cinematic camera animation with three independent [Animatable]s for the
 * eye position (x, y, z) so changes in `cinematic` mid-loop interrupt cleanly
 * without snap. When [cinematic] is false we fall back to a continuous orbit.
 */
@Composable
private fun rememberCinematicCameraManipulator(
    trigger: Boolean,
    cinematic: Boolean,
): CinematicCameraManipulator {
    val eyeX = remember { Animatable(CINEMATIC_POSES[0].eye.x) }
    val eyeY = remember { Animatable(CINEMATIC_POSES[0].eye.y) }
    val eyeZ = remember { Animatable(CINEMATIC_POSES[0].eye.z) }
    // Orbit yaw is its own Animatable so the orbit mode picks up where the
    // last cinematic frame left off (or vice-versa) without snap.
    val orbitYaw = remember { Animatable(0f) }
    // Capture the latest `cinematic` flag in a state holder so the providers
    // (created once inside `remember`) read the up-to-date value on every
    // frame instead of the value at first composition.
    val cinematicRef = remember { androidx.compose.runtime.mutableStateOf(cinematic) }
    cinematicRef.value = cinematic

    LaunchedEffect(trigger, cinematic, DemoSettings.qaMode) {
        if (!trigger || DemoSettings.qaMode) return@LaunchedEffect
        if (cinematic) {
            // Loop through the keyframe sequence forever, tweening each
            // segment with FastOutSlowInEasing for that "camera-op" feel.
            var i = 0
            while (true) {
                val pose = CINEMATIC_POSES[i % CINEMATIC_POSES.size]
                // Hold the current pose briefly before transitioning.
                kotlinx.coroutines.delay(POSE_HOLD_MS.toLong())
                val next = CINEMATIC_POSES[(i + 1) % CINEMATIC_POSES.size]
                val spec = tween<Float>(
                    durationMillis = POSE_TRANSITION_MS,
                    easing = FastOutSlowInEasing,
                )
                kotlinx.coroutines.coroutineScope {
                    launch { eyeX.animateTo(next.eye.x, spec) }
                    launch { eyeY.animateTo(next.eye.y, spec) }
                    launch { eyeZ.animateTo(next.eye.z, spec) }
                }
                i++
            }
        } else {
            // Continuous slow orbit at radius 2.2, height 0.3.
            // Loop yaw 0..360 in 18 s like the original orbit demo.
            while (true) {
                orbitYaw.snapTo(0f)
                orbitYaw.animateTo(
                    targetValue = 360f,
                    animationSpec = tween(
                        durationMillis = 18_000,
                        easing = androidx.compose.animation.core.LinearEasing,
                    ),
                )
            }
        }
    }

    return remember {
        CinematicCameraManipulator(
            eyeProvider = {
                if (cinematicRef.value || DemoSettings.qaMode) {
                    Position(eyeX.value, eyeY.value, eyeZ.value)
                } else {
                    val rad = Math.toRadians(orbitYaw.value.toDouble()).toFloat()
                    Position(sin(rad) * 2.2f, 0.3f, cos(rad) * 2.2f)
                }
            },
            targetProvider = { Position(0f, 0f, 0f) },
        )
    }
}
