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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Personal-solar-system AR demo — 7 bundled GLB models orbit around the user.
 *
 * On the first tracked AR frame, an [Anchor] is created at world origin (the camera's
 * pose at session-start in ARCore — i.e. wherever the user is standing). Seven model
 * instances are placed as children of an [AnchorNode] in a circle of radius 1.5 m,
 * at evenly-spaced angles (360° / 7 ≈ 51.4° apart). Each model has:
 *
 * - its own **orbital speed** (between 0.05 and 0.30 rad/s) — slow at the outer
 *   "planets", fast at the inner ones, so the formation looks like a solar system
 *   rather than a rigid ring;
 * - either a **baked animation** (dragon, soldier — auto-played, with the model
 *   oriented along the orbit tangent so it "flies/walks the orbit") **or** a
 *   **local Y spin** (between 0.6 and 2.0 rad/s) for static GLBs (helmet, lantern,
 *   toy car) so they feel alive without their own rig;
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
    val spinSpeed: Float,    // rad/s local Y axis — ignored when hasBakedAnimation = true
    val height: Float,       // y offset, m
    // True when the GLB has its own baked animation (wing flap, walk cycle, etc.).
    // For these, we skip the local Y spin and instead orient the model along the
    // orbit tangent so it "flies/walks the orbit" naturally — the baked animation
    // does the rest of the movement.
    val hasBakedAnimation: Boolean,
)

// Curated GLB list — only the 7 bundled assets we ship. The two formerly-static
// pets (khronos_fox + shiba) were replaced with a second instance of the animated
// dragon and soldier so every "alive" looking model in the formation actually
// moves. Each duplicate runs at a different height/speed/angle so the user reads
// them as distinct planets rather than clones.
private val ORBITAL_PLANETS = listOf(
    Planet("models/khronos_damaged_helmet.glb", 0.20f, 0f,                                0.08f, 0.7f,  0.0f, hasBakedAnimation = false),
    Planet("models/animated_dragon.glb",        0.30f, 2f * PI.toFloat() / 7f * 1,        0.20f, 0f,   -0.2f, hasBakedAnimation = true),
    Planet("models/khronos_lantern.glb",        0.20f, 2f * PI.toFloat() / 7f * 2,        0.06f, 0.9f,  0.4f, hasBakedAnimation = false),
    Planet("models/animated_dragon.glb",        0.25f, 2f * PI.toFloat() / 7f * 3,        0.15f, 0f,   -0.4f, hasBakedAnimation = true),
    Planet("models/khronos_toy_car.glb",        0.20f, 2f * PI.toFloat() / 7f * 4,        0.10f, 2.0f,  0.2f, hasBakedAnimation = false),
    Planet("models/threejs_soldier.glb",        0.50f, 2f * PI.toFloat() / 7f * 5,        0.05f, 0f,   -0.5f, hasBakedAnimation = true),
    Planet("models/threejs_soldier.glb",        0.40f, 2f * PI.toFloat() / 7f * 6,        0.30f, 0f,    0.5f, hasBakedAnimation = true),
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
                                // Modulo before sin/cos so a long-running session
                                // (~290 h+) doesn't lose Float precision (#978).
                                val orbitAngle =
                                    (planet.initialAngleRad + planet.orbitSpeed * orbitSeconds) %
                                            (2f * PI.toFloat())
                                // Models with a baked animation (dragon, soldier) face the
                                // tangent of the orbit (= direction of motion) instead of
                                // spinning on Y — a flying dragon spinning on itself breaks
                                // the illusion. For position (R·cos θ, h, R·sin θ) on a CCW
                                // orbit, the tangent is (-sin θ, 0, cos θ); for a glTF model
                                // whose forward is -Z, that maps to a Y-rotation of θ + π.
                                val rotationY = if (planet.hasBakedAnimation) {
                                    Math.toDegrees(orbitAngle.toDouble()).toFloat() + 180f
                                } else {
                                    Math.toDegrees(
                                        (planet.spinSpeed * orbitSeconds).toDouble()
                                    ).toFloat() % 360f
                                }
                                ModelNode(
                                    modelInstance = instance,
                                    scaleToUnits = planet.scaleToUnits,
                                    centerOrigin = Position(0f, 0f, 0f),
                                    position = Position(
                                        x = cos(orbitAngle) * ORBIT_RADIUS,
                                        y = planet.height,
                                        z = sin(orbitAngle) * ORBIT_RADIUS,
                                    ),
                                    rotation = Rotation(y = rotationY),
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
