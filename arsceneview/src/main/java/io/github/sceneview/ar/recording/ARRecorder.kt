package io.github.sceneview.ar.recording

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.ar.core.PlaybackStatus
import com.google.ar.core.RecordingConfig
import com.google.ar.core.Session
import com.google.ar.core.exceptions.RecordingFailedException
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Compose-friendly helper that records the full state of an [ARSceneView][io.github.sceneview.ar.ARSceneView]'s
 * underlying ARCore [Session] into an MP4 file.
 *
 * ### What ARCore actually records
 *
 * `Session.startRecording(RecordingConfig)` captures **everything** the session sees: the
 * camera image stream, IMU data, depth (if enabled), light estimation, plane updates,
 * anchors, augmented images, and the rest. The resulting MP4 is not a normal video — it is a
 * full session dataset that can be replayed 1:1 with [Session.setPlaybackDataset].
 *
 * ### Why this exists
 *
 * Recording / playback is the single most useful debugging tool ARCore offers: capture a real
 * outdoor / on-site session once, then iterate at the desk against the recording. Combined
 * with the [RerunBridge][io.github.sceneview.ar.rerun.RerunBridge], a recording becomes the
 * deterministic input of a Rerun-driven debugging loop. It also makes deterministic AR tests
 * possible: ship a fixture recording with the test suite and rerun it on every CI.
 *
 * ### Usage
 *
 * ```kotlin
 * val recorder = rememberARRecorder()
 * ARSceneView(
 *     onSessionUpdated = { session, frame -> recorder.attach(session) },
 *     content = { /* … */ }
 * )
 * Button(onClick = { recorder.start(File(context.getExternalFilesDir("ar-recordings"), "session.mp4")) }) {
 *     Text("Record")
 * }
 * Button(onClick = { recorder.stop() }) { Text("Stop") }
 * ```
 *
 * The recorder is non-fatal: any [RecordingFailedException] thrown by ARCore is captured into
 * [errorMessage] and surfaced via the [State.ERROR] state. The session itself keeps running.
 *
 * ### Threading
 *
 * `attach`, `start` and `stop` may be called from any thread — the underlying [AtomicReference]
 * makes the session pointer safe to publish across the render thread (where `onSessionUpdated`
 * runs) and the UI thread (where the buttons live). The actual ARCore calls are synchronous;
 * they're cheap (no blocking I/O), so calling from a UI handler is fine.
 *
 * @see com.google.ar.core.Session.startRecording
 * @see com.google.ar.core.Session.stopRecording
 * @see com.google.ar.core.Session.setPlaybackDataset
 */
public class ARRecorder {

    /** Lifecycle states for the recorder. */
    public enum class State {
        /** No recording in progress. */
        IDLE,

        /** ARCore is actively writing frames to [recordingFile]. */
        RECORDING,

        /** The last [start] / [stop] call failed. See [errorMessage] for details. */
        ERROR,
    }

    private val sessionRef = AtomicReference<Session?>(null)

    private var _state by mutableStateOf(State.IDLE)
    private var _recordingFile by mutableStateOf<File?>(null)
    private var _errorMessage by mutableStateOf<String?>(null)

    /** Current recorder state. Backed by a Compose `MutableState` — recompose-aware. */
    public val state: State
        get() = _state

    /**
     * The file currently being written (when [state] is [State.RECORDING]) or the most
     * recent file written (after [stop]). `null` until [start] succeeds.
     */
    public val recordingFile: File?
        get() = _recordingFile

    /**
     * Human-readable description of the last error, populated when [state] is [State.ERROR].
     * Cleared on the next successful [start].
     */
    public val errorMessage: String?
        get() = _errorMessage

    /**
     * Bind the recorder to an ARCore [Session]. Call this from `onSessionUpdated` (or any
     * place where you have a live session reference) — passing the same session repeatedly
     * is a no-op, so wiring it on every frame is fine.
     *
     * Without an attached session, [start] returns `false` and transitions to [State.ERROR].
     */
    public fun attach(session: Session) {
        sessionRef.set(session)
    }

    /** Detach the session reference. After this, [start] will fail until [attach] is called again. */
    public fun detach() {
        sessionRef.set(null)
    }

