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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.R
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

/**
 * Secondary camera (picture-in-picture) demo.
 *
 * Two [SceneView]s share the same engine/loaders and render the helmet
 * simultaneously:
 *  - the main view uses the default orbital camera (user-interactive),
 *  - the small PiP overlay binds a dedicated [rememberCameraNode] driven by the
 *    selected preset chip (Top / Side / Front / Corner), so switching chips
 *    actually changes what the PiP shows (top-down, side profile, front-on, 3/4).
 *
 * Each view loads its own [ModelInstance] — two SceneViews cannot share one
 * instance because [ModelNode]'s wrapper entity is `modelInstance.root`, so a
 * single instance attached to two scenes would be destroyed twice on dispose
 * (SIGABRT) and its child light/camera nodes reparented to whichever ModelNode
 * was built last. `rememberModelInstance` calls are cheap — the GLB bytes are
 * parsed once into the shared [io.github.sceneview.loaders.ModelLoader] cache.
 *
 * The PiP SceneView uses [SurfaceType.TextureSurface] so it composites
 * correctly over the main [SurfaceType.Surface] view with rounded corners and
 * a translucent background. Placed top-start so it never collides with the
 * top-end `AssetSourceChip` or bottom-end settings FAB used by [DemoScaffold].
 */
@Composable
fun SecondaryCameraDemo(onBack: () -> Unit) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)

    val mainInstance = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")
    val pipInstance = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")

    var cameraPreset by remember { mutableStateOf(CameraPreset.TOP) }
    val target = remember { Position(0f, 0f, 0f) }

    val pipCameraNode = rememberCameraNode(engine)
    LaunchedEffect(cameraPreset) {
        pipCameraNode.position = cameraPreset.eye
        pipCameraNode.lookAt(target)
    }

    DemoScaffold(
        title = stringResource(R.string.demo_secondary_camera_title),
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
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
        ) {
            mainInstance?.let { instance ->
                ModelNode(
                    modelInstance = instance,
                    scaleToUnits = 0.5f,
                    centerOrigin = Position(0f, 0f, 0f),
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
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
                environmentLoader = environmentLoader,
                cameraNode = pipCameraNode,
            ) {
                pipInstance?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        scaleToUnits = 0.5f,
                        centerOrigin = Position(0f, 0f, 0f),
                    )
                }
            }
        }
    }
}

private enum class CameraPreset(val label: String, val eye: Position) {
    TOP("Top", Position(0f, 1.8f, 0.01f)),
    SIDE("Side", Position(1.8f, 0.2f, 0f)),
    FRONT("Front", Position(0f, 0.2f, 1.8f)),
    CORNER("Corner", Position(1.3f, 0.9f, 1.3f)),
}
