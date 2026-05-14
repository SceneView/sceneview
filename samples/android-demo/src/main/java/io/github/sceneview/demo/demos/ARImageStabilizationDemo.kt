package io.github.sceneview.demo.demos

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import io.github.sceneview.demo.R
import io.github.sceneview.math.Position
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener

/**
 * AR Electronic Image Stabilization (EIS) demo — toggle ARCore's hardware-assisted camera
 * stabilization and feel the difference under hand-shake.
 *
 * EIS is a single ARCore [Config] flag added in ARCore 1.37+. When enabled, ARCore smooths
 * the camera background image so that small hand jitter doesn't translate into perceived
 * judder of the AR scene. The virtual content stays anchored at the same world pose either
 * way — it's the *camera image* (and therefore the perceived overall motion) that is
 * stabilized. Useful for handheld AR, panoramic captures, or any video-style recording where
 * micro-shake is distracting.
 *
 * SceneView's `arsceneview/` library doesn't ship a wrapper because none is needed: the
 * effect is one line in `sessionConfiguration` (`config.imageStabilizationMode = …`). This
 * demo wires it up end-to-end so the toggle is discoverable in-app.
 *
 * Wiring summary:
 * 1. `sessionConfiguration` queries [Session.isImageStabilizationModeSupported] to detect
 *    whether the device supports [Config.ImageStabilizationMode.EIS]. Some devices report
 *    `false` — the toggle is disabled and a banner explains why.
 * 2. When supported and the user toggle is ON, [Config.setImageStabilizationMode] is called
 *    with `EIS`; otherwise `OFF`. ARCore re-applies this every session reconfigure.
 * 3. The whole [ARSceneView] is wrapped in `key(eisOn)`. ARCore *accepts* runtime
 *    [Session.configure] calls for EIS, but the camera background stream can briefly stutter
 *    while the stabilization buffers re-prime. Re-keying the scene is the boring-and-correct
 *    path: tear down the engine + session and rebuild with the new config. The cost (one
 *    engine restart per toggle) is paid only on user interaction and matches a cold launch.
 * 4. Tap-to-place a single helmet GLB on a detected plane, mirroring [ARDepthOcclusionDemo].
 *    The fixed reference object is what makes the stabilization difference visible: the
 *    model stays glued to its world pose while the camera image around it goes from shaky
 *    (EIS OFF) to smooth (EIS ON).
 */
