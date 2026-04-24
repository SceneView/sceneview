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
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
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

    DemoScaffold(
        title = "Text Nodes",
        onBack = onBack,
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
            // Dolly the camera closer — 3 stacked 0.18 m-tall labels at the origin read
            // cramped at the default z = 4. z = 1.2 fills the portrait viewport.
            cameraManipulator = rememberCameraManipulator(
                orbitHomePosition = Position(0f, 0f, 1.2f),
                targetPosition = Position(0f, 0f, 0f),
            ),
        ) {
            // Three labels stacked vertically at x=0 so all three fit inside the default
            // camera framing on a phone portrait viewport. y=±0.22 keeps the top and
            // bottom labels fully inside the viewport (y=±0.32 still cropped the top
            // label on the portrait FOV).
            // Top: user text with white on dark background
            TextNode(
                text = inputText,
                fontSize = fontSize,
                textColor = android.graphics.Color.WHITE,
                backgroundColor = 0xCC000000.toInt(),
                widthMeters = 0.7f,
                heightMeters = 0.18f,
                position = Position(x = 0f, y = 0.22f)
            )

            // Center: fixed label with SceneView Primary on dark surface
            TextNode(
                text = "SceneView 4.0",
                fontSize = fontSize,
                textColor = 0xFFA4C1FF.toInt(),  // SceneView TintLight (readable on dark)
                backgroundColor = 0xCC161B22.toInt(),  // SceneView SurfaceDim
                widthMeters = 0.7f,
                heightMeters = 0.18f,
                position = Position(x = 0f, y = 0f)
            )

            // Bottom: fixed label with SceneView Accent tint
            TextNode(
                text = "3D Text Labels",
                fontSize = fontSize,
                textColor = 0xFFD2A8FF.toInt(),  // SceneView TintSoft (purple tint)
                backgroundColor = 0xCC161B22.toInt(),  // SceneView SurfaceDim
                widthMeters = 0.7f,
                heightMeters = 0.18f,
                position = Position(x = 0f, y = -0.22f)
            )
        }
    }
}
