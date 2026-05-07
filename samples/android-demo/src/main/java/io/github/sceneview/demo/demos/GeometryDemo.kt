package io.github.sceneview.demo.demos

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.filament.LightManager
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.SceneViewColors
import io.github.sceneview.material.setMetallic
import io.github.sceneview.material.setRoughness
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.LightNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader

/**
 * Shows the four built-in geometry primitives: Cube, Sphere, Cylinder, Plane.
 *
 * Controls:
 * - Visibility chips per shape (scrollable on narrow viewports)
 * - Metallic / Roughness sliders applied to all visible primitives — lets users see
 *   the full PBR range from a chalky matte (M=0, R=1) to a polished mirror (M=1, R=0)
 * - Continuous Y-axis spin so each shape shows all sides instead of a single static face
 */
@Composable
fun GeometryDemo(onBack: () -> Unit) {
    var showCube by remember { mutableStateOf(true) }
    var showSphere by remember { mutableStateOf(true) }
    var showCylinder by remember { mutableStateOf(true) }
    var showPlane by remember { mutableStateOf(true) }
    // PBR sliders. Defaults match a slightly metallic, slightly rough surface — a
    // visually interesting "in-between" rather than either extreme. Range 0..1
    // covers the full Filament PBR space.
    var metallic by remember { mutableFloatStateOf(0.3f) }
    var roughness by remember { mutableFloatStateOf(0.5f) }

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

    // Continuous Y-axis spin shared by all primitives. withFrameNanos drives the
    // angle off the Choreographer so it runs at the display's refresh rate without
    // a separate Animatable per shape — one frame loop, four nodes share the value.
    var spinDegrees by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos != 0L) {
                    val deltaSec = (nanos - lastNanos) / 1_000_000_000f
                    // 36°/s → one full revolution every 10 s. Wrap to keep the float small.
                    spinDegrees = (spinDegrees + deltaSec * 36f) % 360f
                }
                lastNanos = nanos
            }
        }
    }

    // Apply metallic/roughness to all 4 materials whenever the sliders change.
    LaunchedEffect(metallic, roughness) {
        cubeMaterial.setMetallic(metallic); cubeMaterial.setRoughness(roughness)
        sphereMaterial.setMetallic(metallic); sphereMaterial.setRoughness(roughness)
        cylinderMaterial.setMetallic(metallic); cylinderMaterial.setRoughness(roughness)
        planeMaterial.setMetallic(metallic); planeMaterial.setRoughness(roughness)
    }

    DemoScaffold(
        title = "Geometry Primitives",
        onBack = onBack,
        controls = {
            Text("Visible Shapes", style = MaterialTheme.typography.labelLarge)
            // horizontalScroll on the chip row so a narrow viewport (or future
            // additional shape chips) still fits without overflow / line break.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(showCube, onClick = { showCube = !showCube }, label = { Text("Cube") })
                FilterChip(showSphere, onClick = { showSphere = !showSphere }, label = { Text("Sphere") })
                FilterChip(showCylinder, onClick = { showCylinder = !showCylinder }, label = { Text("Cylinder") })
                FilterChip(showPlane, onClick = { showPlane = !showPlane }, label = { Text("Plane") })
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "Metallic: ${"%.2f".format(metallic)}",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(value = metallic, onValueChange = { metallic = it }, valueRange = 0f..1f)

            Text(
                "Roughness: ${"%.2f".format(roughness)}",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(value = roughness, onValueChange = { roughness = it }, valueRange = 0f..1f)
        }
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            // Dolly the camera closer: primitives live at z = -1.5 m and span only
            // 1.2 m horizontally, so the default camera at z = 4 framed them as tiny
            // dots in a portrait viewport. z = 1.2 puts them at a comfortable 2.7 m.
            cameraManipulator = rememberCameraManipulator(
                orbitHomePosition = Position(0f, 0.2f, 1.2f),
                targetPosition = Position(0f, 0f, -1.5f),
            ),
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
            // Each shape spins on its Y axis to expose all sides — particularly
            // important for the cylinder + plane which have visually distinct
            // front/side faces.
            val spinRotation = Rotation(y = spinDegrees)
            if (showCube) {
                CubeNode(
                    materialInstance = cubeMaterial,
                    size = Float3(0.18f, 0.18f, 0.18f),
                    position = Position(x = -0.6f, y = 0f, z = -1.5f),
                    rotation = spinRotation,
                )
            }
            if (showSphere) {
                SphereNode(
                    materialInstance = sphereMaterial,
                    radius = 0.13f,
                    position = Position(x = -0.2f, y = 0f, z = -1.5f),
                    rotation = spinRotation,
                )
            }
            if (showCylinder) {
                CylinderNode(
                    materialInstance = cylinderMaterial,
                    radius = 0.1f,
                    height = 0.25f,
                    position = Position(x = 0.2f, y = 0f, z = -1.5f),
                    rotation = spinRotation,
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
                    position = Position(x = 0.6f, y = 0f, z = -1.5f),
                    rotation = spinRotation,
                )
            }
        }
    }
}
