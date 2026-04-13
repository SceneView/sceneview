package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.Node as NodeImpl
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader

/**
 * Demonstrates [PhysicsNode] — drop spheres that fall under gravity and bounce off the floor.
 *
 * Each "Drop" press adds a new sphere. "Reset" clears all spheres by incrementing a generation
 * key that forces full recomposition.
 */
@Composable
fun PhysicsDemo(onBack: () -> Unit) {
    var sphereCount by remember { mutableIntStateOf(1) }
    var generation by remember { mutableIntStateOf(0) }

    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)

    DemoScaffold(
        title = "Physics",
        onBack = onBack,
        controls = {
            Text("Spheres: $sphereCount", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { sphereCount++ }) {
                    Text("Drop")
                }
                Button(onClick = {
                    sphereCount = 1
                    generation++
                }) {
                    Text("Reset")
                }
            }
        }
    ) {
        // key(generation) forces full recomposition on reset
        key(generation) {
            SceneView(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                materialLoader = materialLoader,
                environmentLoader = environmentLoader,
                cameraManipulator = rememberCameraManipulator()
            ) {
                val groundMaterial = remember(materialLoader) { materialLoader.createColorInstance(Color.DarkGray) }
                val sphereMaterials = remember(materialLoader) {
                    listOf(
                        materialLoader.createColorInstance(Color.Red),
                        materialLoader.createColorInstance(Color.Blue),
                        materialLoader.createColorInstance(Color.Green),
                        materialLoader.createColorInstance(Color.Yellow)
                    )
                }

                // Ground plane for visual reference — positioned below center and sized
                // so the camera can see both the plane and the falling spheres above it.
                PlaneNode(
                    materialInstance = groundMaterial,
                    size = Size(x = 3.0f, y = 3.0f),
                    position = Position(y = -1.0f)
                )

                for (i in 0 until sphereCount) {
                    val xOffset = (i % 5 - 2) * 0.4f
                    val startY = 1.5f + i * 0.5f

                    // Capture the Node reference via apply so PhysicsNode can drive it.
                    var nodeRef by remember(i) { mutableStateOf<NodeImpl?>(null) }

                    Node(
                        position = Position(x = xOffset, y = startY, z = -1f),
                        apply = { nodeRef = this }
                    ) {
                        SphereNode(
                            radius = 0.15f,
                            materialInstance = sphereMaterials[i % 4]
                        )
                    }

                    // PhysicsNode attaches an onFrame callback that applies gravity + bounce.
                    nodeRef?.let { node ->
                        PhysicsNode(
                            node = node,
                            restitution = 0.7f,
                            radius = 0.15f
                        )
                    }
                }
            }
        }
    }
}
