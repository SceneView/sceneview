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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.google.android.filament.LightManager
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.DemoSettings
import io.github.sceneview.demo.LoadingScrim
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener

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
    // 200 klx default: high enough that the helmet is visibly lit on first-open even
    // before the user touches the slider (110 klx with default IBL still rendered the
    // helmet near-black on Pixel 7a Metal — the PBR helmet materials need ~150 klx of
    // direct light to read clearly against the dark Surface background).
    var intensity by remember { mutableFloatStateOf(200_000f) }
    var selectedColor by remember { mutableStateOf(colorPresets[0]) }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val modelInstance = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")

    // Hero rotation, deferred until the helmet has loaded (no first-frame snap).
    // Pauses on first gesture so the user can orbit / pinch without fighting it.
    val (yaw, onGesture) = io.github.sceneview.demo.rememberPausableHeroYaw(
        trigger = modelInstance != null, durationMillis = 22_000,
    )

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
                            .semantics { contentDescription = "${preset.label} light color" }
                    )
                }
            }
        }
    ) {
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
            SceneView(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                environmentLoader = environmentLoader,
                // Disable the default 110 klx directional main light — otherwise it adds a
                // constant bright directional on top of the user's LightNode and the chip
                // changes (directional vs point vs spot) produce indistinguishable visuals
                // because the helmet is already fully lit by the constant default. The IBL
                // from the default environmentLoader is kept for ambient fill so the helmet
                // is always somewhat visible (neutral_ibl.ktx), and the user's LightNode
                // adds its directional / point / spot contribution on top.
                mainLightNode = null,
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
                        scaleToUnits = 0.5f,
                        rotation = Rotation(y = yaw),
                    )
                }
            LightNode(
                type = selectedType.type,
                intensity = intensity,
                color = io.github.sceneview.math.colorOf(
                    r = selectedColor.r,
                    g = selectedColor.g,
                    b = selectedColor.b
                ),
                direction = Direction(0f, -1f, -1f),
                position = Position(0f, 2f, 2f),
                apply = {
                    // Fixed-at-creation shape settings — only spot/point-specific values.
                    // intensity / direction / colour go through the reactive parameters
                    // above so the user controls actually drive the rendered scene.
                    if (selectedType.type == LightManager.Type.FOCUSED_SPOT) {
                        spotLightCone(0.1f, 0.5f)
                        falloff(10f)
                    } else if (selectedType.type == LightManager.Type.POINT) {
                        falloff(10f)
                    }
                }
            )
            }
            LoadingScrim(loading = modelInstance == null, label = "Loading helmet…")
        }
    }
}
