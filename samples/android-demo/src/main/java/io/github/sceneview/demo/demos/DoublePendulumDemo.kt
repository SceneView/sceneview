package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.filament.LightManager
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.R
import io.github.sceneview.demo.rememberFirstFrameState
import io.github.sceneview.environment.rememberHDREnvironment
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode as CubeNodeImpl
import io.github.sceneview.physics.DoublePendulum
import io.github.sceneview.physics.DoublePendulumLink
import io.github.sceneview.physics.DoublePendulumState
import io.github.sceneview.physics.HALF_PI
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.sample.rememberMaterialInstance
import kotlin.math.atan2

/**
 * Real-time **double-pendulum** physics — a chaotic two-link mechanism whose
 * motion is driven entirely by the shared, platform-independent
 * [DoublePendulum] simulation in `sceneview-core`.
 *
 * This is the Android face of the cross-platform demo from
 * [#1221](https://github.com/sceneview/sceneview/issues/1221), a port of
 * [@radcli14](https://github.com/radcli14)'s MIT-licensed
 * [`twolinks`](https://github.com/radcli14/twolinks). The exact same Kotlin
 * physics class powers the iOS demo (ported by hand into Swift until the
 * `sceneview-core` XCFramework lands — [#1033](https://github.com/sceneview/sceneview/issues/1033)).
 *
 * ## How the simulation drives the render
 *
 * Two thin [CubeNodeImpl] links are created once at unit-ish dimensions. A
 * [withFrameNanos] loop advances [DoublePendulum.step] every frame and mutates
 * each cube's `position` / `rotation` / `scale` from the simulation's joint
 * positions — no per-frame mesh regeneration, just transform updates (Eliott's
 * "unit-cube scaled per frame" trick). A small sphere-like cube marks the fixed
 * pivot.
 *
 * The pendulum swings in the XY plane; the camera looks straight down -Z so the
 * full mechanism is framed. A studio HDR provides IBL so the metallic links
 * catch highlights as they swing.
 */
