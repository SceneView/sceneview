package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.filament.utils.Manipulator
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.gesture.CameraGestureDetector
import io.github.sceneview.gesture.orbitHomePosition
import io.github.sceneview.gesture.targetPosition
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

/**
 * Demonstrates camera manipulation modes: orbit, free-flight, and map-style navigation.
 *
 * Users can toggle between [Manipulator.Mode] presets and reset the camera to its home position.
 */
@Composable
fun CameraControlsDemo(onBack: () -> Unit) {
    val modes = remember {
        listOf(
            "Orbit" to Manipulator.Mode.ORBIT,
            "Free Flight" to Manipulator.Mode.FREE_FLIGHT,
            "Map" to Manipulator.Mode.MAP
        )
    }
    var selectedMode by remember { mutableStateOf(modes[0]) }
    // Incrementing the reset key forces the manipulator to be recreated.
    var resetKey by remember { mutableIntStateOf(0) }

    val homePosition = remember { Position(0.0f, 0.0f, 4.0f) }
    val target = remember { Position(0.0f, 0.0f, 0.0f) }

    val cameraManipulator = rememberCameraManipulator(
        orbitHomePosition = homePosition,
        targetPosition = target,
        creator = {
            CameraGestureDetector.DefaultCameraManipulator(
                Manipulator.Builder()
                    .orbitHomePosition(homePosition)
                    .targetPosition(target)
                    .orbitSpeed(0.005f, 0.005f)
                    .zoomSpeed(0.05f)
                    .build(selectedMode.second)
            )
        }
    )
    // The creator lambda is the remember key, so we reference resetKey inside it to force
    // recreation. This is intentionally read-only to invalidate the remember key.
    @Suppress("UNUSED_EXPRESSION")
    resetKey

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraNode = rememberCameraNode(engine)
    val modelInstance = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")

    DemoScaffold(
        title = "Camera Controls",
        onBack = onBack,
        controls = {
            Text(
                text = "Camera Mode",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                modes.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { selectedMode = mode },
                        label = { Text(mode.first) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { resetKey++ }) {
                Text("Reset Camera")
            }
        }
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            cameraNode = cameraNode,
            cameraManipulator = cameraManipulator
        ) {
            modelInstance?.let { instance ->
                ModelNode(
                    modelInstance = instance,
                    scaleToUnits = 0.5f,
                    centerOrigin = Position(0.0f, 0.0f, 0.0f)
                )
            }
        }
    }
}
