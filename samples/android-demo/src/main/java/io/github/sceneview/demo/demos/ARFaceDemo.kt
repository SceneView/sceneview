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
import io.github.sceneview.sample.rememberUnlitMaterialInstance

/**
 * Augmented face mesh tracking demo.
 *
 * Configures the AR session with the front camera and [Config.AugmentedFaceMode.MESH3D] to
 * detect face meshes. When a face is detected, an [AugmentedFaceNode] renders a translucent
 * unlit-blue mesh overlay on the user's face — alpha 0.4 so the user's actual facial features
 * remain visible underneath the fitted topology.
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

    // Unlit translucent overlay for the face mesh. ARCore disables `ENVIRONMENTAL_HDR`
    // light estimation when the front camera is in use, so any lit material falls
    // back to whatever neutral IBL is in scope — which historically rendered the
    // mesh near-invisible even after the tangent-quaternion fix in `35e5990d`.
    //
    // `createUnlitColorInstance` ships the mesh's flat colour in a single fragment
    // pass (no PBR shading, no IBL dependency), so the overlay reads as a clean
    // SceneView-blue tone regardless of front-camera lighting. No fill light needed —
    // the previous explicit 100 000 lux directional was a workaround for the lit
    // material that's now obsolete.
    //
    // [SceneViewColors.PrimaryOverlay] (alpha 0.4) routes through `transparent_unlit_colored.mat`
    // which is `doubleSided: true` — pairs with `culling(false)` on the face mesh.
    // We use a translucent overlay (not opaque blue) so the user can SEE their face
    // through the fitted topology — that's the entire point of a face-mesh demo.
    val faceMaterial = rememberUnlitMaterialInstance(materialLoader, SceneViewColors.PrimaryOverlay)

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
                // Unlit material — no fill light needed. The previous 100 000-lux
                // directional was a workaround for the lit-PBR material's IBL
                // dependency; the unlit path renders the mesh's flat colour
                // straight to the framebuffer regardless of scene lighting.
                detectedFaces.forEach { face ->
                    AugmentedFaceNode(
                        augmentedFace = face,
                        meshMaterialInstance = faceMaterial,
                        // Unlit material → no PBR sampling of TANGENTS, so skip the
                        // per-frame Mikkelsen compute + JNI upload (#878).
                        computeTangents = false,
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
