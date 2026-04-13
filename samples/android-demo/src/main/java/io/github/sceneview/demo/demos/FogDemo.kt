package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.node.FogNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberView

/**
 * Demonstrates atmospheric fog applied to a 3D scene.
 *
 * Users can toggle fog on/off, adjust density via a slider, and pick from colour presets.
 * The fog is applied through [FogNode], which wraps Filament's per-View fog options.
 */
@Composable
fun FogDemo(onBack: () -> Unit) {
    data class FogPreset(val label: String, val color: Color)

    val presets = remember {
        listOf(
            FogPreset("Mist", Color(0xFFCCDDFF)),
            FogPreset("Warm Haze", Color(0xFFFFDDAA)),
            FogPreset("Eerie Green", Color(0xFFAAFFCC)),
            FogPreset("Deep Smoke", Color(0xFF888888))
        )
    }

    var fogEnabled by remember { mutableStateOf(true) }
    var fogDensity by remember { mutableFloatStateOf(0.15f) }
    var selectedPreset by remember { mutableStateOf(presets[0]) }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val view = rememberView(engine)
    val modelInstance = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")

    DemoScaffold(
        title = "Fog",
        onBack = onBack,
        controls = {
            // Enable / disable toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Fog Enabled", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = fogEnabled, onCheckedChange = { fogEnabled = it })
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Density slider
            Text(
                text = "Density: ${"%.2f".format(fogDensity)}",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = fogDensity,
                onValueChange = { fogDensity = it },
                valueRange = 0f..1f,
                enabled = fogEnabled
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Colour presets
            Text("Color Preset", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    FilterChip(
                        selected = selectedPreset == preset,
                        onClick = { selectedPreset = preset },
                        label = { Text(preset.label) },
                        enabled = fogEnabled
                    )
                }
            }
        }
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            view = view
        ) {
            FogNode(
                view = view,
                enabled = fogEnabled,
                density = fogDensity,
                color = selectedPreset.color
            )
            modelInstance?.let { instance ->
                ModelNode(
                    modelInstance = instance,
                    scaleToUnits = 0.5f
                )
            }
        }
    }
}
