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
import androidx.compose.ui.unit.dp
import com.google.android.filament.LightManager
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.SceneViewColors
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.Node as NodeImpl
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
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
    // Camera slightly above scene, angled down so the floor + falling spheres are both framed
    val cameraNode = rememberCameraNode(engine) {
        position = Position(0f, 1.5f, 3f)
        lookAt(Position(0f, 0f, 0f))
    }

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
                Button(onClick = { sphereCount += 10 }) {
                    Text("Drop 10")
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
                cameraNode = cameraNode,
                cameraManipulator = rememberCameraManipulator(
                    orbitHomePosition = cameraNode.worldPosition
                )
            ) {
                // Explicit directional light — without this, the PBR colored-material spheres
                // rendered against the default empty environment appear almost black and the
                // demo looks broken (only the floor plane's DarkGray is visible).
                LightNode(
                    type = LightManager.Type.DIRECTIONAL,
                    direction = io.github.sceneview.math.Direction(-0.3f, -1f, -0.5f),
                    apply = {
                        intensity(100_000f)
                    }
                )
                val groundMaterial = remember(materialLoader) {
                    materialLoader.createColorInstance(SceneViewColors.SurfaceDim)
                }
                val sphereMaterials = remember(materialLoader) {
                    SceneViewColors.Ramp4.map { materialLoader.createColorInstance(it) }
                }

                // Ground plane — must use Size(x, y=0, z) for a HORIZONTAL floor
                // (the XZ plane). Passing Size(x, y, z=0) like before built a vertical
                // wall in the XY plane that the camera saw edge-on as a tilted shape,
                // not a floor.
                PlaneNode(
                    materialInstance = groundMaterial,
                    size = Size(x = 1.6f, y = 0f, z = 1.6f),
                    position = Position(y = -0.5f),
                )

                // Diagnostic static sphere (no physics) — if even this is not visible, the bug
                // is in the rendering pipeline, not the physics simulation.
                SphereNode(
                    radius = 0.1f,
                    materialInstance = sphereMaterials[0],  // red
                    position = Position(x = -0.5f, y = 0.2f, z = 0f)
                )

                for (i in 0 until sphereCount) {
                    // Spread spheres across the floor plane in a 5×N grid so a "Drop 10"
                    // looks like a colourful rain instead of a single vertical column.
                    // x stays inside [-0.6, 0.6] (matches floor plane width) and y stacks
                    // up so newly-dropped spheres start above the previous batch.
                    val xOffset = (i % 5 - 2) * 0.18f + (if (i / 5 % 2 == 0) 0f else 0.09f)
                    val zOffset = ((i / 5) % 3 - 1) * 0.18f
                    val startY = 0.6f + (i / 5) * 0.18f

                    // Capture the Node reference via apply so PhysicsNode can drive it.
                    var nodeRef by remember(i) { mutableStateOf<NodeImpl?>(null) }

                    Node(
                        position = Position(x = xOffset, y = startY, z = zOffset),
                        apply = { nodeRef = this }
                    ) {
                        SphereNode(
                            radius = 0.08f,
                            materialInstance = sphereMaterials[i % 4]
                        )
                    }

                    // PhysicsNode attaches an onFrame callback that applies gravity + bounce.
                    nodeRef?.let { node ->
                        PhysicsNode(
                            node = node,
                            restitution = 0.7f,
                            floorY = -0.5f,
                            radius = 0.08f
                        )
                    }
                }
            }
        }
    }
}
