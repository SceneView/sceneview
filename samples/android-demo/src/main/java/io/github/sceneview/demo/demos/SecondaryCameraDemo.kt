package io.github.sceneview.demo.demos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

/**
 * Secondary camera (picture-in-picture) demo.
 *
 * Shows a main 3D scene with a model and a small PiP overlay rendered from a secondary camera
 * at a different angle. The PiP uses a separate [SceneView] with [SurfaceType.TextureSurface]
 * so it composites correctly over the main view.
 *
 * Controls let the user switch between camera position presets.
 */
@Composable
fun SecondaryCameraDemo(onBack: () -> Unit) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)

    val modelInstance = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")

    var cameraPreset by remember { mutableStateOf(CameraPreset.TOP) }

    // Dedicated camera for the PiP SceneView. Created once, then repositioned each
    // recomposition when the preset changes.
    val pipCameraNode = rememberCameraNode(engine) {
        position = CameraPreset.TOP.position
        lookAt(Position(0f, 0f, 0f))
    }
    androidx.compose.runtime.SideEffect {
        pipCameraNode.position = cameraPreset.position
        pipCameraNode.lookAt(Position(0f, 0f, 0f))
    }

    DemoScaffold(
        title = "Secondary Camera (PiP)",
        onBack = onBack,
        controls = {
            Text("PiP Camera Angle", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CameraPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = cameraPreset == preset,
                        onClick = { cameraPreset = preset },
                        label = { Text(preset.label) }
                    )
                }
            }
        }
    ) {
        // Main 3D scene
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader
        ) {
            modelInstance?.let { instance ->
                ModelNode(
                    modelInstance = instance,
                    scaleToUnits = 0.5f,
                    centerOrigin = Position(0f, 0f, 0f)
                )
            }
        }

        // PiP overlay — a second SceneView rendered with TextureSurface for alpha compositing.
        // Its cameraNode is bound to [pipCameraNode], so switching "Top / Side / Front / Corner"
        // actually changes what the PiP shows (top-down, side profile, front, perspective).
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(160.dp, 120.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.3f))
        ) {
            SceneView(
                modifier = Modifier.fillMaxSize(),
                surfaceType = SurfaceType.TextureSurface,
                engine = engine,
                modelLoader = modelLoader,
                materialLoader = materialLoader,
                cameraNode = pipCameraNode
            ) {
                modelInstance?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        scaleToUnits = 0.5f,
                        centerOrigin = Position(0f, 0f, 0f)
                    )
                }
            }
        }
    }
}

private enum class CameraPreset(val label: String, val position: Position) {
    TOP("Top", Position(0f, 2f, 0f)),
    SIDE("Side", Position(2f, 0.5f, 0f)),
    FRONT("Front", Position(0f, 0.5f, 2f)),
    CORNER("Corner", Position(1.5f, 1.5f, 1.5f))
}
