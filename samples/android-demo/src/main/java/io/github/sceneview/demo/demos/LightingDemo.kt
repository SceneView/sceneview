package io.github.sceneview.demo.demos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.android.filament.LightManager
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

/**
 * Demonstrates directional, point, and spot lights with interactive controls.
 */
@Composable
fun LightingDemo(onBack: () -> Unit) {
    data class LightTypeOption(val label: String, val type: LightManager.Type)

    val lightTypes = remember {
        listOf(
            LightTypeOption("Directional", LightManager.Type.DIRECTIONAL),
            LightTypeOption("Point", LightManager.Type.POINT),
            LightTypeOption("Spot", LightManager.Type.FOCUSED_SPOT)
        )
    }

    data class ColorPreset(val label: String, val color: Color, val r: Float, val g: Float, val b: Float)

    val colorPresets = remember {
        listOf(
            ColorPreset("White", Color.White, 1f, 1f, 1f),
            ColorPreset("Warm", Color(0xFFFFCC66), 1f, 0.8f, 0.4f),
            ColorPreset("Blue", Color(0xFF6699FF), 0.4f, 0.6f, 1f),
            ColorPreset("Red", Color(0xFFFF6666), 1f, 0.4f, 0.4f)
        )
    }

    var selectedType by remember { mutableStateOf(lightTypes[0]) }
    var intensity by remember { mutableFloatStateOf(100_000f) }
    var selectedColor by remember { mutableStateOf(colorPresets[0]) }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val modelInstance = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")

    DemoScaffold(
        title = "Lighting",
        onBack = onBack,
        controls = {
            // Light type selector
            Text("Light Type", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                lightTypes.forEach { lt ->
                    FilterChip(
                        selected = selectedType == lt,
                        onClick = { selectedType = lt },
                        label = { Text(lt.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Intensity slider
            Text(
                "Intensity: ${intensity.toInt()}",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = intensity,
                onValueChange = { intensity = it },
                valueRange = 10_000f..500_000f
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Color presets
            Text("Color", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                colorPresets.forEach { preset ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(preset.color, CircleShape)
                            .then(
                                if (selectedColor == preset) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                } else Modifier
                            )
                            .clickable { selectedColor = preset }
                    )
                }
            }
        }
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            environmentLoader = environmentLoader,
            cameraManipulator = rememberCameraManipulator()
        ) {
            modelInstance?.let { instance ->
                ModelNode(
                    modelInstance = instance,
                    scaleToUnits = 0.5f
                )
            }
            LightNode(
                type = selectedType.type,
                position = Position(0f, 2f, 2f),
                direction = Direction(0f, -1f, -1f),
                apply = {
                    intensity(intensity)
                    color(selectedColor.r, selectedColor.g, selectedColor.b)
                    if (selectedType.type == LightManager.Type.FOCUSED_SPOT) {
                        spotLightCone(0.1f, 0.5f)
                        falloff(10f)
                    } else if (selectedType.type == LightManager.Type.POINT) {
                        falloff(10f)
                    }
                }
            )
        }
    }
}
