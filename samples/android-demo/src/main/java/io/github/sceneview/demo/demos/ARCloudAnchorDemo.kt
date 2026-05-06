package io.github.sceneview.demo.demos

import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.CloudAnchorNode as CloudAnchorNodeImpl
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.math.Position
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener

/**
 * Cloud anchor persistence demo.
 *
 * Demonstrates hosting and resolving ARCore Cloud Anchors for cross-device, persistent AR.
 * Tap on a detected plane to place an anchor, then host it to the cloud. Copy the cloud anchor
 * ID and resolve it on another device to see the same 3D content at the same location.
 *
 * Requires ARCore Cloud Anchor API to be enabled in Google Cloud Console.
 */
@Composable
fun ARCloudAnchorDemo(onBack: () -> Unit) {
    val context = LocalContext.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)

    var localAnchor by remember { mutableStateOf<Anchor?>(null) }
    var cloudAnchorId by remember { mutableStateOf<String?>(null) }
    var resolveId by remember { mutableStateOf("") }
    var isTracking by remember { mutableStateOf(false) }
    var trackingFailureReason by remember { mutableStateOf<TrackingFailureReason?>(null) }
    var hostedId by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf("Tap a surface to place an anchor") }
    var latestFrame by remember { mutableStateOf<Frame?>(null) }
    var arSession by remember { mutableStateOf<Session?>(null) }
    // Ref to the CloudAnchorNode created inside the ARSceneView content — needed so the
    // Host button can call node.host(session) to actually upload the anchor. Without this
    // the Host button just updated the status text and nothing hit the Cloud Anchor API.
    var cloudNode by remember { mutableStateOf<CloudAnchorNodeImpl?>(null) }

    val modelInstance = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")

    DemoScaffold(
        title = "Cloud Anchor",
        onBack = onBack,
        controls = {
            Text("Cloud Anchor Controls", style = MaterialTheme.typography.labelLarge)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val node = cloudNode
                        val session = arSession
                        when {
                            localAnchor == null -> Toast.makeText(
                                context, "Place an anchor first", Toast.LENGTH_SHORT
                            ).show()
                            node == null || session == null -> Toast.makeText(
                                context, "AR session not ready", Toast.LENGTH_SHORT
                            ).show()
                            else -> {
                                statusMessage = "Hosting anchor\u2026"
                                node.host(session)
                            }
                        }
                    },
                    enabled = localAnchor != null && hostedId == null
                ) {
                    Text("Host")
                }

                Button(
                    onClick = {
                        val session = arSession
                        if (resolveId.isBlank()) return@Button
                        if (session == null) {
                            Toast.makeText(
                                context, "AR session not ready", Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }
                        statusMessage = "Resolving $resolveId\u2026"
                        CloudAnchorNodeImpl.resolve(engine, session, resolveId) { state, node ->
                            if (state == Anchor.CloudAnchorState.SUCCESS && node != null) {
                                localAnchor = node.anchor
                                cloudAnchorId = resolveId
                                statusMessage = "Resolved $resolveId"
                            } else {
                                statusMessage = "Resolve failed: $state"
                            }
                        }
                    },
                    enabled = resolveId.isNotBlank()
                ) {
                    Text("Resolve")
                }
            }

            OutlinedTextField(
                value = resolveId,
                onValueChange = { resolveId = it },
                label = { Text("Cloud Anchor ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            hostedId?.let {
                Text(
                    text = "Hosted ID: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ARSceneView(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                materialLoader = materialLoader,
                planeRenderer = true,
                sessionConfiguration = { _: Session, config: Config ->
                    config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                    config.cloudAnchorMode = Config.CloudAnchorMode.ENABLED
                },
                onSessionCreated = { session ->
                    arSession = session
                },
                onSessionUpdated = { _, frame: Frame ->
                    latestFrame = frame
                    isTracking = frame.camera.trackingState == TrackingState.TRACKING
                },
                onTrackingFailureChanged = { reason ->
                    trackingFailureReason = reason
                },
                onGestureListener = rememberOnGestureListener(
                    onSingleTapConfirmed = { event: MotionEvent, _ ->
                        val frame = latestFrame ?: return@rememberOnGestureListener
                        if (frame.camera.trackingState != TrackingState.TRACKING) {
                            return@rememberOnGestureListener
                        }
                        if (localAnchor != null) return@rememberOnGestureListener

                        val hit = frame.hitTest(event).firstOrNull { result ->
                            val trackable = result.trackable
                            trackable is Plane &&
                                trackable.isPoseInPolygon(result.hitPose) &&
                                result.distance <= 5.0f
                        }
                        if (hit != null) {
                            localAnchor = hit.createAnchor()
                            statusMessage = "Anchor placed \u2014 tap Host to share"
                        }
                    }
                )
            ) {
                localAnchor?.let { anchor ->
                    CloudAnchorNode(
                        anchor = anchor,
                        cloudAnchorId = cloudAnchorId,
                        onHosted = { id, state ->
                            if (state == Anchor.CloudAnchorState.SUCCESS && id != null) {
                                hostedId = id
                                cloudAnchorId = id
                                statusMessage = "Hosted! ID: $id"
                            } else {
                                statusMessage = "Hosting failed: $state"
                            }
                        },
                        apply = { cloudNode = this },
                    ) {
                        modelInstance?.let { instance ->
                            ModelNode(
                                modelInstance = instance,
                                scaleToUnits = 0.3f,
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
                Text(
                    text = statusMessage,
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
