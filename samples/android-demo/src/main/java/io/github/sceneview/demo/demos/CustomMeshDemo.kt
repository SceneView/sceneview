package io.github.sceneview.demo.demos

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.sceneview.demo.SceneViewColors
import androidx.compose.ui.unit.dp
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader

/**
 * Demonstrates custom geometry using built-in geometry nodes composed together.
 *
 * Builds a "molecule" structure from SphereNodes and CylinderNodes to show
 * how primitive shapes can be combined into complex custom geometries.
 * A toggle enables continuous rotation and a slider adjusts the scale.
 */
@Composable
fun CustomMeshDemo(onBack: () -> Unit) {
    var rotating by remember { mutableStateOf(true) }
    var scale by remember { mutableFloatStateOf(0.5f) }

    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)

    // Atom spheres in SceneView primary blue, bonds in the accent purple — the same hero
    // gradient the brand palette uses.
    val sphereMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(SceneViewColors.Primary)
    }
    val bondMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(SceneViewColors.Accent)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "meshRotation")
    val rotationY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 8_000, easing = LinearEasing)),
        label = "meshRotationY"
    )

    DemoScaffold(
        title = "Custom Mesh",
        onBack = onBack,
        controls = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = rotating,
                        onValueChange = { rotating = it },
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto-Rotate", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = rotating, onCheckedChange = null)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Scale: ${"%.1f".format(scale)}x", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = scale,
                onValueChange = { scale = it },
                valueRange = 0.5f..2.5f
            )
        }
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            materialLoader = materialLoader,
            cameraManipulator = rememberCameraManipulator()
        ) {
            // Molecule-like structure: center atom + 4 outer atoms connected by bonds
            Node(
                rotation = if (rotating) Rotation(y = rotationY) else Rotation(),
                scale = Float3(scale)
            ) {
                // Center atom
                SphereNode(
                    materialInstance = sphereMaterial,
                    radius = 0.2f,
                    position = Position(0f, 0f, 0f)
                )
                // Top atom + bond
                SphereNode(
                    materialInstance = sphereMaterial,
                    radius = 0.12f,
                    position = Position(0f, 0.6f, 0f)
                )
                CylinderNode(
                    materialInstance = bondMaterial,
                    radius = 0.03f,
                    height = 0.4f,
                    position = Position(0f, 0.3f, 0f)
                )
                // Right atom + bond
                SphereNode(
                    materialInstance = sphereMaterial,
                    radius = 0.12f,
                    position = Position(0.6f, 0f, 0f)
                )
                CylinderNode(
                    materialInstance = bondMaterial,
                    radius = 0.03f,
                    height = 0.4f,
                    position = Position(0.3f, 0f, 0f),
                    rotation = Rotation(z = 90f)
                )
                // Left atom + bond
                SphereNode(
                    materialInstance = sphereMaterial,
                    radius = 0.12f,
                    position = Position(-0.6f, 0f, 0f)
                )
                CylinderNode(
                    materialInstance = bondMaterial,
                    radius = 0.03f,
                    height = 0.4f,
                    position = Position(-0.3f, 0f, 0f),
                    rotation = Rotation(z = 90f)
                )
                // Front atom + bond
                SphereNode(
                    materialInstance = sphereMaterial,
                    radius = 0.12f,
                    position = Position(0f, -0.3f, 0.5f)
                )
                CylinderNode(
                    materialInstance = bondMaterial,
                    radius = 0.03f,
                    height = 0.4f,
                    position = Position(0f, -0.15f, 0.25f),
                    rotation = Rotation(x = 45f)
                )
            }
        }
    }
}
