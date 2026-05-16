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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.android.filament.LightManager
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.R
import io.github.sceneview.demo.LoadingScrim
import io.github.sceneview.demo.rememberFirstFrameState
import io.github.sceneview.demo.rememberHeroOrbitCameraManipulator
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.sample.rememberMaterialInstance

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
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val modelInstance = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")

    // Light position is fixed so swapping types reveals the *type's* signature, not a
    // moving source. Y=1.4, Z=1.0 puts it up-and-forward of the helmet (origin) — close
    // enough that the point falloff visibly fades on the backdrop, far enough that the
    // spot cone projects a clean disc rather than a blown-out wash.
    val lightPosition = remember { Position(0f, 1.4f, 1.0f) }
    // Backdrop wall behind helmet — without this, directional / point / spot all read
    // the same way because the helmet alone gets fully lit by any of them. The wall
    // makes the light's *shape in space* visible: directional = uniform wash, point
    // = radial gradient (1/r²), spot = sharp circular disc with cone edge.
    val backdropMaterial = rememberMaterialInstance(
        materialLoader,
        color = Color(0xFF424448),
        metallic = 0.0f,
        roughness = 0.85f,
    )
    // Bright unlit-looking ball to mark the light position. PBR can't be true
    // self-emissive without an emissive material, but a low-roughness saturated
    // colour with the IBL fallback reads convincingly as a glowing source.
    val sourceMaterial = rememberMaterialInstance(
        materialLoader,
        color = selectedColor.color,
        metallic = 0.0f,
        roughness = 0.0f,
    )

    // Camera orbits the helmet; the model itself stays fixed so the directional /
    // point / spot light hits the exact same surface every frame — otherwise the
    // user can't tell the three light types apart because a spinning helmet sweeps
    // its own surface through the light cone each rotation.
    val cameraManipulator = rememberHeroOrbitCameraManipulator(
        trigger = modelInstance != null,
        radius = 2.0f,
        yHeight = 0.3f,
        durationMillis = 22_000,
    )

    val firstFrame = rememberFirstFrameState()

    DemoScaffold(
        title = stringResource(R.string.demo_lighting_title),
        onBack = onBack,
        firstFrameRendered = firstFrame.rendered,
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
                onFrame = firstFrame.onFrame,
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
                // This demo intentionally composes three nodes off-centre: the helmet at
                // origin, a 3 × 2.4 m backdrop wall behind it, and a light-source marker
                // up-and-forward. With the default autoCenterContent the union bounding
                // box of those three is centred — which shifts the helmet far off the
                // HeroOrbit camera's fixed (0,0,0) pivot, leaving the viewport black
                // (#1421). Disable it so each node keeps its authored world position and
                // the camera orbits the helmet exactly as intended.
                autoCenterContent = false,
                cameraManipulator = cameraManipulator,
            ) {
                modelInstance?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        scaleToUnits = 0.5f,
                    )
                }

                // Backdrop wall — see [backdropMaterial] for the rationale. Sized 3 ×
                // 2.4 m so the helmet appears against a continuous surface from any
                // orbit angle the hero camera reaches; offset back so it never clips
                // into the model.
                PlaneNode(
                    materialInstance = backdropMaterial,
                    size = Float3(3f, 2.4f, 0f),
                    normal = Direction(z = 1f),
                    position = Position(0f, 0.4f, -1.3f),
                )

                // Tiny ball at the light source position — only for Point/Spot since
                // a directional light has no localized origin. The colour of the
                // marker tracks the light colour so users tie source ↔ light visually.
                if (selectedType.type != LightManager.Type.DIRECTIONAL) {
                    SphereNode(
                        materialInstance = sourceMaterial,
                        radius = 0.05f,
                        position = lightPosition,
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
                // Direction points from lightPosition toward the helmet at origin so
                // the spot cone hits the helmet front and the wall behind, making the
                // disc clearly visible. Used for Directional + Spot.
                direction = Direction(0f, -1.4f, -1.0f),
                position = lightPosition,
                apply = {
                    // Spot: very narrow cone (≈11° outer) so the disc on the wall
                    // reads as a sharp circle, not a wide wash. Falloff 4 m keeps
                    // the cone visible against the 1.3 m-deep wall.
                    // Point: aggressive 2 m falloff so the wall shows the radial
                    // gradient (helmet front bright, wall corners dark).
                    if (selectedType.type == LightManager.Type.FOCUSED_SPOT) {
                        spotLightCone(0.05f, 0.2f)
                        falloff(4f)
                    } else if (selectedType.type == LightManager.Type.POINT) {
                        falloff(2.5f)
                    }
                }
            )
            }
            LoadingScrim(loading = modelInstance == null, label = "Loading helmet…")
        }
    }
}
