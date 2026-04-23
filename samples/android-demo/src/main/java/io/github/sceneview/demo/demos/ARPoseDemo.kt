package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.SceneViewColors
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader

/**
 * AR pose placement demo.
 *
 * Places objects at specific [Pose] coordinates in AR space using [PoseNode].
 * Unlike [AnchorNode], a PoseNode is not persisted and tracks the given pose directly.
 * Sliders let the user adjust x, y, z offsets in real time.
 */
@Composable
fun ARPoseDemo(onBack: () -> Unit) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)

    var x by remember { mutableFloatStateOf(0f) }
    var y by remember { mutableFloatStateOf(-0.5f) }
    var z by remember { mutableFloatStateOf(-1.0f) }
    var isTracking by remember { mutableStateOf(false) }

    // Colored material for the pose indicator cube — SceneView Accent purple, with a
    // touch of metallic for the AR-debug readability that the previous orange provided.
    val cubeMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(
            color = SceneViewColors.Accent,
            metallic = 0.5f,
            roughness = 0.3f,
            reflectance = 0.5f
        )
    }

    DemoScaffold(
        title = "Pose Placement",
        onBack = onBack,
        controls = {
            Text("Position Controls", style = MaterialTheme.typography.labelLarge)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // X slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("X", modifier = Modifier.alignByBaseline())
                    Slider(
                        value = x,
                        onValueChange = { x = it },
                        valueRange = -2f..2f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "%.2f".format(x),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.alignByBaseline()
                    )
                }

                // Y slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Y", modifier = Modifier.alignByBaseline())
                    Slider(
                        value = y,
                        onValueChange = { y = it },
                        valueRange = -2f..2f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "%.2f".format(y),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.alignByBaseline()
                    )
                }

                // Z slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Z", modifier = Modifier.alignByBaseline())
                    Slider(
                        value = z,
                        onValueChange = { z = it },
                        valueRange = -3f..0f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "%.2f".format(z),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.alignByBaseline()
                    )
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ARSceneView(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                materialLoader = materialLoader,
                planeRenderer = true,
                sessionConfiguration = { _: Session, config: Config ->
                    config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                },
                onSessionUpdated = { _, frame: Frame ->
                    isTracking = frame.camera.trackingState == TrackingState.TRACKING
                }
            ) {
                if (isTracking) {
                    // Place a cube at the specified pose in AR world space
                    PoseNode(
                        pose = Pose(floatArrayOf(x, y, z), floatArrayOf(0f, 0f, 0f, 1f)),
                        onPoseChanged = { newPose ->
                            // Pose was updated
                        }
                    ) {
                        CubeNode(
                            size = Size(0.1f),
                            materialInstance = cubeMaterial
                        )
                    }

                    // Place a second indicator slightly offset
                    PoseNode(
                        pose = Pose(
                            floatArrayOf(x + 0.2f, y, z),
                            floatArrayOf(0f, 0f, 0f, 1f)
                        )
                    ) {
                        SphereNode(
                            radius = 0.05f,
                            materialInstance = cubeMaterial
                        )
                    }
                }
            }
        }
    }
}
