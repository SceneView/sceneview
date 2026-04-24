package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.LoadingScrim
import io.github.sceneview.demo.rememberPausableHeroYaw
import io.github.sceneview.math.Rotation
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener

/**
 * Full-screen 3D model viewer. The helmet auto-rotates for the "hero" showcase view; the
 * user can still orbit / pinch / drag with the stock camera manipulator at any time.
 *
 * The auto-rotation **pauses** the moment the user touches the viewport so manual
 * gestures don't fight against the spinning model. It also freezes when
 * [DemoSettings.qaMode] is on so screenshot tests capture the same pixel every run.
 */
@Composable
fun ModelViewerDemo(onBack: () -> Unit) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val modelInstance = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")

    // Pausable hero yaw: spins after model loads, stops on first user gesture so the
    // helmet doesn't fight the user's pinch / orbit / drag input.
    val (yaw, onGesture) = rememberPausableHeroYaw(
        trigger = modelInstance != null, durationMillis = 20_000,
    )

    DemoScaffold(title = "Model Viewer", onBack = onBack) {
        Box(modifier = Modifier.fillMaxSize()) {
            SceneView(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                environmentLoader = environmentLoader,
                cameraManipulator = rememberCameraManipulator(),
                onGestureListener = rememberOnGestureListener(
                    onSingleTapUp = { _, _ -> onGesture() },
                    onDoubleTap = { _, _ -> onGesture() },
                    onScroll = { _, _, _, _ -> onGesture() },
                ),
            ) {
                modelInstance?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        scaleToUnits = 0.3f,
                        rotation = Rotation(y = yaw),
                    )
                }
            }

            LoadingScrim(loading = modelInstance == null, label = "Loading model…")
        }
    }
}