@Composable
fun ARImageStabilizationDemo(onBack: () -> Unit) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)

    // Hoisted so the model loads once for the whole demo, not on every re-placement.
    // The remember slot survives anchor clears + re-drops, so re-tapping is instant
    // instead of paying the ~200 ms GLB parse hitch each time.
    val helmetInstance = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")

    // User toggle. Off by default so the user can flip it ON and *feel* the difference —
    // starting OFF is the more instructive cold-start state.
    var eisEnabled by remember { mutableStateOf(false) }

    // `null` = "we haven't yet been told whether the device supports EIS". Once the first
    // session reports `isImageStabilizationModeSupported`, we update [eisSupported] so the
    // controls drawer can disable the switch and surface a banner.
    var eisSupported by remember { mutableStateOf<Boolean?>(null) }

    var placedAnchor by remember { mutableStateOf<Anchor?>(null) }
    var trackingFailureReason by remember { mutableStateOf<TrackingFailureReason?>(null) }
    var isTracking by remember { mutableStateOf(false) }

    // Latest Frame for hit testing in the gesture callback.
    var latestFrame by remember { mutableStateOf<Frame?>(null) }

    // The toggle ultimately drives ARCore's session config. Snapshot the value so the keyed
    // scope below sees a stable boolean. We force-OFF when the device doesn't support EIS
    // so the status pill reflects what ARCore actually applies.
    val eisOn = eisEnabled && (eisSupported != false)

    DemoScaffold(
        title = stringResource(R.string.demo_ar_image_stabilization_title),
        onBack = onBack,
        controls = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = "How to test",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp)
                )
                Text(
                    text = "1. Toggle EIS on.\n" +
                        "2. Move the phone around — notice the camera feed is much smoother.\n" +
                        "3. Toggle off — judder returns.\n" +
                        "4. The virtual model stays anchored either way; only the camera " +
                        "background is stabilized.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }

            if (eisSupported == false) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Your device doesn't support Electronic Image " +
                            "Stabilization. Toggle has no effect.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Image Stabilization (EIS)",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = eisEnabled,
                    enabled = eisSupported != false,
                    onCheckedChange = { eisEnabled = it }
                )
            }

            OutlinedButton(
                onClick = {
                    placedAnchor?.let { runCatching { it.detach() } }
                    placedAnchor = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text("Clear")
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Re-keying the whole scene on toggle is the boring-and-correct strategy:
            // ARCore accepts runtime `Session.configure` calls to flip EIS, but the camera
            // background stream can briefly stutter while the stabilization buffers
            // re-prime. Tearing down + rebuilding ARSceneView matches a cold launch and
            // keeps the EIS-on / EIS-off paths identical.
            key(eisOn) {
                ARSceneView(
                    modifier = Modifier.fillMaxSize(),
                    engine = engine,
                    modelLoader = modelLoader,
                    materialLoader = materialLoader,
                    planeRenderer = true,
                    sessionConfiguration = { session: Session, config: Config ->
                        config.planeFindingMode =
                            Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                        config.lightEstimationMode =
                            Config.LightEstimationMode.ENVIRONMENTAL_HDR

                        // Probe device support exactly once per session reconfigure.
                        // Requesting EIS on an unsupported device would throw, so we gate
                        // on the capability check first and surface the result so the UI
                        // can disable the toggle + show the unsupported banner.
                        val supported = session.isImageStabilizationModeSupported(
                            Config.ImageStabilizationMode.EIS
                        )
                        eisSupported = supported

                        config.imageStabilizationMode = if (eisOn && supported) {
                            Config.ImageStabilizationMode.EIS
                        } else {
                            Config.ImageStabilizationMode.OFF
                        }
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
                            if (node != null) return@rememberOnGestureListener

                            val frame = latestFrame ?: return@rememberOnGestureListener
                            if (frame.camera.trackingState != TrackingState.TRACKING) {
                                return@rememberOnGestureListener
                            }

                            val hit = frame.hitTest(event).firstOrNull { result ->
                                val trackable = result.trackable
                                trackable is Plane &&
                                    trackable.isPoseInPolygon(result.hitPose) &&
                                    result.distance <= 5.0f
                            }
                            if (hit != null) {
                                // Replace any previous placement — single-model demo so the
                                // user has one fixed reference to walk around for testing.
                                placedAnchor?.let { runCatching { it.detach() } }
                                placedAnchor = hit.createAnchor()
                            }
                        }
                    )
                ) {
                    // Wait for both the anchor AND the hoisted model instance — the latter
                    // can still be null on the very first frame while the GLB loads in the
                    // background. After that load, the instance is reused across every
                    // re-placement (anchor clear + re-drop) without reloading.
                    placedAnchor?.let { anchor ->
                        helmetInstance?.let { instance ->
                            AnchorNode(anchor = anchor) {
                                ModelNode(
                                    modelInstance = instance,
                                    scaleToUnits = 0.3f,
                                    centerOrigin = Position(0.0f, 0.0f, 0.0f)
                                )
                            }
                        }
                    }
                }
            }

            // Top-center status pill — green tint when EIS is ON, red tint when OFF.
            // Big enough for a screenshot to read at a glance.
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
                color = if (eisOn) {
                    Color(0xFF1B5E20).copy(alpha = 0.85f)
                } else {
                    Color(0xFFB71C1C).copy(alpha = 0.85f)
                },
                contentColor = Color.White,
                tonalElevation = 4.dp,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = if (eisOn) "EIS ON" else "EIS OFF",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            // Scanning / tracking-failure overlay — same vocabulary as ARPlacementDemo and
            // ARDepthOcclusionDemo so all three AR demos share the same visual language.
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
