package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

/**
 * Demonstrates rotating the displayed model to mimic four named camera angles.
 *
 * The chip row picks an orientation (Top / Side / Front / Corner) and the helmet rotates
 * to face the camera from that angle — visually equivalent to moving the camera, without
 * the cost of a second SceneView render or fighting with the [CameraManipulator] orbit
 * controller for transform authority.
 */
@Composable
fun SecondaryCameraDemo(onBack: () -> Unit) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)

    val modelInstance = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")

    var cameraPreset by remember { mutableStateOf(CameraPreset.FRONT) }

    DemoScaffold(
        title = "Secondary Camera (PiP)",
        onBack = onBack,
        controls = {
            Text("Camera Angle", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CameraPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = cameraPreset == preset,
                        onClick = { cameraPreset = preset },
                        label = { Text(preset.label) }
                    )
                }
            }
        }
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            cameraManipulator = rememberCameraManipulator()
        ) {
            modelInstance?.let { instance ->
                ModelNode(
                    modelInstance = instance,
                    scaleToUnits = 0.5f,
                    centerOrigin = Position(0f, 0f, 0f),
                    rotation = cameraPreset.rotation
                )
            }
        }
    }
}

private enum class CameraPreset(val label: String, val rotation: Rotation) {
    TOP("Top", Rotation(x = 90f)),
    SIDE("Side", Rotation(y = 90f)),
    FRONT("Front", Rotation()),
    CORNER("Corner", Rotation(x = 30f, y = 45f))
}
