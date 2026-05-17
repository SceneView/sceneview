package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.R
import io.github.sceneview.demo.rememberFirstFrameState
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader

/**
 * Demonstrates TextNode composables with editable text and adjustable font size.
 * Three text labels are arranged in a row at different positions.
 */
@Composable
fun TextDemo(onBack: () -> Unit) {
    var inputText by remember { mutableStateOf("Hello SceneView") }
    var fontSize by remember { mutableFloatStateOf(48f) }

    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val firstFrame = rememberFirstFrameState()

    DemoScaffold(
        title = stringResource(R.string.demo_text_title),
        onBack = onBack,
        firstFrameRendered = firstFrame.rendered,
        controls = {
            Text("Text Content", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Display Text") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Font Size: ${fontSize.toInt()}px", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = fontSize,
                onValueChange = { fontSize = it },
                valueRange = 16f..96f
            )
        }
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            materialLoader = materialLoader,
            onFrame = firstFrame.onFrame,
            // Dolly the camera closer — 3 stacked 0.18 m-tall labels at the origin read
            // cramped at the default z = 4. z = 1.5 fills the portrait viewport while
            // keeping enough vertical room for the top label (#1470: z = 1.2 framed the
            // top 'Hello SceneView' card past the top viewport edge at rest).
            cameraManipulator = rememberCameraManipulator(
                orbitHomePosition = Position(0f, 0f, 1.5f),
                targetPosition = Position(0f, 0f, 0f),
            ),
        ) {
            // Three labels stacked vertically at x=0 so all three fit inside the default
            // camera framing on a phone portrait viewport. y=±0.22 keeps the top and
            // bottom labels' card edges (y reaching ±0.31 with the 0.18 m height) fully
            // inside the viewport at the pulled-back z = 1.5 framing — see #1470.
            //
            // Each label's quad material is marked double-sided: TextNode does not face
            // the camera on its own (no cameraPositionProvider is wired here), so when
            // the camera orbits behind a label the default single-sided culling makes
            // it vanish entirely — see #1426. setDoubleSided is a per-MaterialInstance
            // override on the ubershader, so this is a code-only change (no .filamat
            // recompile) that keeps the back of every label readable.
            // Top: user text with white on dark background
            TextNode(
                text = inputText,
                fontSize = fontSize,
                textColor = android.graphics.Color.WHITE,
                backgroundColor = 0xCC000000.toInt(),
                widthMeters = 0.7f,
                heightMeters = 0.18f,
                position = Position(x = 0f, y = 0.22f),
                apply = { materialInstance.setDoubleSided(true) },
            )

            // Center: fixed label with SceneView Primary on dark surface
            TextNode(
                text = "SceneView 4.0",
                fontSize = fontSize,
                textColor = 0xFFA4C1FF.toInt(),  // SceneView TintLight (readable on dark)
                backgroundColor = 0xCC161B22.toInt(),  // SceneView SurfaceDim
                widthMeters = 0.7f,
                heightMeters = 0.18f,
                position = Position(x = 0f, y = 0f),
                apply = { materialInstance.setDoubleSided(true) },
            )

            // Bottom: fixed label with SceneView Accent tint
            TextNode(
                text = "3D Text Labels",
                fontSize = fontSize,
                textColor = 0xFFD2A8FF.toInt(),  // SceneView TintSoft (purple tint)
                backgroundColor = 0xCC161B22.toInt(),  // SceneView SurfaceDim
                widthMeters = 0.7f,
                heightMeters = 0.18f,
                position = Position(x = 0f, y = -0.22f),
                apply = { materialInstance.setDoubleSided(true) },
            )
        }
    }
}
