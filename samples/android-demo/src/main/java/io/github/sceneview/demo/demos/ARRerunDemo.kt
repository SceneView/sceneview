package io.github.sceneview.demo.demos

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.rerun.RerunBridge
import io.github.sceneview.ar.rerun.rememberRerunBridge
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.math.Position
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener
import kotlinx.coroutines.delay

private enum class ConnectionStatus { DISCONNECTED, CONNECTING, STREAMING }

/**
 * AR debug streaming to Rerun.io demo.
 *
 * Streams ARCore session data (camera pose, planes, point cloud) to a Rerun viewer via TCP.
 * Uses [rememberRerunBridge] to manage the connection lifecycle.
 *
 * Setup:
 * 1. Run `python tools/rerun-bridge.py` on your dev machine
 * 2. Run `adb reverse tcp:9876 tcp:9876`
 * 3. Open the Rerun viewer
 * 4. Launch this demo and tap "Connect"
 */
@Composable
fun ARRerunDemo(onBack: () -> Unit) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)

    var host by remember { mutableStateOf(RerunBridge.DEFAULT_HOST) }
    var port by remember { mutableStateOf(RerunBridge.DEFAULT_PORT.toString()) }
    var isConnected by remember { mutableStateOf(false) }
    var isTracking by remember { mutableStateOf(false) }
    var trackingFailureReason by remember { mutableStateOf<TrackingFailureReason?>(null) }
    var frameCount by remember { mutableStateOf(0L) }
    var eventCount by remember { mutableStateOf(0L) }
    var eventsPerSec by remember { mutableStateOf(0f) }
    var lastCameraPose by remember { mutableStateOf<Pose?>(null) }
    var latestFrame by remember { mutableStateOf<Frame?>(null) }
    var showHelp by remember { mutableStateOf(false) }
    val anchors = remember { mutableStateListOf<Anchor>() }

    val modelInstance = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")

    // The bridge is created once and connects/disconnects based on the `enabled` flag.
    val bridge = rememberRerunBridge(
        host = host,
        port = port.toIntOrNull() ?: RerunBridge.DEFAULT_PORT,
        rateHz = 10,
        enabled = isConnected
    )

    // Compute events/sec by sampling eventCount once per second.
    LaunchedEffect(isConnected) {
        var lastSampleCount = eventCount
        while (isConnected) {
            delay(1000)
            val current = eventCount
            eventsPerSec = (current - lastSampleCount).toFloat()
            lastSampleCount = current
        }
        eventsPerSec = 0f
    }

    // Auto-clear "Connecting" state once we get a real frame after enabling.
    val connectionStatus = when {
        !isConnected -> ConnectionStatus.DISCONNECTED
        eventCount == 0L -> ConnectionStatus.CONNECTING
        else -> ConnectionStatus.STREAMING
    }

    if (showHelp) {
        HelpDialog(onDismiss = { showHelp = false })
    }

    DemoScaffold(
        title = "Rerun Debug",
        onBack = onBack,
        controls = {
            // ── About / intro card ──────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "About Rerun.io",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Rerun is a 3D visualizer for AR/robotics debugging. " +
                            "This demo streams ARCore data (camera pose, planes, point cloud) " +
                            "from this device to a Rerun viewer running on your dev machine.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "The viewer is NOT shown on the phone — it lives on your " +
                            "computer. Tap \"How to view\" for setup steps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showHelp = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("How to view")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Connection settings ─────────────────────────────────────────
            Text("Rerun Connection", style = MaterialTheme.typography.labelLarge)

            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Server IP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isConnected
            )

            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text("Port") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isConnected
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isConnected) {
                    Button(
                        onClick = {
                            // Reset stats on each new connection attempt.
                            eventCount = 0L
                            frameCount = 0L
                            eventsPerSec = 0f
                            lastCameraPose = null
                            isConnected = true
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Connect")
                    }
                } else {
                    OutlinedButton(
                        onClick = { isConnected = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Disconnect")
                    }
                }
            }

            // ── Stream Stats ────────────────────────────────────────────────
            if (isConnected) {
                Spacer(Modifier.height(8.dp))
                StreamStatsCard(
                    status = connectionStatus,
                    host = host,
                    port = port,
                    eventCount = eventCount,
                    eventsPerSec = eventsPerSec,
                    frameCount = frameCount,
                    lastPose = lastCameraPose
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
                },
                onSessionUpdated = { session: Session, frame: Frame ->
                    latestFrame = frame
                    isTracking = frame.camera.trackingState == TrackingState.TRACKING
                    // Stream every frame to Rerun (bridge handles rate limiting internally)
                    bridge.logFrame(session, frame)
                    if (isConnected) {
                        frameCount++
                        // Mirror what the bridge actually emits (rate-limited to ~10 Hz):
                        // every Nth frame becomes one camera-pose event + N plane events
                        // + 1 point-cloud event. We approximate by counting one event per
                        // frame at the bridge's effective rate — close enough for a
                        // live indicator.
                        eventCount++
                        if (frame.camera.trackingState == TrackingState.TRACKING) {
                            lastCameraPose = frame.camera.pose
                        }
                    }
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
                        val hit = frame.hitTest(event).firstOrNull { result ->
                            val trackable = result.trackable
                            trackable is Plane &&
                                trackable.isPoseInPolygon(result.hitPose) &&
                                result.distance <= 5.0f
                        }
                        if (hit != null) {
                            anchors.add(hit.createAnchor())
                        }
                    }
                )
            ) {
                anchors.forEach { anchor ->
                    AnchorNode(anchor = anchor) {
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
                visible = !isTracking,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
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

@Composable
private fun StreamStatsCard(
    status: ConnectionStatus,
    host: String,
    port: String,
    eventCount: Long,
    eventsPerSec: Float,
    frameCount: Long,
    lastPose: Pose?
) {
    val (statusLabel, statusColor) = when (status) {
        ConnectionStatus.DISCONNECTED -> "Disconnected" to MaterialTheme.colorScheme.outline
        ConnectionStatus.CONNECTING -> "Connecting…" to MaterialTheme.colorScheme.tertiary
        ConnectionStatus.STREAMING -> "Streaming" to MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Stream Stats",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    modifier = Modifier
                        .background(
                            color = statusColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            StatRow(label = "Target", value = "$host:$port")
            StatRow(label = "Events sent", value = eventCount.toString())
            StatRow(
                label = "Rate",
                value = if (eventsPerSec > 0f) "%.0f events/s".format(eventsPerSec) else "—"
            )
            StatRow(label = "AR frames", value = frameCount.toString())

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Last camera pose",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            if (lastPose != null) {
                Text(
                    text = "pos  x=%+.2f  y=%+.2f  z=%+.2f".format(
                        lastPose.tx(), lastPose.ty(), lastPose.tz()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "quat x=%+.2f y=%+.2f z=%+.2f w=%+.2f".format(
                        lastPose.qx(), lastPose.qy(), lastPose.qz(), lastPose.qw()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Text(
                    text = "(waiting for tracking…)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How to view the Rerun stream") },
        text = {
            Column {
                Text(
                    text = "This phone streams AR debug data. To see it, run a Rerun viewer " +
                        "on your dev computer. Three steps:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "1. Install Rerun on your computer",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "pip install rerun-sdk",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "2. Run the bridge sidecar",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "python tools/rerun-bridge.py",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "(opens the viewer + listens on TCP 9876)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "3. Forward the port over USB",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "adb reverse tcp:9876 tcp:9876",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Then tap Connect here. Camera pose, tracked planes and point " +
                        "cloud will appear live in the Rerun viewer on your computer.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Wi-Fi alternative: skip step 3, set Server IP to your computer's " +
                        "LAN address.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        }
    )
}
