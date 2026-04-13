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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader

/**
 * Shows the four built-in geometry primitives: Cube, Sphere, Cylinder, Plane.
 * Toggle chips control which shapes are visible.
 */
@Composable
fun GeometryDemo(onBack: () -> Unit) {
    var showCube by remember { mutableStateOf(true) }
    var showSphere by remember { mutableStateOf(true) }
    var showCylinder by remember { mutableStateOf(true) }
    var showPlane by remember { mutableStateOf(true) }

    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)

    val redMaterial = remember(materialLoader) { materialLoader.createColorInstance(Color.Red) }
    val blueMaterial = remember(materialLoader) { materialLoader.createColorInstance(Color.Blue) }
    val greenMaterial = remember(materialLoader) { materialLoader.createColorInstance(Color.Green) }
    val yellowMaterial = remember(materialLoader) { materialLoader.createColorInstance(Color.Yellow) }

    DemoScaffold(
        title = "Geometry Primitives",
        onBack = onBack,
        controls = {
            Text("Visible Shapes", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(showCube, onClick = { showCube = !showCube }, label = { Text("Cube") })
                FilterChip(showSphere, onClick = { showSphere = !showSphere }, label = { Text("Sphere") })
                FilterChip(showCylinder, onClick = { showCylinder = !showCylinder }, label = { Text("Cylinder") })
                FilterChip(showPlane, onClick = { showPlane = !showPlane }, label = { Text("Plane") })
            }
        }
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            materialLoader = materialLoader,
            cameraManipulator = rememberCameraManipulator()
        ) {
            if (showCube) {
                CubeNode(
                    materialInstance = redMaterial,
                    size = Float3(0.15f, 0.15f, 0.15f),
                    position = Position(x = -0.3f, y = 0.1f)
                )
            }
            if (showSphere) {
                SphereNode(
                    materialInstance = blueMaterial,
                    radius = 0.1f,
                    position = Position(x = -0.1f, y = 0.1f)
                )
            }
            if (showCylinder) {
                CylinderNode(
                    materialInstance = greenMaterial,
                    radius = 0.08f,
                    height = 0.2f,
                    position = Position(x = 0.1f, y = 0.1f)
                )
            }
            if (showPlane) {
                PlaneNode(
                    materialInstance = yellowMaterial,
                    size = Float3(0.3f, 0.3f, 1f),
                    position = Position(x = 0.3f, y = 0.1f)
                )
            }
        }
    }
}
