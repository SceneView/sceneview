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
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.SceneView
import io.github.sceneview.demo.SceneViewColors
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.R
import io.github.sceneview.demo.rememberFirstFrameState
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.sample.rememberMaterialInstance

/**
 * Demonstrates the three ways a flat [io.github.sceneview.node.TextNode] label can face the
 * world, plus a thickness control that extrudes a real 3D slab behind each label so the
 * 3D-ness is obvious as the camera orbits.
 *
 * The three stacked labels each demonstrate one facing mode:
 *  1. **Single-sided** — the quad material is culled from behind, so the label vanishes
 *     entirely once the camera orbits past 90°, exactly like a one-sided plane.
 *  2. **Double-sided** — `materialInstance.setDoubleSided(true)` keeps the back face drawn,
 *     so the text stays visible from behind (mirror-reversed, as expected of a flat quad).
 *  3. **Mirror / billboard** — a `cameraPositionProvider` is wired, so the label rotates to
 *     face the camera every frame and always reads in the correct orientation.
 *
 * ### Thickness control — note on text extrusion
 * SceneView's `TextNode` renders text to a *flat* bitmap quad; the SDK has **no true glyph
 * extrusion API**. To still make the "3D text" tangible, each label is mounted on a real
 * extruded [io.github.sceneview.node.CubeNode] slab whose Z-depth is driven by the Thickness
 * slider. Sliding it from 0 (paper-thin) to a deep slab shows genuine geometric depth, edge
 * shading and parallax — the closest faithful representation until the SDK ships glyph
 * extrusion (tracked for a future release).
 */
@Composable
fun TextDemo(onBack: () -> Unit) {
    var inputText by remember { mutableStateOf("Hello SceneView") }
    var fontSize by remember { mutableFloatStateOf(48f) }
    // Z-depth of the slab behind each label, in meters. 0.02 m reads as a thin plaque;
    // 0.18 m is a chunky 3D block. The text quad is parked just in front of the slab's
    // front face so it always reads on top no matter the thickness.
    var thickness by remember { mutableFloatStateOf(0.06f) }

    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val firstFrame = rememberFirstFrameState()

    // We own the camera node so the billboard label can read its world position every
    // frame via `cameraPositionProvider`. `cameraWorldPos` is refreshed in `onFrame`
    // (main thread, once per rendered frame) and read back by the provider lambda.
    val cameraNode = rememberCameraNode(engine)
    var cameraWorldPos by remember { mutableStateOf(Position(0f, 0f, 1.5f)) }

    // Lit slab materials — one tint per label so the three modes stay visually distinct.
    val slabMaterialSingle = rememberMaterialInstance(materialLoader, SceneViewColors.Primary)
    val slabMaterialDouble = rememberMaterialInstance(materialLoader, SceneViewColors.Accent)
    val slabMaterialMirror = rememberMaterialInstance(materialLoader, SceneViewColors.AccentDeep)

    DemoScaffold(
        title = stringResource(R.string.demo_text_title),
        onBack = onBack,
        firstFrameRendered = firstFrame.rendered,
        controls = {
            Text("Text Content", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Top label text") },
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
            Spacer(modifier = Modifier.height(8.dp))
            // Thickness — extrudes the 3D slab behind every label. See the KDoc note:
            // SceneView has no glyph-extrusion API, so this drives a real CubeNode depth.
            Text(
                "Slab Thickness: ${"%.2f".format(thickness)} m",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = thickness,
                onValueChange = { thickness = it },
                valueRange = 0.01f..0.20f
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Orbit the scene to compare the 3 facing modes:\n" +
                    "• Single-sided — vanishes from behind\n" +
                    "• Double-sided — readable from both sides\n" +
                    "• Mirror — always faces you",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            materialLoader = materialLoader,
            cameraNode = cameraNode,
            // Capture the camera world position each frame so the billboard label can
            // turn toward it. `firstFrame.onFrame` is chained so the loading scrim still
            // clears on the first presented frame.
            onFrame = { frameTimeNanos ->
                cameraWorldPos = cameraNode.worldPosition
                firstFrame.onFrame(frameTimeNanos)
            },
            // Dolly the camera closer — 3 stacked 0.18 m-tall labels at the origin read
            // cramped at the default z = 4. z = 1.5 fills the portrait viewport while
            // keeping enough vertical room for the top label (#1470: z = 1.2 framed the
            // top label past the top viewport edge at rest).
            cameraManipulator = rememberCameraManipulator(
                orbitHomePosition = Position(0f, 0f, 1.5f),
                targetPosition = Position(0f, 0f, 0f),
            ),
        ) {
            // Half the slab depth — the text quad is pushed this far in +Z so it sits
            // flush on the slab's front face for every thickness value.
            val frontOffset = thickness / 2f + 0.001f

            // ── Label 1 (top): SINGLE-SIDED ───────────────────────────────────────────
            // No cameraPositionProvider → the quad is static. setDoubleSided(false)
            // (the TextNode default) means the back face is culled, so when the camera
            // orbits behind this label it disappears completely — like a one-sided plane.
            CubeNode(
                materialInstance = slabMaterialSingle,
                size = Float3(0.66f, 0.18f, thickness),
                position = Position(x = 0f, y = 0.30f, z = 0f),
            )
            TextNode(
                text = inputText,
                fontSize = fontSize,
                textColor = android.graphics.Color.WHITE,
                backgroundColor = 0xCC000000.toInt(),
                widthMeters = 0.62f,
                heightMeters = 0.16f,
                position = Position(x = 0f, y = 0.30f, z = frontOffset),
                apply = { materialInstance.setDoubleSided(false) },
            )

            // ── Label 2 (center): DOUBLE-SIDED ────────────────────────────────────────
            // setDoubleSided(true) keeps the back face drawn, so the text remains visible
            // (mirror-reversed) when the camera orbits behind it — see #1426.
            CubeNode(
                materialInstance = slabMaterialDouble,
                size = Float3(0.66f, 0.18f, thickness),
                position = Position(x = 0f, y = 0f, z = 0f),
            )
            TextNode(
                text = "Double-sided",
                fontSize = fontSize,
                textColor = 0xFFA4C1FF.toInt(),  // SceneView TintLight (readable on dark)
                backgroundColor = 0xCC161B22.toInt(),  // SceneView SurfaceDim
                widthMeters = 0.62f,
                heightMeters = 0.16f,
                position = Position(x = 0f, y = 0f, z = frontOffset),
                apply = { materialInstance.setDoubleSided(true) },
            )

            // ── Label 3 (bottom): MIRROR / BILLBOARD ──────────────────────────────────
            // cameraPositionProvider is wired → the TextNode rotates to face the camera
            // every frame, so the text always reads upright in the correct orientation
            // no matter how far the scene is orbited.
            CubeNode(
                materialInstance = slabMaterialMirror,
                size = Float3(0.66f, 0.18f, thickness),
                position = Position(x = 0f, y = -0.30f, z = 0f),
            )
            TextNode(
                text = "Mirror billboard",
                fontSize = fontSize,
                textColor = 0xFFD2A8FF.toInt(),  // SceneView TintSoft (purple tint)
                backgroundColor = 0xCC161B22.toInt(),  // SceneView SurfaceDim
                widthMeters = 0.62f,
                heightMeters = 0.16f,
                position = Position(x = 0f, y = -0.30f, z = frontOffset),
                cameraPositionProvider = { cameraWorldPos },
                apply = { materialInstance.setDoubleSided(true) },
            )
        }
    }
}
