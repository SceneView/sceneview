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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader

/**
 * Demonstrates BillboardNode (always faces the camera) vs a regular ImageNode
 * (stays fixed in world space). Toggle billboard mode on/off to compare.
 */
@Composable
fun BillboardDemo(onBack: () -> Unit) {
    var showBillboard by remember { mutableStateOf(true) }
    var showFixed by remember { mutableStateOf(true) }

    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)

    // Create simple colored bitmaps for the billboard and fixed image
    val billboardBitmap = remember {
        createLabelBitmap("Billboard", 0xFF005BC1.toInt())  // SceneView Primary
    }
    val fixedBitmap = remember {
        createLabelBitmap("Fixed", 0xFF6446CD.toInt())  // SceneView Accent
    }

    DemoScaffold(
        title = "Billboard Node",
        onBack = onBack,
        controls = {
            Text("Visible Nodes", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    showBillboard,
                    onClick = { showBillboard = !showBillboard },
                    label = { Text("Billboard") }
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
            engine = engine,
            materialLoader = materialLoader,
            cameraManipulator = rememberCameraManipulator()
        ) {
            // BillboardNode: always faces the camera
            if (showBillboard) {
                BillboardNode(
                    bitmap = billboardBitmap,
                    widthMeters = 0.3f,
                    heightMeters = 0.15f,
                    position = Position(x = -0.25f, y = 0f, z = -1.5f)
                )
            }

            // Regular ImageNode: stays fixed in world space
            if (showFixed) {
                ImageNode(
                    bitmap = fixedBitmap,
                    position = Position(x = 0.25f, y = 0f, z = -1.5f),
                    scale = Scale(0.3f)
                )
            }
        }
    }
}

/**
 * Creates a simple bitmap with a colored background and centered white text label.
 */
private fun createLabelBitmap(label: String, bgColor: Int): Bitmap {
    val width = 256
    val height = 128
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Background
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 16f, 16f, bgPaint)

    // Text
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val textY = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(label, width / 2f, textY, textPaint)

    return bitmap
}
