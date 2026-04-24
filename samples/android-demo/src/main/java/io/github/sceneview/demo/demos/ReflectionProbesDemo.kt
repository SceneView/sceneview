package io.github.sceneview.demo.demos

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.DemoSettings
import io.github.sceneview.demo.LoadingScrim
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberScene
import com.google.android.filament.Scene as FilamentScene

/**
 * Demonstrates [ReflectionProbeNode] — a local IBL override zone.
 *
 * The probe uses a separate HDR environment that overrides the scene's default IBL when the
 * camera is within [radius] metres of [probePosition]. Sliders control the probe radius and
 * its Y-axis position.
 */
@Composable
fun ReflectionProbesDemo(onBack: () -> Unit) {
    var probeRadius by remember { mutableFloatStateOf(3f) }
    var probeY by remember { mutableFloatStateOf(0.5f) }
    var cameraPos by remember { mutableStateOf(Position()) }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val scene: FilamentScene = rememberScene(engine)
    // Hold the camera so we can read its world position every frame. Without this the probe's
    // distance check always compares against Position() (origin) and the probe silently
    // disables itself as soon as the user orbits away from the origin.
    val cameraNode = rememberCameraNode(engine)

    val modelInstance = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")

    // Probe environment — uses the default neutral IBL from the environment loader.
    // In a real app you would load a different HDR for the probe zone.
    val probeEnvironment = rememberEnvironment(environmentLoader)

    val yaw = io.github.sceneview.demo.rememberHeroYaw(
        trigger = modelInstance != null, durationMillis = 20_000, staticYaw = 30f,
    )

    DemoScaffold(
        title = "Reflection Probes",
        onBack = onBack,
        controls = {
            Text(
                "Probe Radius: %.1f m".format(probeRadius),
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = probeRadius,
                onValueChange = { probeRadius = it },
                valueRange = 0f..10f
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Probe Y Position: %.1f".format(probeY),
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = probeY,
                onValueChange = { probeY = it },
                valueRange = -2f..3f
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SceneView(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                environmentLoader = environmentLoader,
                scene = scene,
                cameraNode = cameraNode,
                cameraManipulator = rememberCameraManipulator(),
                onFrame = { _ ->
                    // Push the latest camera world position into compose state so the
                    // ReflectionProbeNode below can enable/disable itself based on
                    // actual distance instead of always comparing against the origin.
                    cameraPos = cameraNode.worldPosition
                },
            ) {
                ReflectionProbeNode(
                    filamentScene = scene,
                    environment = probeEnvironment,
                    position = Position(x = 0f, y = probeY, z = 0f),
                    radius = probeRadius,
                    cameraPosition = cameraPos,
                )

                modelInstance?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        scaleToUnits = 0.5f,
                        position = Position(y = 0f),
                        rotation = Rotation(y = yaw),
                    )
                }
            }
            LoadingScrim(loading = modelInstance == null, label = "Loading helmet…")
        }
    }
}
