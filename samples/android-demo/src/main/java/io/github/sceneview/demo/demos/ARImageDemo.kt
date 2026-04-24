package io.github.sceneview.demo.demos

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.ar.core.AugmentedImage
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.math.Position
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

/**
 * Augmented image tracking demo.
 *
 * Configures an [AugmentedImageDatabase] with a reference image loaded from assets.
 * When ARCore detects the image in the camera feed, an [AugmentedImageNode] is placed
 * at the image location with a 3D model attached.
 *
 * To test: print or display the image "augmented_images/qrcode.png" from the assets folder
 * and point the camera at it.
 */
@Composable
fun ARImageDemo(onBack: () -> Unit) {
    val context = LocalContext.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)

    val detectedImages = remember { mutableStateListOf<AugmentedImage>() }
    var isTracking by remember { mutableStateOf(false) }
    var trackingFailureReason by remember { mutableStateOf<TrackingFailureReason?>(null) }
    var imageCount by remember { mutableStateOf(0) }

    val modelInstance = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")

    // Decode the reference image once per composition — ARCore calls sessionConfiguration on
    // every session reconfig, so decoding inside that lambda re-opens the asset + allocates a
    // fresh Bitmap every time (the old bitmap is never recycled either).
    val referenceBitmap = remember(context) {
        context.assets.open("augmented_images/qrcode.png").use {
            BitmapFactory.decodeStream(it)
        }
    }

    DemoScaffold(
        title = "Augmented Image",
        onBack = onBack
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ARSceneView(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                materialLoader = materialLoader,
                planeRenderer = false,
                sessionConfiguration = { session: Session, config: Config ->
                    config.planeFindingMode = Config.PlaneFindingMode.DISABLED
                    config.focusMode = Config.FocusMode.AUTO
                    // Build an augmented image database with the pre-decoded reference bitmap.
                    config.augmentedImageDatabase =
                        AugmentedImageDatabase(session).apply {
                            addImage("reference", referenceBitmap, 0.15f) // 15 cm physical width
                        }
                },
                onSessionUpdated = { _: Session, frame: Frame ->
                    isTracking = frame.camera.trackingState == TrackingState.TRACKING
                    frame.getUpdatedTrackables(AugmentedImage::class.java).forEach { image ->
                        if (image.trackingState == TrackingState.TRACKING &&
                            image.trackingMethod == AugmentedImage.TrackingMethod.FULL_TRACKING
                        ) {
                            if (detectedImages.none { it.index == image.index }) {
                                detectedImages.add(image)
                            }
                        }
                    }
                    // Drop stopped images so stale AugmentedImageNodes don't linger in the scene.
                    detectedImages.removeAll { it.trackingState == TrackingState.STOPPED }
                    imageCount = detectedImages.size
                },
                onTrackingFailureChanged = { reason ->
                    trackingFailureReason = reason
                }
            ) {
                detectedImages.forEach { image ->
                    AugmentedImageNode(
                        augmentedImage = image,
                        applyImageScale = true
                    ) {
                        modelInstance?.let { instance ->
                            ModelNode(
                                modelInstance = instance,
                                scaleToUnits = 0.1f,
                                centerOrigin = Position(0f, 0f, 0f)
                            )
                        }
                    }
                }
            }

            // Status overlay
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val statusText = when {
                    imageCount > 0 -> "Tracking $imageCount image(s)"
                    !isTracking -> trackingFailureReason?.let { reason ->
                        when (reason) {
                            TrackingFailureReason.NONE -> "Point camera at reference image"
                            TrackingFailureReason.BAD_STATE -> "AR session error"
                            TrackingFailureReason.INSUFFICIENT_LIGHT -> "Not enough light"
                            TrackingFailureReason.EXCESSIVE_MOTION -> "Moving too fast"
                            TrackingFailureReason.INSUFFICIENT_FEATURES -> "Not enough detail"
                            TrackingFailureReason.CAMERA_UNAVAILABLE -> "Camera unavailable"
                        }
                    } ?: "Scanning for images\u2026"
                    else -> "Looking for reference image\u2026"
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(bottom = 32.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }
}
