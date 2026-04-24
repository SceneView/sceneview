package io.github.sceneview.demo.demos

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
import io.github.sceneview.demo.rememberPausableHeroYaw
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberOnGestureListener

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

    // Hero yaw auto-pauses on first camera gesture so orbit/pinch don't fight the spin.
    // Triggered by the user's Auto-Rotate switch.
    val (rotationY, onHeroGesture) = rememberPausableHeroYaw(
        trigger = rotating, durationMillis = 8_000, staticYaw = 0f,
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
            cameraManipulator = rememberCameraManipulator(),
            onGestureListener = rememberOnGestureListener(
                onSingleTapUp = { _, _ -> onHeroGesture() },
                onDoubleTap = { _, _ -> onHeroGesture() },
                onScroll = { _, _, _, _ -> onHeroGesture() },
            ),
        ) {
            // Molecule-like structure: center atom + 4 outer atoms connected by bonds.
            // Rotation y freezes at the current angle when the user toggles off, which reads
            // better than snapping back to 0° — you can stop the molecule at any pose.
            Node(
                rotation = Rotation(y = rotationY),
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
                // Front atom + bond — aligned on +Z so the bond is a clean 90° X
                // rotation instead of the previous (−Y, +Z) diagonal that left the
                // cylinder dangling at the wrong angle between the two atoms.
                SphereNode(
                    materialInstance = sphereMaterial,
                    radius = 0.12f,
                    position = Position(0f, 0f, 0.6f)
                )
                CylinderNode(
                    materialInstance = bondMaterial,
                    radius = 0.03f,
                    height = 0.4f,
                    position = Position(0f, 0f, 0.3f),
                    rotation = Rotation(x = 90f)
                )
            }
        }
    }
}
