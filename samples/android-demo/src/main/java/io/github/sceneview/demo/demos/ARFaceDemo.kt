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
import androidx.compose.ui.unit.dp
import com.google.android.filament.LightManager
import com.google.ar.core.AugmentedFace
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.SceneViewColors
import io.github.sceneview.node.LightNode
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
    // History: the demo previously used `transparent_colored` at alpha 0.4, then 0.85 —
    // both rendered as effectively invisible on Pixel 9 even though tracking reported
    // "Tracking 1 face(s)" and `35e5990d` fixed the underlying tangent-buffer bug. Two
    // factors compounded:
    //   1. ARCore disables `ENVIRONMENTAL_HDR` light estimation when the front camera
    //      is in use, so the lit-PBR shading falls back to the AR scene's neutral IBL,
    //      which is dim. With low alpha + lit + dim IBL the final rgba ≈ near-zero.
    //   2. The transparent_colored material draws back-to-front order alpha-blended
    //      with the camera feed; a face mesh is dense enough that self-occluded
    //      transparent fragments collapse to the same pixel and look uniform.
    //
    // Switch to fully **opaque** alpha (1.0) — that routes through
    // `opaque_colored.mat` which writes depth, blends additively over the camera
    // feed, and removes the alpha-stacking failure mode entirely. Pure-diffuse PBR
    // (metallic 0, roughness 1, reflectance 0) keeps the colour readable even under
    // a dim IBL because there's no specular path to drown it out. Combined with the
    // explicit fill light declared inside the scene, the mesh now reads as a solid
    // SceneView-blue overlay tracking the user's face.
    val faceMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(
            color = SceneViewColors.Primary.copy(alpha = 1.0f),
            metallic = 0.0f,
            roughness = 1.0f,
            reflectance = 0.0f
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
                // Explicit directional fill light. ARCore disables `ENVIRONMENTAL_HDR`
                // light estimation when the front camera is in use, leaving the AR
                // scene with only its dim neutral IBL — which is what made the face
                // mesh look invisible in earlier review rounds even after the
                // tangent-quaternion fix in `35e5990d`. A 100 000-lux key light
                // pointing slightly down-and-forward from above the user lights the
                // mesh against the camera feed without blowing out highlights.
                LightNode(
                    type = LightManager.Type.DIRECTIONAL,
                    intensity = 100_000f,
                    direction = io.github.sceneview.math.Direction(x = 0f, y = -1f, z = -0.5f),
                )
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
