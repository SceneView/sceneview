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
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

/**
 * Loads three different glTF models side by side with per-model visibility toggles.
 */
@Composable
fun MultiModelDemo(onBack: () -> Unit) {
    var showAvocado by remember { mutableStateOf(true) }
    var showLantern by remember { mutableStateOf(true) }
    var showHelmet by remember { mutableStateOf(true) }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)

    val avocado = rememberModelInstance(modelLoader, "models/khronos_avocado.glb")
    val lantern = rememberModelInstance(modelLoader, "models/khronos_lantern.glb")
    val helmet = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")

    DemoScaffold(
        title = "Multiple Models",
        onBack = onBack,
        controls = {
            Text("Visibility", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = showAvocado,
                    onClick = { showAvocado = !showAvocado },
                    label = { Text("Avocado") }
                )
                FilterChip(
                    selected = showLantern,
                    onClick = { showLantern = !showLantern },
                    label = { Text("Lantern") }
                )
                FilterChip(
                    selected = showHelmet,
                    onClick = { showHelmet = !showHelmet },
                    label = { Text("Helmet") }
                )
            }
        }
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            environmentLoader = environmentLoader,
            cameraManipulator = rememberCameraManipulator()
        ) {
            if (showAvocado) {
                avocado?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        scaleToUnits = 0.3f,
                        position = Position(x = -0.4f, y = 0f)
                    )
                }
            }
            if (showLantern) {
                lantern?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        scaleToUnits = 0.3f,
                        position = Position(x = 0f, y = 0f)
                    )
                }
            }
            if (showHelmet) {
                helmet?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        scaleToUnits = 0.3f,
                        position = Position(x = 0.4f, y = 0f)
                    )
                }
            }
        }
    }
}