@Composable
fun DoublePendulumDemo(onBack: () -> Unit) {
    // --- Tunable simulation parameters (exposed as sliders) ---
    var length1 by remember { mutableFloatStateOf(0.45f) }
    var length2 by remember { mutableFloatStateOf(0.40f) }
    var gravity by remember { mutableFloatStateOf(9.8f) }

    // Generation key — bumping it re-seeds the simulation (Reset button).
    var generation by remember { mutableStateOf(0) }

    // The mutable simulation state. Re-seeded whenever a slider or Reset
    // changes the parameters: the pendulum starts raised to horizontal so
    // the first frame already shows dramatic motion.
    var state by remember(length1, length2, gravity, generation) {
        mutableStateOf(
            DoublePendulumState(
                link1 = DoublePendulumLink(length = length1, mass = 1f, angle = HALF_PI),
                link2 = DoublePendulumLink(length = length2, mass = 1f, angle = HALF_PI * 0.6f),
                pivot = Position(0f, 0.45f, 0f),
                gravity = gravity,
                damping = 0.04f,
            )
        )
    }

    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)

    // Studio HDR for IBL so the metallic links catch moving highlights.
    val hdrEnvironment = rememberHDREnvironment(
        environmentLoader,
        "environments/studio_2k.hdr",
        createSkybox = false,
    )
    val fallbackEnvironment = rememberEnvironment(environmentLoader)
    val activeEnvironment = hdrEnvironment ?: fallbackEnvironment

    // Camera looks at the pivot down the -Z axis — the pendulum swings in XY.
    val cameraNode = rememberCameraNode(engine) {
        position = Position(0f, 0f, 2.2f)
        lookAt(Position(0f, 0f, 0f))
    }

    val firstFrame = rememberFirstFrameState()

    DemoScaffold(
        title = stringResource(R.string.demo_double_pendulum_title),
        onBack = onBack,
        firstFrameRendered = firstFrame.rendered,
        controls = {
            Text(
                "Upper link: ${"%.2f".format(length1)} m",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(value = length1, onValueChange = { length1 = it }, valueRange = 0.2f..0.6f)

            Text(
                "Lower link: ${"%.2f".format(length2)} m",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(value = length2, onValueChange = { length2 = it }, valueRange = 0.2f..0.6f)

            Text(
                "Gravity: ${"%.1f".format(gravity)} m/s²",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(value = gravity, onValueChange = { gravity = it }, valueRange = 1.6f..20f)

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { generation++ }) {
                    Text("Reset & drop")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "A chaotic two-link pendulum. The physics runs in sceneview-core " +
                    "(shared KMP) — the same simulation drives the iOS demo. " +
                    "Adapted from @radcli14's twolinks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            onFrame = firstFrame.onFrame,
            engine = engine,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            environment = activeEnvironment,
            cameraNode = cameraNode,
            cameraManipulator = rememberCameraManipulator(
                orbitHomePosition = cameraNode.worldPosition,
            ),
        ) {
            // Soft fill light so the links read even when the IBL is dim.
            LightNode(
                type = LightManager.Type.DIRECTIONAL,
                direction = Direction(-0.3f, -0.6f, -0.8f),
                apply = { intensity(8_000f) },
            )

            // Metallic PBR material shared by both links.
            val linkMaterial = rememberMaterialInstance(
                materialLoader,
                Color(0xFF9AA7B4),
                metallic = 0.85f,
                roughness = 0.25f,
            )
            // Brass-toned pivot marker.
            val pivotMaterial = rememberMaterialInstance(
                materialLoader,
                Color(0xFFD7A24B),
                metallic = 0.9f,
                roughness = 0.2f,
            )

            // Each link is a thin unit-ish box; the frame loop below mutates
            // its transform every frame. Initial size: 1 m tall so a Y-scale
            // equal to the link length gives the correct rendered length.
            var link1Ref by remember { mutableStateOf<CubeNodeImpl?>(null) }
            var link2Ref by remember { mutableStateOf<CubeNodeImpl?>(null) }

            CubeNode(
                size = Size(x = 0.045f, y = 1f, z = 0.045f),
                materialInstance = linkMaterial,
                apply = { link1Ref = this },
            )
            CubeNode(
                size = Size(x = 0.04f, y = 1f, z = 0.04f),
                materialInstance = linkMaterial,
                apply = { link2Ref = this },
            )
            // Fixed pivot marker — a small box at the hinge.
            CubeNode(
                size = Size(x = 0.07f, y = 0.07f, z = 0.07f),
                materialInstance = pivotMaterial,
                position = state.pivot,
            )

            // Per-frame physics loop. Keyed on the node refs + the generation
            // so a Reset (or slider change re-seeding `state`) restarts it.
            LaunchedEffect(link1Ref, link2Ref, generation) {
                val n1 = link1Ref ?: return@LaunchedEffect
                val n2 = link2Ref ?: return@LaunchedEffect
                var lastNanos = withFrameNanos { it }
                while (true) {
                    val now = withFrameNanos { it }
                    val dt = ((now - lastNanos) / 1_000_000_000.0).toFloat()
                    lastNanos = now

                    state = DoublePendulum.step(state, dt)

                    applyLinkTransform(n1, state.pivot, state.joint)
                    applyLinkTransform(n2, state.joint, state.tip)
                }
            }
        }
    }
}

/**
 * Position, orient and stretch a unit-tall link box so its two ends sit at
 * [from] and [to] in world space.
 *
 * The box mesh is 1 m tall along local +Y; we scale Y by the segment length,
 * place the box centre at the segment midpoint, and rotate about Z so local
 * +Y points from [from] toward [to]. (The pendulum swings in the XY plane, so
 * Z rotation alone fully orients the link.)
 */
private fun applyLinkTransform(node: CubeNodeImpl, from: Position, to: Position) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = kotlin.math.sqrt(dx * dx + dy * dy)
    // Angle of the segment from the +Y axis (Filament Z-rotation, degrees).
    // atan2(dx, dy) gives 0° when the link points straight up (+Y); negate so
    // a link hanging down-and-to-the-right rotates the expected way.
    val angleDeg = Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())).toFloat()
    node.position = Position(
        x = (from.x + to.x) * 0.5f,
        y = (from.y + to.y) * 0.5f,
        z = from.z,
    )
    node.rotation = Rotation(x = 0f, y = 0f, z = -angleDeg)
    node.scale = Scale(x = 1f, y = length.coerceAtLeast(0.001f), z = 1f)
}
