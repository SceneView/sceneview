package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.LoadingScrim
import io.github.sceneview.demo.rememberHeroYaw
import io.github.sceneview.environment.rememberHDREnvironment
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

/**
 * Loads four glTF assets and arranges them as a tabletop living-room display so the
 * demo actually showcases what "multi model" means: multiple independent assets, each
 * at its own scale + position + rotation, all under the same warm dusk lighting.
 *
 * Lighting comes from `studio_warm_2k.hdr` — a soft golden-hour interior wash that
 * makes the four very different materials (avocado skin, lantern brass, helmet metal
 * paint, dragon scales) read distinctly while feeling like one cohesive scene.
 *
 * Controls:
 * - Visibility chips per model (toggle individual nodes off / on)
 * - "Spin scene" toggle — slow circular auto-rotation of the whole formation, lets
 *   the viewer walk around the display without touching the screen
 *
 * The previous "spread slider" was removed: the new fixed layout is hand-tuned for the
 * dusk-lit display (front row at z=-1.3, back row at z=-1.7) so user adjustment would
 * pull pieces out of the lighting sweet spot rather than improve the framing.
 */
@Composable
fun MultiModelDemo(onBack: () -> Unit) {
    var showAvocado by remember { mutableStateOf(true) }
    var showLantern by remember { mutableStateOf(true) }
    var showHelmet by remember { mutableStateOf(true) }
    var showDragon by remember { mutableStateOf(true) }
    var spinScene by remember { mutableStateOf(true) }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)

    val avocado = rememberModelInstance(modelLoader, "models/khronos_avocado.glb")
    val lantern = rememberModelInstance(modelLoader, "models/khronos_lantern.glb")
    val helmet = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")
    val dragon = rememberModelInstance(modelLoader, "models/animated_dragon.glb")

    // Warm dusk HDR — `studio_warm_2k.hdr` gives a golden-hour interior wash that
    // unifies the four very different materials. Skybox enabled so the warm tint is
    // visible behind the display, not just rim-lighting the models on a black void.
    // Falls back to the default neutral environment while the HDR is still loading.
    val hdrEnvironment = rememberHDREnvironment(
        environmentLoader,
        "environments/studio_warm_2k.hdr",
        createSkybox = true,
    )
    val fallbackEnvironment = rememberEnvironment(environmentLoader)
    val activeEnvironment = hdrEnvironment ?: fallbackEnvironment

    val allLoaded = avocado != null && lantern != null && helmet != null && dragon != null
    // Yaw drives the parent-scene rotation when "Spin scene" is on. Slow 30 s sweep
    // so the viewer can take in each face of the display before it cycles round.
    val sceneYaw = rememberHeroYaw(
        trigger = allLoaded && spinScene, durationMillis = 30_000, staticYaw = 0f,
    )

    DemoScaffold(
        title = "Multiple Models",
        onBack = onBack,
        controls = {
            Text("Visibility", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = showAvocado,
                    onClick = { showAvocado = !showAvocado },
                    label = { Text("Avocado") },
                )
                FilterChip(
                    selected = showLantern,
                    onClick = { showLantern = !showLantern },
                    label = { Text("Lantern") },
                )
                FilterChip(
                    selected = showHelmet,
                    onClick = { showHelmet = !showHelmet },
                    label = { Text("Helmet") },
                )
                FilterChip(
                    selected = showDragon,
                    onClick = { showDragon = !showDragon },
                    label = { Text("Dragon") },
                )
            }

            // Spin toggle — wrap the row in toggleable so taps anywhere flip the state
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = spinScene,
                        onValueChange = { spinScene = it },
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Spin scene", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = spinScene, onCheckedChange = null)
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SceneView(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                environmentLoader = environmentLoader,
                environment = activeEnvironment,
                cameraManipulator = rememberCameraManipulator(
                    orbitHomePosition = Position(0f, 0.4f, 0.5f),
                    targetPosition = Position(0f, 0f, -1.5f),
                ),
            ) {
                // Tabletop arrangement: helmet at front-center as the hero, lantern
                // at back-right as the warm light source, dragon at back-left where
                // its tail sweep doesn't intersect the helmet, avocado as a small
                // front-left accent. Front row z=-1.3, back row z=-1.7 so the
                // depth difference reads even on a portrait phone viewport.
                //
                // sceneYaw rotates each model AROUND the formation centre by treating
                // its (x, z) as polar coords offset from (0, -1.5). Per-model rotation
                // cancels the yaw on its own Y so each piece stays facing the camera
                // as the formation sweeps — gives a "turntable display" feel.
                val centerZ = -1.5f
                val displays = listOf(
                    Display(showAvocado, avocado, x = -0.55f, z = -1.3f, scale = 0.4f),
                    Display(showHelmet, helmet, x = 0.0f, z = -1.3f, scale = 0.5f),
                    Display(showDragon, dragon, x = -0.45f, z = -1.7f, scale = 0.4f),
                    Display(showLantern, lantern, x = 0.55f, z = -1.7f, scale = 0.5f),
                )
                val yawRad = Math.toRadians(sceneYaw.toDouble())
                val cosYaw = kotlin.math.cos(yawRad).toFloat()
                val sinYaw = kotlin.math.sin(yawRad).toFloat()
                for (d in displays) {
                    if (!d.show || d.instance == null) continue
                    // Rotate (x, z - centerZ) by sceneYaw around centre, then translate
                    // back. Equivalent to wrapping all models in a parent rotated node.
                    val dx = d.x
                    val dz = d.z - centerZ
                    val rx = dx * cosYaw + dz * sinYaw
                    val rz = -dx * sinYaw + dz * cosYaw
                    ModelNode(
                        modelInstance = d.instance,
                        scaleToUnits = d.scale,
                        centerOrigin = Position(0f, 0.5f, 0f),
                        position = Position(x = rx, y = 0f, z = rz + centerZ),
                        rotation = Rotation(y = -sceneYaw),
                    )
                }
            }
            LoadingScrim(loading = !allLoaded, label = "Loading 4 models…")
        }
    }
}

private data class Display(
    val show: Boolean,
    val instance: io.github.sceneview.model.ModelInstance?,
    val x: Float,
    val z: Float,
    val scale: Float,
)
