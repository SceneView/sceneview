package io.github.sceneview.demo.demos

import android.content.Context
import android.os.Build
import android.view.MotionEvent
import android.view.WindowManager
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.recording.ARRecorder
import io.github.sceneview.ar.recording.rememberARRecorder
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.math.Position
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AR record-and-replay demo.
 *
 * ARCore's `Session.startRecording(RecordingConfig)` captures the **entire** AR session —
 * camera frames, IMU, planes, depth, anchors, light estimation — into an MP4 dataset.
 * `Session.setPlaybackDataset(absolutePath)` then makes the same session replay that MP4 1:1,
 * as if you were physically there. The combination is the most useful debugging primitive
 * ARCore exposes:
 *
 * - **Record outside, replay at the desk** — no need to retake a physical test for every
 *   tweak.
 * - **Reproduce bugs across devs** — share a recording instead of a verbal description.
 * - **Deterministic AR tests** — ship recordings as fixtures, replay them in CI.
 * - **Pair with the Rerun debug demo** — replay a recording while streaming to Rerun for a
 *   stable, repeatable visual inspection loop.
 *
 * The demo has three modes wired to a top segmented control:
 *
 * - **LIVE** — plain AR, tap a plane to drop a helmet (sanity check the session works).
 * - **RECORD** — same as LIVE, plus a record button. While recording, an elapsed-time pill
 *   is shown. Stopping reveals the saved MP4 in the Recordings card.
 * - **PLAYBACK** — lists the MP4s on disk; tapping one re-mounts the [ARSceneView] with
 *   `playbackDataset = file` so ARCore replays the dataset.
 *
 * Switching mode forces the [ARSceneView] to be rebuilt via `key(currentMode, …)` because
 * ARCore binds the playback source at session-creation time and cannot be toggled after
 * resume. Recordings live in `context.getExternalFilesDir("ar-recordings")` — app-private
 * external storage, no runtime permission needed.
 */