    /**
     * Begin recording the attached session into [file]. The parent directory is created if
     * it doesn't already exist.
     *
     * Recording cannot run while the session is in playback mode — if it is, this returns
     * `false` and transitions to [State.ERROR].
     *
     * @param file              Absolute path of the destination MP4. Passed to
     *                          [RecordingConfig.setMp4DatasetFilePath] (no scoped-storage /
     *                          [android.net.Uri] wrapping needed).
     * @param recordingRotation Optional [android.view.Display] rotation (`Surface.ROTATION_0` …
     *                          `Surface.ROTATION_270`) so the MP4 plays back upright when
     *                          captured in landscape. Pass the current display rotation from
     *                          the calling context. Default `null` keeps ARCore's default of 0.
     *
     * Returns `true` if recording started, `false` if no session is attached, the session is
     * in playback mode, or ARCore refused the start. On failure, [state] is set to
     * [State.ERROR] and [errorMessage] holds the reason.
     */
    public fun start(file: File, recordingRotation: Int? = null): Boolean {
        val session = sessionRef.get()
        if (session == null) {
            _errorMessage = "no AR session attached — call attach(session) first"
            _state = State.ERROR
            return false
        }
        // Recording is only allowed when the session is NOT in playback mode. PlaybackStatus
        // values: NONE (no playback dataset bound — recordable), OK / FINISHED / IO_ERROR
        // (playback active or terminated — never recordable).
        if (session.playbackStatus != PlaybackStatus.NONE) {
            _errorMessage = "Cannot record while session is in playback mode"
            _state = State.ERROR
            return false
        }
        return try {
            file.parentFile?.mkdirs()
            val config = RecordingConfig(session)
                .setMp4DatasetFilePath(file.absolutePath)
                .setAutoStopOnPause(true)
            recordingRotation?.let { config.setRecordingRotation(it) }
            session.startRecording(config)
            _recordingFile = file
            _errorMessage = null
            _state = State.RECORDING
            true
        } catch (e: RecordingFailedException) {
            logWarning("startRecording failed: ${e.message}")
            _errorMessage = e.message ?: "RecordingFailedException"
            _state = State.ERROR
            false
        } catch (e: Exception) {
            logWarning("start failed: ${e.message}")
            _errorMessage = e.message ?: e::class.java.simpleName
            _state = State.ERROR
            false
        }
    }

    /**
     * Stop the current recording. Returns the file that was written, or `null` if no
     * recording was active. Safe to call when idle — it's a no-op.
     */
    public fun stop(): File? {
        val session = sessionRef.get() ?: run {
            _state = State.IDLE
            return _recordingFile
        }
        return try {
            session.stopRecording()
            _state = State.IDLE
            _recordingFile
        } catch (e: RecordingFailedException) {
            logWarning("stopRecording failed: ${e.message}")
            _errorMessage = e.message ?: "RecordingFailedException"
            _state = State.ERROR
            null
        } catch (e: Exception) {
            logWarning("stop failed: ${e.message}")
            _errorMessage = e.message ?: e::class.java.simpleName
            _state = State.ERROR
            null
        }
    }

    /** Reset the recorder to [State.IDLE], clearing any previous error. */
    public fun clearError() {
        if (_state == State.ERROR) {
            _state = State.IDLE
            _errorMessage = null
        }
    }

    private fun logWarning(msg: String) {
        try { Log.w(TAG, msg) } catch (_: RuntimeException) { /* unit test stub */ }
    }

    private companion object {
        const val TAG = "ARRecorder"
    }
}

/**
 * Creates and remembers an [ARRecorder] tied to the composable lifecycle.
 *
 * If the recorder is still recording when the composable leaves the tree, [ARRecorder.stop]
 * is invoked automatically so the MP4 is finalized cleanly. Without this hook, an
 * unfinished recording would be left in an undefined state on the disk.
 */
@Composable
public fun rememberARRecorder(): ARRecorder {
    val recorder = remember { ARRecorder() }
    DisposableEffect(recorder) {
        onDispose {
            if (recorder.state == ARRecorder.State.RECORDING) {
                recorder.stop()
            }
            recorder.detach()
        }
    }
    return recorder
}
