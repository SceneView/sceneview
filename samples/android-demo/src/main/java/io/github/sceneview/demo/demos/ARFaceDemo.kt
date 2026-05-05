package io.github.sceneview.demo.demos

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.ar.core.AugmentedFace
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.SceneViewColors
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader

/**
 * Augmented face mesh tracking demo.
 *
 * Configures the AR session with the front camera and [Config.AugmentedFaceMode.MESH3D] to
 * detect face meshes. When a face is detected, an [AugmentedFaceNode] renders a semi-transparent
 * colored mesh overlay on the user's face.
 *
 * Requires a device with a front-facing camera and ARCore face mesh support.
 */
@Composable
fun ARFaceDemo(onBack: () -> Unit) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)

    var detectedFaces by remember { mutableStateOf<List<AugmentedFace>>(emptyList()) }
    var faceCount by remember { mutableStateOf(0) }

    // Strongly visible material for the face mesh overlay.
    //
    // The previous version used `PrimaryOverlay` (alpha 0.4) on the lit `transparent_colored`
    // material. With the front camera, ARCore disables light estimation
    // ([ArSession.configure]) so the scene falls back to the default IBL — combined with
    // a 0.4-alpha PBR pass, the rendered mesh was effectively invisible on Pixel 9
    // even though tracking reported "Tracking 1 face(s)".
    //
    // Bumping alpha to 0.85 + a saturated brand color makes the mesh read clearly under
    // any lighting / IBL while still letting the camera feed show through enough to
    // confirm it follows facial features.
    val faceMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(
            color = SceneViewColors.Primary.copy(alpha = 0.85f),
            metallic = 0.0f,
            roughness = 0.6f,
            reflectance = 0.1f
        )
    }

    DemoScaffold(
        title = "Face Mesh",
        onBack = onBack
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ARSceneView(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                materialLoader = materialLoader,
                planeRenderer = false,
                sessionFeatures = setOf(Session.Feature.FRONT_CAMERA),
                // Counter the washed-out / over-exposed selfie-camera output reported on
                // Pixel 9 — the front camera's auto-exposure runs hotter than Camera2's
                // baseline, producing a pale image when ARCore's defaults are kept.
                // Negative EV darkens the preview to match natural skin tones again.
                cameraExposure = -1.5f,
                sessionConfiguration = { _: Session, config: Config ->
                    config.augmentedFaceMode = Config.AugmentedFaceMode.MESH3D
                    config.planeFindingMode = Config.PlaneFindingMode.DISABLED
                },
                onSessionUpdated = { session: Session, _: Frame ->
                    detectedFaces = session.getAllTrackables(AugmentedFace::class.java)
                        .filter { it.trackingState == TrackingState.TRACKING }
                    faceCount = detectedFaces.size
                }
            ) {
                detectedFaces.forEach { face ->
                    AugmentedFaceNode(
                        augmentedFace = face,
                        meshMaterialInstance = faceMaterial,
                        onTrackingStateChanged = { state ->
                            // Face tracking state changed
                        }
                    )
                }
            }

            // Status overlay
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Text(
                    text = if (faceCount > 0) {
                        "Tracking $faceCount face(s)"
                    } else {
                        "Point the front camera at a face"
                    },
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
