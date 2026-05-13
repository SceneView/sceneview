package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader

/**
 * Demonstrates ImageNode composable displaying a drawable resource as a flat
 * textured plane in 3D space. A scale slider controls the node's uniform scale.
 */
@Composable
fun ImageDemo(onBack: () -> Unit) {
    var scaleFactor by remember { mutableFloatStateOf(1f) }

    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)

    DemoScaffold(
        title = "Image Planes",
        onBack = onBack,
        controls = {
            Text(
                "Scale: ${"%.1f".format(scaleFactor)}x",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = scaleFactor,
                onValueChange = { scaleFactor = it },
                valueRange = 0.2f..3f
            )
        }
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            materialLoader = materialLoader,
            cameraManipulator = rememberCameraManipulator()
        ) {
            ImageNode(
                imageFileLocation = "textures/sceneview_logo.png",
                position = Position(x = 0f, y = 0f, z = 0f),
                scale = Scale(scaleFactor)
            )
        }
    }
}
