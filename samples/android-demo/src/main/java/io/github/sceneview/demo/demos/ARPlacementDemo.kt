package io.github.sceneview.demo.demos

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
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
import io.github.sceneview.rememberOnGestureListener

/**
 * Interactive AR tap-to-place demo.
 *
 * Plane detection is rendered as a translucent overlay. Each tap on a detected horizontal or
 * vertical plane spawns a NEW [ModelNode] instance attached to its own
 * [AnchorNode][io.github.sceneview.ar.node.AnchorNode]. The model cycles through a small curated
 * list of bundled GLBs so the user can sample the SDK's variety: helmet, avocado, fox, lantern,
 * boom-box-style toy car, and shiba.
 *
 * Each placed model is **editable** — `isEditable = true` on the [ModelNode] enables
 * pinch-to-scale, two-finger rotate, and one-finger drag. Because the parent [AnchorNode] is
 * locked to its ARCore [Anchor] pose, the editable child node transforms relative to the anchor:
 * the anchor stays glued to the plane while the user manipulates the model on top of it.
 *
 * Top-center pill shows the live "X models placed" count. The "Clear All" control wipes every
 * placed model and detaches the underlying ARCore anchors.
 */

private data class PlacedModel(
    val id: Int,
    val anchor: Anchor,
    val assetPath: String,
    val displayName: String
)

private data class CycleEntry(val assetPath: String, val displayName: String)

// Curated list of bundled GLBs that look good as small AR objects on a plane.
// Each has a distinct silhouette and material so the cycle visibly rotates through variety.
private val MODEL_CYCLE = listOf(
    CycleEntry("models/khronos_damaged_helmet.glb", "Damaged Helmet"),
    CycleEntry("models/khronos_avocado.glb", "Avocado"),
    CycleEntry("models/khronos_fox.glb", "Fox"),
    CycleEntry("models/khronos_lantern.glb", "Lantern"),
    CycleEntry("models/khronos_toy_car.glb", "Toy Car"),
    CycleEntry("models/shiba.glb", "Shiba")
)

