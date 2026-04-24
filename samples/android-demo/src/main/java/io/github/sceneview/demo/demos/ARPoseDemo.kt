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

    // Sliders hold the user-tweakable *offset* from the pose captured in front of
    // the camera on first tracked frame. Keeping the offset small and centred means
    // the cubes start where the user is already pointing — the previous version
    // placed them at ARCore world origin (wherever the phone was at session-start),
    // which is usually off-screen once the user moves.
    var x by remember { mutableFloatStateOf(0f) }
    var y by remember { mutableFloatStateOf(0f) }
    var z by remember { mutableFloatStateOf(0f) }
    var isTracking by remember { mutableStateOf(false) }
    // Base pose: 1 m in front of the camera at the moment the demo gained tracking.
    // Captured once per session to keep the cubes anchored in world space (so sliders
    // nudge them relative to that anchor, not to the moving camera).
    var basePose by remember { mutableStateOf<Pose?>(null) }

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
                        valueRange = -1f..1f,
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
                        valueRange = -0.5f..0.5f,
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
                        valueRange = -0.5f..0.5f,
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
                    if (isTracking && basePose == null) {
                        // Compute a pose 1 m in front of the camera, without tilt —
                        // keep the camera's yaw (facing direction) but zero-out
                        // pitch/roll so the base pose is level and the cubes stay
                        // upright on the horizon rather than matching the phone's
                        // exact orientation.
                        val cameraPose = frame.camera.pose
                        val translation = cameraPose.transformPoint(floatArrayOf(0f, 0f, -1f))
                        basePose = Pose(translation, floatArrayOf(0f, 0f, 0f, 1f))
                    }
                }
            ) {
                val base = basePose
                if (isTracking && base != null) {
                    // Cache Pose objects across recompositions — without remember,
                    // each slider drag allocates two new Pose + four FloatArray
                    // instances per frame.
                    val cubePose = remember(base, x, y, z) {
                        val t = base.translation
                        Pose(
                            floatArrayOf(t[0] + x, t[1] + y, t[2] + z),
                            floatArrayOf(0f, 0f, 0f, 1f),
                        )
                    }
                    val spherePose = remember(base, x, y, z) {
                        val t = base.translation
                        Pose(
                            floatArrayOf(t[0] + x + 0.3f, t[1] + y, t[2] + z),
                            floatArrayOf(0f, 0f, 0f, 1f),
                        )
                    }
                    // Cube is 0.2 m / sphere is 0.1 m — big enough to read clearly
                    // at 1 m from the camera on a phone screen. The previous 0.1 m
                    // cube was visible only as a couple of pixels at default FOV.
                    PoseNode(pose = cubePose) {
                        CubeNode(
                            size = Size(0.2f),
                            materialInstance = cubeMaterial,
                        )
                    }
                    PoseNode(pose = spherePose) {
                        SphereNode(
                            radius = 0.1f,
                            materialInstance = cubeMaterial,
                        )
                    }
                }
            }
        }
    }
}
