package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.filament.View.AntiAliasing
import com.google.android.filament.View.Dithering
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberView

/**
 * Demonstrates direct Filament [View] post-processing controls: SSAO, anti-aliasing, and dithering.
 *
 * The Filament [View] is created via [rememberView] and passed to [SceneView]. Toggle switches
 * modify the view's properties on every recomposition via [SideEffect]-style updates.
 */
@Composable
fun PostProcessingDemo(onBack: () -> Unit) {
    var ssaoEnabled by remember { mutableStateOf(false) }
    var msaaEnabled by remember { mutableStateOf(false) }
    var fxaaEnabled by remember { mutableStateOf(true) }
    var ditheringEnabled by remember { mutableStateOf(true) }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val view = rememberView(engine)
    val modelInstance = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")

    // Apply post-processing settings to the Filament View
    view.ambientOcclusionOptions = view.ambientOcclusionOptions.apply {
        enabled = ssaoEnabled
    }
    view.multiSampleAntiAliasingOptions = view.multiSampleAntiAliasingOptions.apply {
        enabled = msaaEnabled
    }
    view.antiAliasing = if (fxaaEnabled) AntiAliasing.FXAA else AntiAliasing.NONE
    view.dithering = if (ditheringEnabled) Dithering.TEMPORAL else Dithering.NONE

    DemoScaffold(
        title = "Post-Processing",
        onBack = onBack,
        controls = {
            Text("Render Effects", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            ToggleRow("SSAO (Ambient Occlusion)", ssaoEnabled) { ssaoEnabled = it }
            ToggleRow("MSAA (4x Multi-Sample)", msaaEnabled) { msaaEnabled = it }
            ToggleRow("FXAA (Fast Approx. AA)", fxaaEnabled) { fxaaEnabled = it }
            ToggleRow("Temporal Dithering", ditheringEnabled) { ditheringEnabled = it }
        }
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            environmentLoader = environmentLoader,
            view = view,
            cameraManipulator = rememberCameraManipulator()
        ) {
            modelInstance?.let { instance ->
                ModelNode(
                    modelInstance = instance,
                    scaleToUnits = 0.5f
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
