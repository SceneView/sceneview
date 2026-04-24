package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.LoadingScrim
import io.github.sceneview.demo.rememberHeroYaw
import io.github.sceneview.math.Rotation
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

/**
 * Full-screen 3D model viewer. The helmet auto-rotates for the "hero" showcase view; the
 * user can still orbit / pinch / drag with the stock camera manipulator at any time.
 *
 * The auto-rotation freezes when [DemoSettings.qaMode] is on so screenshot tests capture
 * the same pixel every run.
 */
@Composable
fun ModelViewerDemo(onBack: () -> Unit) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val modelInstance = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")

    // Hero yaw starts spinning only AFTER the helmet finishes loading so the model
    // doesn't appear at 0° for one frame and then snap to whatever angle a free-running
    // InfiniteTransition reached during the 8-10 s GLB decode.
    val yaw = rememberHeroYaw(trigger = modelInstance != null, durationMillis = 20_000)

    DemoScaffold(title = "Model Viewer", onBack = onBack) {
        Box(modifier = Modifier.fillMaxSize()) {
            SceneView(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                environmentLoader = environmentLoader,
                cameraManipulator = rememberCameraManipulator(),
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
