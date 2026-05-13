package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import kotlin.math.cos
import kotlin.math.sin

/**
 * Personal-solar-system AR demo — 8 bundled GLB models orbit around the user.
 *
 * On the first tracked AR frame, an [Anchor] is created at world origin (the camera's
 * pose at session-start in ARCore — i.e. wherever the user is standing). Eight model
 * instances are placed as children of an [AnchorNode] in a circle of radius 1.5 m,
 * at angles 360° / 8 = 45° apart. Each model has:
 *
 * - its own **orbital speed** (between 0.05 and 0.30 rad/s) — slow at the outer
 *   "planets", fast at the inner ones, so the formation looks like a solar system
 *   rather than a rigid ring;
 * - its own **local spin** (between 0.5 and 2.0 rad/s on the Y axis);
 * - a **distinct height** between -0.5 m and +0.5 m relative to the user's eye level.
 *
 * The user stays fixed in AR and can turn around to watch each model pass by.
 *
 * Animation is driven by `withFrameNanos` so the orbit advances at the display's
 * refresh rate. Per-recompose state (`orbitSeconds`) is hoisted via `mutableLongStateOf`
 * so the `ModelNode` positions/rotations recompute every frame.
 */
private data class Planet(
    val assetPath: String,
    val scaleToUnits: Float,
    val initialAngleRad: Float,
    val orbitSpeed: Float,   // rad/s around the user
    val spinSpeed: Float,    // rad/s local Y axis
    val height: Float,       // y offset, m
)

// Curated GLB list — only the 8 bundled assets we ship. Two of them are repeated to
// reach 8 planets (with distinct speeds and heights so they read as different motion).
// When more GLBs are bundled, swap in the variety here.
private val ORBITAL_PLANETS = listOf(
    Planet("models/khronos_damaged_helmet.glb", 0.20f, 0f,                 0.08f, 0.7f,  0.0f),
    Planet("models/khronos_fox.glb",            0.25f, Math.PI.toFloat() / 4 * 2, 0.20f, 1.6f, -0.2f),
    Planet("models/khronos_lantern.glb",        0.20f, Math.PI.toFloat() / 4 * 3, 0.06f, 0.9f,  0.4f),
    Planet("models/animated_dragon.glb",        0.25f, Math.PI.toFloat() / 4 * 4, 0.15f, 1.2f, -0.4f),
    Planet("models/khronos_toy_car.glb",        0.20f, Math.PI.toFloat() / 4 * 5, 0.10f, 2.0f,  0.2f),
    Planet("models/shiba.glb",                  0.20f, Math.PI.toFloat() / 4 * 6, 0.05f, 0.6f, -0.5f),
    Planet("models/threejs_soldier.glb",        0.40f, Math.PI.toFloat() / 4 * 7, 0.30f, 1.8f,  0.5f),
)

private const val ORBIT_RADIUS = 1.5f

@Composable
fun OrbitalARDemo(onBack: () -> Unit) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)

    // The user's initial-pose anchor. Created lazily on the first tracked frame, since
    // ARCore world origin is undefined until tracking begins. After that, all 8 planets
    // ride this anchor — turning the phone shows them passing by in world space.
    var userAnchor by remember { mutableStateOf<Anchor?>(null) }
    var isTracking by remember { mutableStateOf(false) }

    // Elapsed seconds since the anchor was created, advanced by withFrameNanos. Drives
    // orbit + spin animation. Stored as nanos to avoid float-precision drift over long
    // sessions (a 10 min orbital run would lose ms-resolution stored as plain Float).
    var orbitNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(userAnchor) {
        if (userAnchor == null) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos != 0L) orbitNanos += (nanos - lastNanos)
                lastNanos = nanos
            }
        }
    }
    val orbitSeconds = orbitNanos / 1_000_000_000f

    DemoScaffold(
        title = "Orbital AR",
        onBack = onBack,
        controls = {
            Text(
                text = "8 models orbit around you in a personal solar system. " +
                    "Stand still and turn the phone — each model passes by at " +
                    "a different speed and height.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ARSceneView(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                materialLoader = materialLoader,
                planeRenderer = false,
                // Same -1 EV bias as the other AR demos — ARCore's auto-exposure
                // tends to overshoot indoors on Pixel 9 devices.
                cameraExposure = -1.0f,
                sessionConfiguration = { _: Session, config: Config ->
                    // Plane detection off — the formation lives in world space around the
                    // user, not on a plane. Disabling planes is cheaper and gives a cleaner
                    // visual (no overlay polygons in front of the orbiting models).
                    config.planeFindingMode = Config.PlaneFindingMode.DISABLED
                    config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                },
                onSessionUpdated = { session: Session, frame: Frame ->
                    isTracking = frame.camera.trackingState == TrackingState.TRACKING
                    // Drop the world-origin anchor on the first tracked frame. ARCore's
                    // world origin = the camera's pose at session-start, which is exactly
                    // what we want: the formation sits where the user is standing.
                    if (isTracking && userAnchor == null) {
                        userAnchor = runCatching {
                            session.createAnchor(Pose.IDENTITY)
                        }.getOrNull()
                    }
                }
            ) {
                val anchor = userAnchor
                if (anchor != null) {
                    AnchorNode(anchor = anchor) {
                        ORBITAL_PLANETS.forEachIndexed { index, planet ->
                            val instance = rememberModelInstance(modelLoader, planet.assetPath)
                            if (instance != null) {
                                val orbitAngle = planet.initialAngleRad + planet.orbitSpeed * orbitSeconds
                                val spinDegrees = Math.toDegrees(
                                    (planet.spinSpeed * orbitSeconds).toDouble()
                                ).toFloat() % 360f
                                ModelNode(
                                    modelInstance = instance,
                                    scaleToUnits = planet.scaleToUnits,
                                    centerOrigin = Position(0f, 0f, 0f),
                                    position = Position(
                                        x = cos(orbitAngle) * ORBIT_RADIUS,
                                        y = planet.height,
                                        z = sin(orbitAngle) * ORBIT_RADIUS,
                                    ),
                                    rotation = Rotation(y = spinDegrees),
                                    autoAnimate = true,
                                )
                            }
                        }
                    }
                }
            }

            // Status pill — top-center, mirrors the ARPlacement / ARInstantPlacement style.
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
                color = Color.Black.copy(alpha = 0.7f),
                contentColor = Color.White,
                tonalElevation = 4.dp,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = when {
                        !isTracking -> "Initializing AR — look around to start tracking"
                        userAnchor == null -> "Locking world anchor…"
                        else -> "Turn around — ${ORBITAL_PLANETS.size} models orbiting"
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
