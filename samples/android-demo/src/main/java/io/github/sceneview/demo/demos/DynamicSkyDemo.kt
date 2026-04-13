package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.math.Position
import io.github.sceneview.node.DynamicSkyNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

/**
 * Demonstrates [DynamicSkyNode] — a time-of-day sun that changes colour, intensity, and
 * direction as a slider moves from 0 h (midnight) to 24 h.
 */
@Composable
fun DynamicSkyDemo(onBack: () -> Unit) {
    var timeOfDay by remember { mutableFloatStateOf(12f) }
    var turbidity by remember { mutableFloatStateOf(2f) }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val modelInstance = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")

    DemoScaffold(
        title = "Dynamic Sky",
        onBack = onBack,
        controls = {
            Text(
                "Time of Day: %.1f h".format(timeOfDay),
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = timeOfDay,
                onValueChange = { timeOfDay = it },
                valueRange = 0f..24f
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Turbidity: %.1f".format(turbidity),
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = turbidity,
                onValueChange = { turbidity = it },
                valueRange = 1f..10f
            )
        }
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            environmentLoader = environmentLoader,
            cameraManipulator = rememberCameraManipulator()
        ) {
            DynamicSkyNode(
                timeOfDay = timeOfDay,
                turbidity = turbidity
            )

            modelInstance?.let { instance ->
                ModelNode(
                    modelInstance = instance,
                    scaleToUnits = 0.5f,
                    position = Position(y = 0f)
                )
            }
        }
    }
}
