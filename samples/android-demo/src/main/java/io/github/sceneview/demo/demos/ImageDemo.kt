package io.github.sceneview.demo.demos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
 * Demonstrates [ImageNode] — a flat, textured plane positioned in 3D space.
 *
 * Rather than a single logo floating in the void (which reads as a flat 2D
 * sticker), the demo stages a small **photo gallery**: three framed pictures
 * hung at different depths and angles, like a wall display. The depth offset,
 * the perspective foreshortening as the camera orbits, and the framed
 * thumbnails together make it obvious the images are real 3D planes — not a
 * HUD overlay. A scale slider grows / shrinks the whole gallery in unison.
 */
@Composable
fun ImageDemo(onBack: () -> Unit) {
    var scaleFactor by remember { mutableFloatStateOf(1f) }

    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)

    // Three distinct photographic-style bitmaps generated once. Procedural
    // rather than bundled JPEGs so the demo ships no extra binary assets and
    // still reads as real photos (sky gradient, sun, layered hills).
    val sunsetPhoto = remember { createScenePhoto(Scene.SUNSET) }
    val noonPhoto = remember { createScenePhoto(Scene.NOON) }
    val nightPhoto = remember { createScenePhoto(Scene.NIGHT) }

    val firstFrame = rememberFirstFrameState()

    DemoScaffold(
        title = stringResource(R.string.demo_image_title),
        onBack = onBack,
        firstFrameRendered = firstFrame.rendered,
        controls = {
            Text(
                "Gallery scale: ${"%.1f".format(scaleFactor)}x",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = scaleFactor,
                onValueChange = { scaleFactor = it },
                valueRange = 0.4f..2f
            )
        }
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            onFrame = firstFrame.onFrame,
            engine = engine,
            materialLoader = materialLoader,
            cameraManipulator = rememberCameraManipulator()
        ) {
            // Centre picture — hung flat, facing the camera.
            ImageNode(
                bitmap = noonPhoto,
                position = Position(x = 0f, y = 0.1f, z = 0f),
                scale = Scale(0.8f * scaleFactor)
            )
            // Left picture — pushed back and angled inward so its perspective
            // foreshortening is visible from the default camera. Orbiting the
            // scene swings it through a clear depth arc.
            ImageNode(
                bitmap = sunsetPhoto,
                position = Position(x = -0.85f, y = -0.05f, z = -0.45f),
                rotation = Rotation(y = 32f),
                scale = Scale(0.62f * scaleFactor)
            )
            // Right picture — also recessed and angled the opposite way.
            ImageNode(
                bitmap = nightPhoto,
                position = Position(x = 0.85f, y = -0.05f, z = -0.45f),
                rotation = Rotation(y = -32f),
                scale = Scale(0.62f * scaleFactor)
            )
        }
    }
}

/** Time-of-day variants for the generated photo. */
private enum class Scene(
    val skyTop: Int,
    val skyBottom: Int,
    val sun: Int,
    val sunY: Float,
    val hillFront: Int,
    val hillBack: Int,
) {
    SUNSET(0xFF1B2A52.toInt(), 0xFFF4A259.toInt(), 0xFFFFE3A3.toInt(), 0.62f, 0xFF1E2A38.toInt(), 0xFF3A4A63.toInt()),
    NOON(0xFF2F6FB8.toInt(), 0xFFBFE3F5.toInt(), 0xFFFFFFFF.toInt(), 0.30f, 0xFF2F5D3A.toInt(), 0xFF4C8455.toInt()),
    NIGHT(0xFF05060F.toInt(), 0xFF1E2747.toInt(), 0xFFEDEFF7.toInt(), 0.26f, 0xFF0A1019.toInt(), 0xFF1A2436.toInt()),
}

/**
 * Renders a small photographic-looking landscape (gradient sky, sun/moon glow,
 * two layered hill silhouettes) inside a neutral photo frame, so an
 * [ImageNode] reads as a hung picture rather than an icon.
 */
private fun createScenePhoto(scene: Scene): Bitmap {
    val w = 512
    val h = 384
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Frame border.
    val frameInset = 18f
    canvas.drawColor(0xFFEAE6DE.toInt())

    val photo = RectF(frameInset, frameInset, w - frameInset, h - frameInset)
    canvas.save()
    canvas.clipRect(photo)

    // Sky gradient.
    val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, photo.top, 0f, photo.bottom,
            scene.skyTop, scene.skyBottom, Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(photo, skyPaint)

    // Sun / moon glow.
    val sunX = photo.left + photo.width() * 0.68f
    val sunY = photo.top + photo.height() * scene.sunY
    val sunRadius = photo.width() * 0.16f
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            sunX, sunY, sunRadius * 2.4f,
            intArrayOf(scene.sun, scene.sun and 0x00FFFFFF),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
    }
    canvas.drawCircle(sunX, sunY, sunRadius * 2.4f, glowPaint)
    canvas.drawCircle(sunX, sunY, sunRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = scene.sun
    })

    // Back hill silhouette.
    canvas.drawPath(hillPath(photo, 0.62f, 0.10f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = scene.hillBack
    })
    // Front hill silhouette.
    canvas.drawPath(hillPath(photo, 0.78f, 0.16f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = scene.hillFront
    })

    canvas.restore()

    // Thin inner mat line so the frame reads as a real picture frame.
    canvas.drawRect(photo, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0x33000000
    })

    return bitmap
}

/**
 * Builds a rolling-hill silhouette filling the lower part of [photo].
 *
 * @param baseY    Hill crest height as a fraction of the photo (0 = top).
 * @param amplitude Crest undulation as a fraction of the photo height.
 */
private fun hillPath(photo: RectF, baseY: Float, amplitude: Float): Path {
    val crest = photo.top + photo.height() * baseY
    val amp = photo.height() * amplitude
    return Path().apply {
        moveTo(photo.left, photo.bottom)
        lineTo(photo.left, crest)
        cubicTo(
            photo.left + photo.width() * 0.30f, crest - amp,
            photo.left + photo.width() * 0.55f, crest + amp,
            photo.left + photo.width() * 0.78f, crest - amp * 0.4f
        )
        cubicTo(
            photo.left + photo.width() * 0.90f, crest - amp * 0.9f,
            photo.right, crest,
            photo.right, crest
        )
        lineTo(photo.right, photo.bottom)
        close()
    }
}