@Composable
fun ARRecordPlaybackDemo(onBack: () -> Unit) {
    val context = LocalContext.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)

    var currentMode by remember { mutableStateOf(Mode.LIVE) }
    var currentPlaybackFile by remember { mutableStateOf<File?>(null) }
    var exportToast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(exportToast) {
        if (exportToast != null) {
            android.widget.Toast.makeText(context, exportToast, android.widget.Toast.LENGTH_LONG).show()
            exportToast = null
        }
    }

    // List of recordings on disk — refreshed when a recording finishes or the user enters
    // PLAYBACK mode.
    val recordingsDir = remember(context) {
        context.getExternalFilesDir("ar-recordings")!!.also { it.mkdirs() }
    }
    val recordings = remember { mutableStateListOf<File>() }
    fun refreshRecordings() {
        recordings.clear()
        recordingsDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("mp4", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.let { recordings.addAll(it) }
    }
    LaunchedEffect(currentMode) { refreshRecordings() }

    DemoScaffold(
        title = "Record & Playback",
        onBack = onBack,
        controls = {
            // ── About card ─────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "How this helps",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Record an AR session once, replay it 1:1 without holding a " +
                            "phone. Iterate on AR code at your desk against a stable real-world " +
                            "capture, share recordings between devs to reproduce bugs, or feed " +
                            "deterministic sessions into your test suite.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Pair with the Rerun Debug demo for a fully visual inspection " +
                            "loop: replay a recording while streaming to Rerun.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Mode selector ──────────────────────────────────────────────
            Text("Mode", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Mode.values().forEach { mode ->
                    FilterChip(
                        selected = currentMode == mode,
                        onClick = {
                            if (currentMode != mode) {
                                currentMode = mode
                                if (mode != Mode.PLAYBACK) currentPlaybackFile = null
                            }
                        },
                        label = { Text(mode.label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            when (currentMode) {
                Mode.LIVE -> {
                    Text(
                        text = "Tap a detected plane to drop a model. This is the same as " +
                            "the Tap-to-Place demo — use it to sanity-check the session " +
                            "before recording.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Mode.RECORD -> {
                    Text(
                        text = "Recordings are saved to app-private external storage. They " +
                            "include camera frames, IMU, planes, depth and anchors — anything " +
                            "the session sees.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Mode.PLAYBACK -> {
                    Text(
                        text = "Pick a recording to replay. ARCore re-runs it as if the camera " +
                            "were live — anchors, planes and tracking all replay deterministically. " +
                            "Tap \"Export\" to copy a recording to Downloads/SceneView/ so you can " +
                            "pull it via `adb pull /sdcard/Download/SceneView/…` or share it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    RecordingsList(
                        recordings = recordings,
                        selected = currentPlaybackFile,
                        onSelect = { file ->
                            // Re-key the ARSceneView so ARCore gets a fresh Session bound to
                            // the new playback dataset.
                            currentPlaybackFile = file
                        },
                        onExport = { file ->
                            ARRecorder.exportToDownloads(context, file)?.let { uri ->
                                exportToast = "Exported to Downloads/SceneView/${file.name}"
                            } ?: run { exportToast = "Export failed — see logs" }
                        },
                        onRefresh = { refreshRecordings() }
                    )
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // The ARSceneView is keyed on (currentMode, currentPlaybackFile) so that any
            // mode switch — and any new playback selection — forces a fresh ARCore Session.
            // This is required: setPlaybackDataset must run before resume(), which only
            // happens when a brand-new session is created.
            key(currentMode, currentPlaybackFile?.absolutePath) {
                ModeContent(
                    mode = currentMode,
                    playbackFile = currentPlaybackFile,
                    engine = engine,
                    modelLoader = modelLoader,
                    materialLoader = materialLoader,
                    onRecordingFinished = { refreshRecordings() },
                    recordingsDir = recordingsDir
                )
            }
        }
    }
}

/**
 * Top-level 3-way segmented control values.
 */
private enum class Mode(val label: String) {
    LIVE("Live"),
    RECORD("Record"),
    PLAYBACK("Playback")
}

/**
 * Wraps the per-mode [ARSceneView] + overlays. Pulled out into its own composable so that
 * the outer `key(...)` block remounts everything (including the recorder, recording timer
 * and tap state) on every mode change — the simplest way to guarantee a fresh ARCore
 * Session for each transition.
 */
@Composable
private fun ModeContent(
    mode: Mode,
    playbackFile: File?,
    engine: com.google.android.filament.Engine,
    modelLoader: io.github.sceneview.loaders.ModelLoader,
    materialLoader: io.github.sceneview.loaders.MaterialLoader,
    onRecordingFinished: () -> Unit,
    recordingsDir: File
) {
    val context = LocalContext.current
    val anchors = remember { mutableStateListOf<Anchor>() }
    var latestFrame by remember { mutableStateOf<Frame?>(null) }
    var isTracking by remember { mutableStateOf(false) }
    var trackingFailureReason by remember { mutableStateOf<TrackingFailureReason?>(null) }

    val recorder = rememberARRecorder()

    // Elapsed-time tick — only runs while actively recording so we don't burn cycles.
    var elapsedSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(recorder.state) {
        if (recorder.state == ARRecorder.State.RECORDING) {
            val start = System.currentTimeMillis()
            while (recorder.state == ARRecorder.State.RECORDING) {
                elapsedSeconds = (System.currentTimeMillis() - start) / 1000
                delay(500)
            }
        } else {
            elapsedSeconds = 0
        }
    }

    // When the recorder transitions out of RECORDING (and back to IDLE), refresh the
    // recordings list so the new MP4 shows up in PLAYBACK mode.
    DisposableEffect(recorder) {
        onDispose {
            if (recorder.state == ARRecorder.State.RECORDING) {
                recorder.stop()
                onRecordingFinished()
            }
        }
    }

    val helmet = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")

    val playbackDataset: File? = if (mode == Mode.PLAYBACK) playbackFile else null

    ARSceneView(
        modifier = Modifier.fillMaxSize(),
        engine = engine,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        planeRenderer = true,
        // Same exposure tweak as ARPlacementDemo — Pixel 9 review feedback.
        cameraExposure = -1.0f,
        playbackDataset = playbackDataset,
        sessionConfiguration = { _: Session, config: Config ->
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
        },
        onSessionUpdated = { session: Session, frame: Frame ->
            latestFrame = frame
            isTracking = frame.camera.trackingState == TrackingState.TRACKING
            // Wire the recorder every frame — attach is idempotent so this is cheap.
            recorder.attach(session)
        },
        onTrackingFailureChanged = { trackingFailureReason = it },
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
                if (hit != null) anchors.add(hit.createAnchor())
            }
        )
    ) {
        anchors.forEach { anchor ->
            AnchorNode(anchor = anchor) {
                helmet?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        scaleToUnits = 0.3f,
                        centerOrigin = Position(0f, 0f, 0f)
                    )
                }
            }
        }
    }

    // ── Mode-specific overlays ─────────────────────────────────────────────

    if (mode == Mode.RECORD) {
        RecordOverlay(
            recorder = recorder,
            elapsedSeconds = elapsedSeconds,
            onStart = {
                val name = "ar-session-${TIMESTAMP_FORMAT.format(Date())}.mp4"
                recorder.start(
                    file = File(recordingsDir, name),
                    recordingRotation = currentDisplayRotation(context)
                )
            },
            onStop = {
                recorder.stop()
                onRecordingFinished()
            }
        )
    }

    if (mode == Mode.PLAYBACK && playbackFile != null) {
        PlaybackBanner(playbackFile.name)
    }

    // Tracking failure overlay — uses its own fillMaxSize Box internally so it can align to
    // the bottom of the parent Box.
    if (!isTracking) {
        TrackingFailureBanner(reason = trackingFailureReason)
    }
}

@Composable
private fun RecordOverlay(
    recorder: ARRecorder,
    elapsedSeconds: Long,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Elapsed-time pill, top-center, only while recording.
        AnimatedVisibility(
            visible = recorder.state == ARRecorder.State.RECORDING,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
        ) {
            Surface(
                color = Color.Red.copy(alpha = 0.85f),
                contentColor = Color.White,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "REC  ${formatElapsed(elapsedSeconds)}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        // Big start/stop button, bottom-center.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            when (recorder.state) {
                ARRecorder.State.RECORDING -> {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("■  Stop")
                    }
                }
                ARRecorder.State.IDLE,
                ARRecorder.State.ERROR -> {
                    Button(onClick = onStart) {
                        Text("●  Record")
                    }
                }
            }
        }

        // Error pill, just above the button.
        if (recorder.state == ARRecorder.State.ERROR) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = recorder.errorMessage ?: "Recording failed",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackBanner(filename: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(
                text = "Now replaying: $filename",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun TrackingFailureBanner(reason: TrackingFailureReason?) {
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = MaterialTheme.shapes.large
        ) {
            Text(
                text = when (reason) {
                    null,
                    TrackingFailureReason.NONE -> "Point your camera at a surface"
                    TrackingFailureReason.BAD_STATE -> "AR session error"
                    TrackingFailureReason.INSUFFICIENT_LIGHT -> "Not enough light"
                    TrackingFailureReason.EXCESSIVE_MOTION -> "Moving too fast"
                    TrackingFailureReason.INSUFFICIENT_FEATURES ->
                        "Not enough detail — try a textured surface"
                    TrackingFailureReason.CAMERA_UNAVAILABLE -> "Camera unavailable"
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun RecordingsList(
    recordings: List<File>,
    selected: File?,
    onSelect: (File) -> Unit,
    onExport: (File) -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Recordings (${recordings.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                OutlinedButton(onClick = onRefresh) { Text("Refresh") }
            }
            Spacer(Modifier.height(8.dp))
            if (recordings.isEmpty()) {
                Text(
                    text = "No recordings yet. Switch to Record mode to capture one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                recordings.forEach { file ->
                    RecordingRow(
                        file = file,
                        isSelected = selected?.absolutePath == file.absolutePath,
                        onClick = { onSelect(file) },
                        onExport = { onExport(file) }
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(file: File, isSelected: Boolean, onClick: () -> Unit, onExport: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${formatBytes(file.length())} • ${formatRelativeAge(file.lastModified())}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            OutlinedButton(
                onClick = onExport,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text("Export")
            }
            Button(onClick = onClick) {
                Text(if (isSelected) "Replaying" else "Replay")
            }
        }
    }
}

/**
 * Returns the current display rotation (`Surface.ROTATION_0` … `Surface.ROTATION_270`) so the
 * MP4 plays back upright when captured in landscape. Uses `Context.getDisplay()` on API 30+
 * and falls back to the deprecated `WindowManager.defaultDisplay` on older APIs.
 */
private fun currentDisplayRotation(context: Context): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display.rotation
    } else {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
    }
}

private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

private fun formatElapsed(seconds: Long): String {
    val mm = seconds / 60
    val ss = seconds % 60
    return "%02d:%02d".format(mm, ss)
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.1f kB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

private fun formatRelativeAge(epochMillis: Long): String {
    val seconds = (System.currentTimeMillis() - epochMillis) / 1000
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86400 -> "${seconds / 3600}h ago"
        else -> "${seconds / 86400}d ago"
    }
}
