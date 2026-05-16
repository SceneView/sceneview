package io.github.sceneview.demo.demos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader

/**
 * Demonstrates [BillboardNode] (always faces the camera, like a map pin or
 * AR label) vs a regular [ImageNode] (fixed in world space).
 *
 * The two are staged on a shared "ground" plane as if they were signs planted
 * in a scene: a **billboard label** that stays squarely readable from every
 * angle, and a **fixed signboard** that rotates with the world and turns
 * edge-on — nearly invisible — as the user orbits past it. Orbiting the camera
 * is what makes the difference obvious, so the demo invites it explicitly.
 */
@Composable
fun BillboardDemo(onBack: () -> Unit) {
    var showBillboard by remember { mutableStateOf(true) }
    var showFixed by remember { mutableStateOf(true) }

    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)

    // A neutral "ground" so the two signs read as planted in a scene rather
    // than floating in the void — sells the 3D staging.
    val groundBitmap = remember { createGroundBitmap() }
    // Billboard label — pin-style, with a "FACING YOU" caption that stays true
    // because the node always rotates to face the camera.
    val billboardBitmap = remember {
        createSignBitmap("Billboard", "always faces you", 0xFF005BC1.toInt())
    }
    // Fixed signboard — caption warns it turns edge-on as you orbit.
    val fixedBitmap = remember {
        createSignBitmap("Fixed sign", "turns away as you orbit", 0xFF6446CD.toInt())
    }

    val firstFrame = rememberFirstFrameState()

    DemoScaffold(
        title = stringResource(R.string.demo_billboard_title),
        onBack = onBack,
        firstFrameRendered = firstFrame.rendered,
        controls = {
            Text(
                "Orbit the scene — the billboard stays readable, the fixed sign turns edge-on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Visible Nodes", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    showBillboard,
                    onClick = { showBillboard = !showBillboard },
                    // Distinct from the AppBar title "Billboard" so test
                    // matchers and screen readers can disambiguate (#1040).
                    label = { Text("Billboard Panel") }
                )
                FilterChip(
                    showFixed,
                    onClick = { showFixed = !showFixed },
                    label = { Text("Fixed Image") }
                )
            }
        }
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            onFrame = firstFrame.onFrame,
            engine = engine,
            materialLoader = materialLoader,
            // Slightly raised, angled camera so the ground plane reads as a
            // floor receding into the scene — sells the "two planted signs"
            // staging instead of two panels on a flat backdrop.
            cameraManipulator = rememberCameraManipulator(
                orbitHomePosition = Position(0f, 0.55f, 1.5f),
                targetPosition = Position(0f, -0.05f, -0.8f),
            )
        ) {
            // Ground plane the two signs are "planted" on. Laid flat (rotated
            // -90° about X) and pushed down so the signs rise out of it.
            ImageNode(
                bitmap = groundBitmap,
                position = Position(x = 0f, y = -0.42f, z = -0.8f),
                rotation = Rotation(x = -90f),
                scale = Scale(2.4f)
            )

            // BillboardNode: always faces the camera. As the user orbits, this
            // stays square-on and fully legible — the "FACING YOU" caption
            // never lies.
            if (showBillboard) {
                BillboardNode(
                    bitmap = billboardBitmap,
                    widthMeters = 0.62f,
                    heightMeters = 0.40f,
                    position = Position(x = -0.5f, y = -0.05f, z = -0.8f)
                )
            }

            // Regular ImageNode: fixed in world space. Orbiting the camera
            // swings it edge-on so it nearly vanishes — the visible contrast
            // with the billboard.
            if (showFixed) {
                ImageNode(
                    bitmap = fixedBitmap,
                    position = Position(x = 0.5f, y = -0.05f, z = -0.8f),
                    scale = Scale(0.62f)
                )
            }
        }
    }
}

/**
 * A pin-style sign bitmap: a rounded card with a bold [title], a smaller
 * [caption], and a short "post" beneath so it reads as planted signage.
 */
private fun createSignBitmap(title: String, caption: String, bgColor: Int): Bitmap {
    val width = 320
    val height = 200
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Sign post.
    val postPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6B5B4A.toInt() }
    canvas.drawRect(width / 2f - 8f, 150f, width / 2f + 8f, height.toFloat(), postPaint)

    // Sign card.
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
    canvas.drawRoundRect(RectF(12f, 12f, width - 12f, 156f), 20f, 20f, bgPaint)

    // Title.
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 44f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    canvas.drawText(title, width / 2f, 78f, titlePaint)

    // Caption.
    val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8E8F4.toInt()
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText(caption, width / 2f, 122f, captionPaint)

    return bitmap
}

/**
 * A subtle checkered "ground" bitmap so the signs read as planted in a scene
 * rather than floating in empty space.
 */
private fun createGroundBitmap(): Bitmap {
    val size = 256
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(0xFF2A2E3A.toInt())
    val tilePaint = Paint().apply { color = 0xFF353A4A.toInt() }
    val tiles = 8
    val tile = size / tiles
    for (row in 0 until tiles) {
        for (col in 0 until tiles) {
            if ((row + col) % 2 == 0) {
                canvas.drawRect(
                    (col * tile).toFloat(), (row * tile).toFloat(),
                    ((col + 1) * tile).toFloat(), ((row + 1) * tile).toFloat(),
                    tilePaint
                )
            }
        }
    }
    return bitmap
}
