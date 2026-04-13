package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import io.github.sceneview.rememberOnGestureListener

/**
 * Demonstrates gesture-based editing of a 3D model.
 *
 * When `isEditable = true`, the ModelNode responds to move, scale, and rotate gestures.
 * Controls let the user toggle editing mode and reset the model to its original position.
 */
@Composable
fun GestureEditingDemo(onBack: () -> Unit) {
    var editable by remember { mutableStateOf(true) }
    // Incrementing the key forces a full recomposition of the SceneView content,
    // which recreates the ModelNode at its default position.
    var resetKey by remember { mutableStateOf(0) }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val modelInstance = rememberModelInstance(modelLoader, "models/khronos_avocado.glb")

    DemoScaffold(
        title = "Gesture Editing",
        onBack = onBack,
        controls = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Editable", style = MaterialTheme.typography.labelLarge)
                Switch(checked = editable, onCheckedChange = { editable = it })
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { resetKey++ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset Position")
            }
        }
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            environmentLoader = environmentLoader,
            cameraManipulator = rememberCameraManipulator(),
            onGestureListener = rememberOnGestureListener()
        ) {
            // The key(resetKey) block ensures the node is recreated from scratch on reset.
            key(resetKey) {
                modelInstance?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        scaleToUnits = 0.3f,
                        centerOrigin = Position(x = 0f, y = 0f, z = 0f),
                        isEditable = editable
                    )
                }
            }
        }
    }
}
