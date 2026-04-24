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
import com.google.android.filament.LightManager
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.SceneViewColors
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.node.LightNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
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
    // IBL environment gives the primitives real ambient + specular highlights, so the
    // rendering doesn't look flat. Without this the cube/cylinder/plane are unlit.
    val environmentLoader = rememberEnvironmentLoader(engine)

    // On-brand ramp — Primary blue, Accent purple, light blue, soft purple — so the four
    // primitives stay visually distinct while all reading as SceneView.
    val cubeMaterial = remember(materialLoader) { materialLoader.createColorInstance(SceneViewColors.Ramp4[0]) }
    val sphereMaterial = remember(materialLoader) { materialLoader.createColorInstance(SceneViewColors.Ramp4[1]) }
    val cylinderMaterial = remember(materialLoader) { materialLoader.createColorInstance(SceneViewColors.Ramp4[2]) }
    val planeMaterial = remember(materialLoader) { materialLoader.createColorInstance(SceneViewColors.Ramp4[3]) }

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
            environmentLoader = environmentLoader,
            cameraManipulator = rememberCameraManipulator()
        ) {
            // Key light — gives each primitive a clear shaded/lit side so the 3D form
            // reads as depth rather than flat color silhouettes.
            LightNode(
                engine = engine,
                type = LightManager.Type.DIRECTIONAL,
                apply = {
                    color(1.0f, 0.95f, 0.9f)
                    intensity(80_000f)
                    direction(0.3f, -1f, -0.5f)
                    castShadows(false)
                },
            )

            // Centered around x=0, equal spacing 0.4 m apart, lifted slightly so
            // they sit in the centre of the portrait viewport instead of drifting
            // to the right like with the previous tighter spacing.
            if (showCube) {
                CubeNode(
                    materialInstance = cubeMaterial,
                    size = Float3(0.18f, 0.18f, 0.18f),
                    position = Position(x = -0.6f, y = 0f, z = -1.5f)
                )
            }
            if (showSphere) {
                SphereNode(
                    materialInstance = sphereMaterial,
                    radius = 0.13f,
                    position = Position(x = -0.2f, y = 0f, z = -1.5f)
                )
            }
            if (showCylinder) {
                CylinderNode(
                    materialInstance = cylinderMaterial,
                    radius = 0.1f,
                    height = 0.25f,
                    position = Position(x = 0.2f, y = 0f, z = -1.5f)
                )
            }
            if (showPlane) {
                // Plane primitive renders as a flat XY panel facing the camera (+Z).
                // Previous `Float3(0.32, 0.32, 1f)` made a tilted parallelogram because
                // Plane.getVertices uses ALL three size components — a non-zero z on what
                // should be a flat XY quad twists the four corners into a diagonal surface.
                // Use z=0 for a true flat panel, and set `normal = +Z` so lighting hits
                // the visible face.
                PlaneNode(
                    materialInstance = planeMaterial,
                    size = Float3(0.32f, 0.32f, 0f),
                    normal = Direction(z = 1f),
                    position = Position(x = 0.6f, y = 0f, z = -1.5f)
                )
            }
        }
    }
}