@Composable
fun ARPlacementDemo(onBack: () -> Unit) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)

    val placedModels = remember { mutableStateListOf<PlacedModel>() }
    var nextId by remember { mutableStateOf(0) }
    var cycleIndex by remember { mutableStateOf(0) }

    var trackingFailureReason by remember { mutableStateOf<TrackingFailureReason?>(null) }
    var isTracking by remember { mutableStateOf(false) }

    // Keep a reference to the latest Frame for hit testing in the gesture callback.
    var latestFrame by remember { mutableStateOf<Frame?>(null) }

    // Active-gesture label (shown while the user is mid-manipulating a placed
    // model). `null` ⇒ no overlay. Mirrors the GestureEditingDemo pattern so
    // both demos share the same visual language for "what is my touch doing
    // right now". Drag → "Moving", twist → "Rotating", pinch → "Scaling".
    var gestureMode by remember { mutableStateOf<String?>(null) }

    DemoScaffold(
        title = "Tap to Place",
        onBack = onBack,
        controls = {
            Text(
                text = "Tap a detected plane to drop a model. Each model is editable: drag to " +
                    "translate, pinch to scale, twist to rotate — the active gesture is shown " +
                    "in the top-center pill. Models cycle on each tap.",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedButton(
                onClick = {
                    // Detach every ARCore anchor so the session stops tracking them, then drop
                    // the Compose state — recomposition removes the AnchorNodes from the graph.
                    placedModels.forEach { runCatching { it.anchor.detach() } }
                    placedModels.clear()
                    cycleIndex = 0
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text("Clear All")
            }

            // Up-next preview so the user knows what the next tap will spawn.
            val nextEntry = MODEL_CYCLE[cycleIndex % MODEL_CYCLE.size]
            Text(
                text = "Next tap places: ${nextEntry.displayName}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ARSceneView(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                materialLoader = materialLoader,
                planeRenderer = true,
                // Pixel 9 review feedback: the rear-camera preview was washed out on
                // this device — ARCore's auto-exposure under ENVIRONMENTAL_HDR
                // overshoots Camera2's baseline. Apply a -1 EV bias for realistic
                // tones, matching ar-pose / ar-streetscape.
                cameraExposure = -1.0f,
                sessionConfiguration = { _: Session, config: Config ->
                    config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                },
                onSessionUpdated = { _, frame: Frame ->
                    latestFrame = frame
                    isTracking = frame.camera.trackingState == TrackingState.TRACKING
                },
                onTrackingFailureChanged = { reason ->
                    trackingFailureReason = reason
                },
                onGestureListener = rememberOnGestureListener(
                    onSingleTapConfirmed = { event: MotionEvent, node ->
                        // If the tap landed on an existing editable ModelNode, the gesture
                        // system handles it (drag/scale/rotate). Don't spawn a new model on top.
                        if (node != null) return@rememberOnGestureListener

                        val frame = latestFrame ?: return@rememberOnGestureListener
                        if (frame.camera.trackingState != TrackingState.TRACKING) {
                            return@rememberOnGestureListener
                        }

                        // Perform an ARCore hit test at the tap coordinates.
                        val hitResults = frame.hitTest(event)
                        val hit = hitResults.firstOrNull { result ->
                            val trackable = result.trackable
                            trackable is Plane &&
                                trackable.isPoseInPolygon(result.hitPose) &&
                                result.distance <= 5.0f // limit placement distance
                        }
                        if (hit != null) {
                            val entry = MODEL_CYCLE[cycleIndex % MODEL_CYCLE.size]
                            placedModels.add(
                                PlacedModel(
                                    id = nextId++,
                                    anchor = hit.createAnchor(),
                                    assetPath = entry.assetPath,
                                    displayName = entry.displayName
                                )
                            )
                            cycleIndex = (cycleIndex + 1) % MODEL_CYCLE.size
                        }
                    },
                    // Pixel 9 review v2: surface which gesture is active so the user
                    // can tell drag-to-move from twist-to-rotate from pinch-to-scale.
                    // `node != null` ⇒ gesture is targeting an editable ModelNode
                    // (placed model). `node == null` ⇒ the touch fell through to the
                    // background; AR has no orbit camera so we skip the indicator.
                    onMoveBegin = { _, _, node ->
                        if (node != null) gestureMode = "Moving"
                    },
                    onMoveEnd = { _, _, _ -> gestureMode = null },
                    onRotateBegin = { _, _, node ->
                        if (node != null) gestureMode = "Rotating"
                    },
                    onRotateEnd = { _, _, _ -> gestureMode = null },
                    onScaleBegin = { _, _, node ->
                        if (node != null) gestureMode = "Scaling"
                    },
                    onScaleEnd = { _, _, _ -> gestureMode = null }
                )
            ) {
                // One AnchorNode + ModelNode per placement. Wrapping each in `key(id)` gives
                // every placement its own remember slot, so the rememberModelInstance call
                // inside loads a fresh, independent ModelInstance per anchor (Filament instances
                // can only live in one transform at a time, so we cannot share them).
                placedModels.forEach { placed ->
                    key(placed.id) {
                        AnchorNode(anchor = placed.anchor) {
                            val instance = rememberModelInstance(modelLoader, placed.assetPath)
                            instance?.let {
                                ModelNode(
                                    modelInstance = it,
                                    scaleToUnits = 0.3f,
                                    centerOrigin = Position(0.0f, 0.0f, 0.0f),
                                    isEditable = true
                                )
                            }
                        }
                    }
                }
            }

            // Top-center pill: live count of placed models. Mirrors the GestureEditingDemo
            // "Editing: …" Surface pattern so the two AR/3D demos share an overlay style.
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
                color = Color.Black.copy(alpha = 0.7f),
                contentColor = Color.White,
                tonalElevation = 4.dp,
                shape = MaterialTheme.shapes.small
            ) {
                val count = placedModels.size
                Text(
                    text = if (count == 1) "1 model placed" else "$count models placed",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            // Active-gesture indicator. Sits below the count pill, only visible
            // while the user is actively manipulating a placed model. Uses the
            // primary tonal color (vs. the count pill's neutral black) so the
            // two overlays stay visually distinct even when stacked.
            AnimatedVisibility(
                visible = gestureMode != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    tonalElevation = 4.dp,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = gestureMode ?: "",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // Scanning indicator overlay
            AnimatedVisibility(
                visible = !isTracking,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    modifier = Modifier.padding(bottom = 32.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        text = trackingFailureReason?.let { reason ->
                            when (reason) {
                                TrackingFailureReason.NONE -> "Point your camera at a surface"
                                TrackingFailureReason.BAD_STATE -> "AR session error"
                                TrackingFailureReason.INSUFFICIENT_LIGHT -> "Not enough light"
                                TrackingFailureReason.EXCESSIVE_MOTION -> "Moving too fast"
                                TrackingFailureReason.INSUFFICIENT_FEATURES ->
                                    "Not enough detail — try a textured surface"
                                TrackingFailureReason.CAMERA_UNAVAILABLE -> "Camera unavailable"
                            }
                        } ?: "Scanning for surfaces…",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

