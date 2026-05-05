package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.SceneViewColors
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener

/**
 * Demonstrates gesture-based editing of a 3D model.
 *
 * When `isEditable = true`, the Avocado [ModelNode] responds to move, scale, and rotate gestures.
 * A small grey reference sphere sits next to it — it is **not** editable, so it serves as a
 * static landmark: the user can tell whether their gesture is moving the camera (both objects
 * appear to move together) or moving the Avocado (only the avocado moves relative to the sphere).
 *
 * An on-screen overlay at the top of the scene labels the active gesture as either
 * "Editing: Avocado" (when a per-node move/scale/rotate is in progress on the editable model)
 * or "Moving camera" (when the user is touching empty space — orbit / pan / zoom).
 *
 * Controls let the user toggle editing mode and reset the model to its original position.
 */
@Composable
fun GestureEditingDemo(onBack: () -> Unit) {
    var editable by remember { mutableStateOf(true) }
    // Incrementing the key forces a full recomposition of the SceneView content,
    // which recreates the ModelNode at its default position.
    var resetKey by remember { mutableStateOf(0) }

    // Active-gesture label shown at the top of the scene. `null` ⇒ no overlay.
    // Updated from the gesture listener: when a per-node gesture starts/ends we know
    // which target the touch is being applied to (node != null ⇒ Avocado; node == null
    // ⇒ background ⇒ camera orbit/pan/zoom).
    var gestureMode by remember { mutableStateOf<String?>(null) }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val modelInstance = rememberModelInstance(modelLoader, "models/khronos_avocado.glb")

    // Static reference material — neutral grey so it reads as "background landmark"
    // and never competes visually with the editable Avocado.
    val referenceMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(color = SceneViewColors.SurfaceDim)
    }

    DemoScaffold(
        title = "Gesture Editing",
        onBack = onBack,
        controls = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = editable,
                        onValueChange = { editable = it },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Editable", style = MaterialTheme.typography.labelLarge)
                Switch(checked = editable, onCheckedChange = null)
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
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            cameraManipulator = rememberCameraManipulator(),
            // Distinguish per-node gestures from camera gestures via the `node`
            // parameter: when the gesture system identifies a target node, it's a
            // per-node edit; when `node == null`, the gesture falls through to the
            // CameraGestureDetector. We label the overlay accordingly. End-callbacks
            // clear the label so it disappears on release.
            onGestureListener = rememberOnGestureListener(
                onDown = { _, node ->
                    gestureMode = if (node != null) "Editing: Avocado" else "Moving camera"
                },
                onMoveBegin = { _, _, node ->
                    gestureMode = if (node != null) "Editing: Avocado" else "Moving camera"
                },
                onMoveEnd = { _, _, _ -> gestureMode = null },
                onScaleBegin = { _, _, node ->
                    gestureMode = if (node != null) "Editing: Avocado" else "Moving camera"
                },
                onScaleEnd = { _, _, _ -> gestureMode = null },
                onRotateBegin = { _, _, node ->
                    gestureMode = if (node != null) "Editing: Avocado" else "Moving camera"
                },
                onRotateEnd = { _, _, _ -> gestureMode = null }
            )
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
                // Static grey reference sphere — NOT editable. Offset to the right so the
                // user can compare its position against the avocado: if both shift together,
                // it's a camera move; if only the avocado moves, it's a per-node edit.
                SphereNode(
                    radius = 0.05f,
                    materialInstance = referenceMaterial,
                    position = Position(x = 0.4f, y = 0f, z = 0f)
                )
            }
        }

        // On-screen indicator: small pill at the top center showing what the
        // current gesture is doing. Hidden when no gesture is active.
        gestureMode?.let { label ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
                color = Color.Black.copy(alpha = 0.7f),
                contentColor = Color.White,
                tonalElevation = 4.dp,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
