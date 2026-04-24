package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.LoadingScrim
import io.github.sceneview.demo.rememberHeroOrbitCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

/**
 * Full-screen 3D model viewer. The CAMERA orbits the helmet for the hero showcase so
 * lights, reflections and IBL hit the same surface every frame — a model that spins
 * under the same lighting looks wrong (reflections slide, shadows chase the geometry).
 *
 * The moment the user touches the viewport the orbit hands off to the stock
 * [io.github.sceneview.gesture.CameraGestureDetector.DefaultCameraManipulator] at the
 * exact same pose, so there's no snap — drag / pinch / zoom continue from where the
 * automated orbit left off.
 */
@Composable
fun ModelViewerDemo(onBack: () -> Unit) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val modelInstance = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")

    // Camera orbits; model stays fixed. Radius 1.4 m keeps the 0.3 m helmet framed
    // comfortably at portrait aspect without clipping the near plane.
    val cameraManipulator = rememberHeroOrbitCameraManipulator(
        trigger = modelInstance != null,
        radius = 1.4f,
        yHeight = 0.2f,
        durationMillis = 20_000,
    )

    DemoScaffold(title = "Model Viewer", onBack = onBack) {
        Box(modifier = Modifier.fillMaxSize()) {
            SceneView(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                environmentLoader = environmentLoader,
                cameraManipulator = cameraManipulator,
            ) {
                modelInstance?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        scaleToUnits = 0.3f,
                    )
                }
            }

            LoadingScrim(loading = modelInstance == null, label = "Loading model…")
        }
    }
}
