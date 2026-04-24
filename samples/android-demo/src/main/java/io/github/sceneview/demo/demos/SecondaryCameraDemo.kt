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
import io.github.sceneview.gesture.CameraGestureDetector
import io.github.sceneview.math.Position
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

/**
 * Demonstrates snapping the CAMERA to four named viewing angles around a fixed helmet.
 *
 * The chip row picks a preset (Top / Side / Front / Corner); each preset parks the
 * `CameraManipulator` at a different orbit home so lighting, reflections and shadows
 * stay stable on the model while the viewer's vantage point changes. This is the
 * correct "camera preset" interpretation — the previous implementation rotated the
 * helmet itself, which contradicted the demo's name and left IBL reflections static
 * regardless of the selected "angle".
 */
@Composable
fun SecondaryCameraDemo(onBack: () -> Unit) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)

    val modelInstance = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")

    var cameraPreset by remember { mutableStateOf(CameraPreset.FRONT) }
    val target = remember { Position(0f, 0f, 0f) }

    DemoScaffold(
        title = "Camera Presets",
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
        // key(cameraPreset) forces a fresh rememberCameraManipulator each time the
        // user picks a chip — the creator lambda captures the preset's orbit-home
        // position, so the camera snaps to that vantage point. Without the key the
        // manipulator would ignore `orbitHomePosition` changes (it's only read on
        // build) and the preset toggle would have no effect.
        androidx.compose.runtime.key(cameraPreset) {
            val manipulator = androidx.compose.runtime.remember {
                CameraGestureDetector.DefaultCameraManipulator(
                    orbitHomePosition = cameraPreset.eye,
                    targetPosition = target,
                )
            }
            SceneView(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                materialLoader = materialLoader,
                environmentLoader = environmentLoader,
                cameraManipulator = manipulator,
            ) {
                modelInstance?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        scaleToUnits = 0.5f,
                        centerOrigin = Position(0f, 0f, 0f),
                    )
                }
            }
        }
    }
}

private enum class CameraPreset(val label: String, val eye: Position) {
    TOP("Top", Position(0f, 1.8f, 0.01f)),
    SIDE("Side", Position(1.8f, 0.2f, 0f)),
    FRONT("Front", Position(0f, 0.2f, 1.8f)),
    CORNER("Corner", Position(1.3f, 0.9f, 1.3f)),
}
