package io.github.sceneview.demo.demos

import android.view.MotionEvent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sceneview.demo.SceneViewColors
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberView

/**
 * Demonstrates collision-based hit testing.
 *
 * Several geometry nodes (cubes and spheres) are placed in the scene. Tapping a node changes its
 * color to highlight it. A "Reset Colors" button reverts all nodes to their default appearance.
 */
@Composable
fun CollisionDemo(onBack: () -> Unit) {
    // Track which node indices have been "hit" (highlighted).
    var highlightedIndices by remember { mutableStateOf(setOf<Int>()) }

    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val view = rememberView(engine)
    val collisionSystem = rememberCollisionSystem(view)

    // Pre-create materials for default and highlighted states.
    // On-brand: Primary blue by default, Accent purple when a shape is hit — same hero
    // gradient as the product identity.
    val defaultMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(color = SceneViewColors.Primary)
    }
    val highlightedMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(color = SceneViewColors.Accent)
    }

    // Node layout: 3 cubes and 2 spheres in a row.
    data class ShapeSpec(
        val index: Int,
        val isSphere: Boolean,
        val position: Position
    )

    val shapes = remember {
        listOf(
            ShapeSpec(0, isSphere = false, position = Position(x = -0.6f, y = 0f, z = -2f)),
            ShapeSpec(1, isSphere = true, position = Position(x = -0.3f, y = 0.3f, z = -2f)),
            ShapeSpec(2, isSphere = false, position = Position(x = 0f, y = 0f, z = -2f)),
            ShapeSpec(3, isSphere = true, position = Position(x = 0.3f, y = 0.3f, z = -2f)),
            ShapeSpec(4, isSphere = false, position = Position(x = 0.6f, y = 0f, z = -2f))
        )
    }

    val gestureListener = rememberOnGestureListener(
        // `onSingleTapUp` fires on release, no 300 ms double-tap disambiguation window.
        // Matches the ViewNodeDemo fix — users (and tests) firing several single taps in
        // quick succession should see each one register instead of being dropped as a
        // possible double-tap.
        onSingleTapUp = { _: MotionEvent, node: Node? ->
            if (node != null) {
                val idx = node.name?.removePrefix("shape_")?.toIntOrNull()
                if (idx != null) {
                    highlightedIndices = if (idx in highlightedIndices) {
                        highlightedIndices - idx
                    } else {
                        highlightedIndices + idx
                    }
                }
            }
        }
    )

    DemoScaffold(
        title = "Collision & Hit Test",
        onBack = onBack,
        controls = {
            Text(
                "Tap a shape to highlight it",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { highlightedIndices = emptySet() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset Colors")
            }
        }
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            view = view,
            collisionSystem = collisionSystem,
            cameraManipulator = rememberCameraManipulator(),
            onGestureListener = gestureListener
        ) {
            for (shape in shapes) {
                val mat = if (shape.index in highlightedIndices) {
                    highlightedMaterial
                } else {
                    defaultMaterial
                }
                if (shape.isSphere) {
                    SphereNode(
                        radius = 0.15f,
                        materialInstance = mat,
                        position = shape.position,
                        apply = {
                            name = "shape_${shape.index}"
                            isHittable = true
                        }
                    )
                } else {
                    CubeNode(
                        size = Size(x = 0.25f, y = 0.25f, z = 0.25f),
                        materialInstance = mat,
                        position = shape.position,
                        apply = {
                            name = "shape_${shape.index}"
                            isHittable = true
                        }
                    )
                }
            }
        }
    }
}
