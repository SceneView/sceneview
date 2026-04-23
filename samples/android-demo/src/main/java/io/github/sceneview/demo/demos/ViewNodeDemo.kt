package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.math.Position
import io.github.sceneview.node.ViewNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberViewNodeManager

/**
 * Demonstrates ViewNode — embedding live Compose UI inside a 3D scene.
 *
 * A Card with text and a counter button floats in 3D space. The control panel toggles visibility.
 */
@Composable
fun ViewNodeDemo(onBack: () -> Unit) {
    var isVisible by remember { mutableStateOf(true) }

    // Tap-counter state is hoisted to the demo scope so both the embedded Compose
    // Button (inside the 3D-textured card) *and* a viewport-level gesture listener
    // write to the same source of truth. A raw MotionEvent hitting the SurfaceView
    // never reaches the Compose hierarchy embedded in the ViewNode quad (library
    // limitation — input is not routed back through the 3D projection), so the
    // gesture listener is the reliable path on touch. Users on real hardware who
    // physically reach the cube's "Tap me" button via the side-pixel accessibility
    // tree still get a count via the button's own onClick.
    var tapCount by remember { mutableIntStateOf(0) }

    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val windowManager = rememberViewNodeManager()

    val gestureListener = rememberOnGestureListener(
        // onSingleTapUp fires on the touch-up immediately, no 300 ms double-tap
        // disambiguation delay — lets rapid tap sequences (tests, impatient users)
        // increment the counter every time instead of dropping alternate taps.
        onSingleTapUp = { _, _ -> tapCount++ },
    )

    DemoScaffold(
        title = "View Node",
        onBack = onBack,
        controls = {
            // Wrap the whole row in Modifier.toggleable so the row itself is the
            // click target — tapping anywhere on "Visible" (label or switch) flips
            // the state. Also gives UiAutomator a clickable ancestor of the label.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = isVisible,
                        onValueChange = { isVisible = it },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Visible", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.width(12.dp))
                Switch(checked = isVisible, onCheckedChange = null)
            }
        }
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            viewNodeWindowManager = windowManager,
            cameraManipulator = rememberCameraManipulator(),
            onGestureListener = gestureListener,
        ) {
            ViewNode(
                windowManager = windowManager,
                unlit = true,
                position = Position(x = 0f, y = 0f, z = -2f),
                scale = Float3(0.15f),
                isVisible = isVisible
            ) {
                // This Compose content is rendered onto a quad in 3D space.
                EmbeddedCard(tapCount = tapCount, onTap = { tapCount++ })
            }
        }
    }
}

/**
 * A simple Compose Card used as the embedded content for the ViewNode.
 *
 * State is hoisted up so viewport taps (which can't cross the 3D projection to reach
 * this Compose tree) and the embedded Button share the same counter.
 */
@Composable
private fun EmbeddedCard(tapCount: Int, onTap: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Hello from 3D!",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tapped $tapCount times",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onTap) {
                Text("Tap me")
            }
        }
    }
}
