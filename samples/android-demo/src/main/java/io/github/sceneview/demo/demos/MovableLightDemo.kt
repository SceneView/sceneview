package io.github.sceneview.demo.demos

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.google.android.filament.LightManager
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.R
import io.github.sceneview.demo.LoadingScrim
import io.github.sceneview.demo.rememberFirstFrameState
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.colorOf
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.sample.rememberUnlitMaterialInstance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Drag-to-orbit-the-light demo.
 *
 * The user drags anywhere on the scene to move a single point light in an
 * orbit (azimuth / elevation, fixed radius) around a PBR helmet model. The
 * specular highlights track the light in real time, which is the whole point —
 * a Filament/PBR equivalent of "wave a torch in front of a metallic helmet".
 *
 * Design choices:
 * - **Camera is fixed** (`cameraManipulator = null`) so the user's drag is
 *   unambiguously interpreted as "move the light" and never as "rotate the
 *   model". The pointerInput sits on the `Modifier.fillMaxSize()` Box around
 *   the SceneView and consumes drag events upstream of the SceneView's own
 *   gesture detector.
 * - **PBR Damaged Helmet** — same glb shipped with [LightingDemo]. Has crisp
 *   metallic edges and matte fabric, both of which respond visibly to a
 *   moving light source.
 * - **Yellow unlit marker** — a small sphere with `createUnlitColorInstance`
 *   so it always reads as a glow regardless of where the actual light is. Its
 *   orbit radius is kept tight enough that it stays inside the (fixed) camera
 *   frustum for the whole azimuth/elevation sweep — see `orbitRadius` (#1467).
 * - **Default `mainLightNode` is kept disabled** (set to `null`) so the only
 *   light affecting the helmet is the user-controlled one — otherwise the
 *   helmet stays bright and the drag effect is invisible.
 */
@Composable
fun MovableLightDemo(onBack: () -> Unit) {
    // Spherical-orbit state. Start with the light up-and-to-the-right of the
    // helmet so the user sees a strong highlight on first paint, not a flat
    // helmet they have to discover by dragging.
    var azimuth by remember { mutableFloatStateOf((PI / 4).toFloat()) }      // 45°
    var elevation by remember { mutableFloatStateOf((PI / 6).toFloat()) }    // 30°
    var intensity by remember { mutableFloatStateOf(30_000f) }
    var showLightSource by remember { mutableStateOf(true) }

    // Fixed orbit radius. Kept deliberately small (#1467): the helmet is fit to
    // a 0.6 m bounding cube (≈ 0.35 m diagonal half-extent) and the default
    // camera (`DefaultCameraNode`) sits at z = 2.75 m with a 28 mm lens — its
    // frame was tuned in #1427 to show a 0.6 m origin-placed model with only
    // modest headroom. The old 1.5 m orbit swung the yellow handle far outside
    // the camera frustum for most of the azimuth/elevation sweep, so the user
    // was dragging an invisible target. 0.75 m clears the helmet (no clipping
    // into the model) yet keeps the handle on-screen for the whole orbit, while
    // the moving highlight on the metal stays sharp.
    val orbitRadius = 0.75f
    // Clamp elevation to ±50° so the handle never climbs/drops out of the camera
    // frustum vertically (#1467). At 0.75 m orbit, sin(50°)·0.75 ≈ 0.57 m, well
    // within the vertical FOV. This also keeps the light off the gimbal poles.
    val minElevation = -((PI / 180) * 50).toFloat()
    val maxElevation = ((PI / 180) * 50).toFloat()
    // Drag sensitivity in radians-per-pixel. 0.005 rad/px gives ≈ a half-turn
    // per full screen-width swipe on a typical 1080p phone — feels responsive
    // without being twitchy.
    val sensitivity = 0.005f

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val modelInstance = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")

    // Compute Cartesian position from spherical. We do this in the composition
    // so reading `azimuth`/`elevation` triggers a recomposition → the LightNode
    // SideEffect re-pushes the new `position` on the Filament side.
    val lightPos = remember(azimuth, elevation) {
        val cosE = cos(elevation)
        Position(
            x = orbitRadius * cosE * sin(azimuth),
            y = orbitRadius * sin(elevation),
            z = orbitRadius * cosE * cos(azimuth),
        )
    }
    // Direction from light → origin (the helmet centre). Spot/directional
    // ignore this for our chosen Point type but we keep it in case the user
    // experiments — defensive parity with LightingDemo.
    val lightDir = remember(lightPos) {
        Direction(-lightPos.x, -lightPos.y, -lightPos.z)
    }

    // Yellow unlit material for the marker sphere — see iOS rationale. Unlit
    // = always glowing, regardless of the user-light's position. Helper has
    // a DisposableEffect-backed onDispose → no JNI MaterialInstance leak on
    // recompose / navigate-away (#979).
    val markerMaterial = rememberUnlitMaterialInstance(materialLoader, Color(0xFFFFEB3B))

    val firstFrame = rememberFirstFrameState()

    DemoScaffold(
        title = stringResource(R.string.demo_movable_light_title),
        onBack = onBack,
        firstFrameRendered = firstFrame.rendered,
        controls = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.WbSunny, contentDescription = null)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Intensity: ${intensity.toInt()}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Slider(
                value = intensity,
                onValueChange = { intensity = it },
                valueRange = 1_000f..100_000f
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Show light source",
                    style = MaterialTheme.typography.labelLarge
                )
                Switch(
                    checked = showLightSource,
                    onCheckedChange = { showLightSource = it }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Drag anywhere on the scene to orbit the light around the helmet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Capture drag input *before* SceneView's gesture detector
                // sees it. Because `cameraManipulator = null` below disables
                // the camera pinch/drag detector anyway, this is the only
                // consumer of drag events and the gesture stays smooth even
                // on small fast motions.
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        azimuth += dragAmount.x * sensitivity
                        // Screen Y grows downwards → invert so "drag down" =
                        // "light goes down" (matches iOS behaviour).
                        elevation -= dragAmount.y * sensitivity
                        elevation = min(max(elevation, minElevation), maxElevation)
                    }
                }
        ) {
            SceneView(
                modifier = Modifier.fillMaxSize(),
                onFrame = firstFrame.onFrame,
                engine = engine,
                modelLoader = modelLoader,
                environmentLoader = environmentLoader,
                // No camera gestures — fixes the camera so the user's drag is
                // unambiguously interpreted as "move the light".
                cameraManipulator = null,
                // Disable the default 110 klx main directional light so the
                // ONLY light shaping the helmet is the user-controlled one.
                // The IBL from environmentLoader stays on for a tiny bit of
                // ambient fill so the helmet is never pitch-black even if the
                // user drags the light behind it.
                mainLightNode = null,
            ) {
                modelInstance?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        scaleToUnits = 0.6f,
                    )
                }

                // User-controlled point light orbiting the helmet at origin.
                LightNode(
                    type = LightManager.Type.POINT,
                    intensity = intensity,
                    direction = lightDir,
                    position = lightPos,
                    color = colorOf(r = 1.0f, g = 0.95f, b = 0.8f),
                    apply = {
                        // 4 m attenuation radius — beyond this the light has
                        // no effect. Comfortably larger than orbitRadius (0.75 m)
                        // so the light always reaches the helmet.
                        falloff(4f)
                    },
                )

                // Yellow marker sphere — the draggable light handle. Only
                // rendered when the toggle is on. Radius 0.09 m (#1467): the
                // old 0.05 m disc was a barely-visible speck at the camera's
                // ≈ 2 m working distance; 0.09 m reads as a clear glowing
                // handle the user can confidently aim a drag at.
                if (showLightSource) {
                    SphereNode(
                        materialInstance = markerMaterial,
                        radius = 0.09f,
                        position = lightPos,
                    )
                }
            }
            LoadingScrim(loading = modelInstance == null, label = "Loading helmet…")
        }
    }
}
