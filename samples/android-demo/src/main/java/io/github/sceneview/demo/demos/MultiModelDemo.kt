package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import kotlin.math.cos
import kotlin.math.sin

/**
 * Loads several glTF models and lays them out so the demo actually showcases what
 * "multi model" means: multiple independent assets, each at its own scale + position +
 * rotation, all reactive to user controls.
 *
 * Controls:
 * - Visibility chips per model (toggle individual nodes off / on)
 * - "Spin scene" toggle — circular auto-rotation around the formation
 * - Spread slider — pull the formation apart / together in real time
 */
@Composable
fun MultiModelDemo(onBack: () -> Unit) {
    var showAvocado by remember { mutableStateOf(true) }
    var showLantern by remember { mutableStateOf(true) }
    var showHelmet by remember { mutableStateOf(true) }
    var showDragon by remember { mutableStateOf(true) }
    var spinScene by remember { mutableStateOf(true) }
    var spread by remember { mutableFloatStateOf(0.7f) }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)

    val avocado = rememberModelInstance(modelLoader, "models/khronos_avocado.glb")
    val lantern = rememberModelInstance(modelLoader, "models/khronos_lantern.glb")
    val helmet = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")
    val dragon = rememberModelInstance(modelLoader, "models/animated_dragon.glb")

    val allLoaded = avocado != null && lantern != null && helmet != null && dragon != null
    // Yaw drives both the parent-scene rotation (when "Spin scene" is on) and the
    // per-model self-spin so each asset shows all sides as it sweeps past the camera.
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
            Spacer(modifier = Modifier.height(12.dp))

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

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Spread: ${"%.1f".format(spread)} m",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = spread,
                onValueChange = { spread = it },
                valueRange = 0.3f..1.4f,
            )
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SceneView(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                environmentLoader = environmentLoader,
                cameraManipulator = rememberCameraManipulator(),
            ) {
                // Place each visible model on a 90° arc, rotated by `sceneYaw` so the
                // whole formation sweeps the camera. Per-model `rotation = Rotation(y =
                // -sceneYaw)` cancels the orbit so each asset still faces the camera as
                // the formation rotates — gives a "carousel" feel without hiding any
                // model's silhouette behind itself.
                val models = listOf(
                    Triple(showAvocado, avocado, ModelSpec("Avocado", 0.5f, 0f)),
                    Triple(showLantern, lantern, ModelSpec("Lantern", 0.5f, 90f)),
                    Triple(showHelmet, helmet, ModelSpec("Helmet", 0.5f, 180f)),
                    Triple(showDragon, dragon, ModelSpec("Dragon", 0.4f, 270f)),
                )
                for ((show, instance, spec) in models) {
                    if (!show || instance == null) continue
                    val angleRad = Math.toRadians((spec.angleDeg + sceneYaw).toDouble())
                    ModelNode(
                        modelInstance = instance,
                        scaleToUnits = spec.scale,
                        centerOrigin = Position(0f, 0.5f, 0f),
                        position = Position(
                            x = sin(angleRad).toFloat() * spread,
                            y = 0f,
                            z = (cos(angleRad).toFloat() * spread) - 1.5f,
                        ),
                        rotation = Rotation(y = -sceneYaw),
                    )
                }
            }
            LoadingScrim(loading = !allLoaded, label = "Loading 4 models…")
        }
    }
}

private data class ModelSpec(val name: String, val scale: Float, val angleDeg: Float)
