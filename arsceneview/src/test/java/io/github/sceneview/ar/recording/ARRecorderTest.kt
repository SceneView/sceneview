package io.github.sceneview.ar.recording

import com.google.ar.core.PlaybackStatus
import com.google.ar.core.RecordingConfig
import com.google.ar.core.Session
import com.google.ar.core.exceptions.RecordingFailedException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import java.io.File
import java.nio.file.Files

/**
 * JVM unit tests for [ARRecorder].
 *
 * The ARCore [Session] and [RecordingConfig] classes are not `final`, so we exercise
 * the recorder against:
 *
 *  * a hand-rolled [FakeSession] subclass that overrides only the methods we need
 *    ([Session.getPlaybackStatus], [Session.startRecording], [Session.stopRecording]),
 *  * a Robolectric [Shadow][org.robolectric.shadow.api.Shadow] for [RecordingConfig]
 *    so calls to the native-backed setters become observable in pure JVM.
 *
 * All tests run on the Robolectric runtime, which neutralises the JNI symbols ARCore
 * would otherwise pull in. No emulator is required.
 *
 * Closes [sceneview/sceneview#875](https://github.com/sceneview/sceneview/issues/875).
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ARRecorderTest.ShadowRecordingConfig::class], manifest = Config.NONE)
class ARRecorderTest {

    // ── Shared fixtures ──────────────────────────────────────────────────────

    private lateinit var tempDir: File
    private lateinit var outFile: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("ar-recorder-test").toFile()
        outFile = File(tempDir, "session.mp4")
        ShadowRecordingConfig.reset()
    }

    @After
    fun tearDown() {
        outFile.delete()
        tempDir.deleteRecursively()
        ShadowRecordingConfig.reset()
    }

    // ── Initial state ────────────────────────────────────────────────────────

    @Test
    fun `initial state is IDLE with no error and no file`() {
        val recorder = ARRecorder()
        assertEquals(ARRecorder.State.IDLE, recorder.state)
        assertNull(recorder.errorMessage)
        assertNull(recorder.recordingFile)
    }

    // ── start() preconditions ────────────────────────────────────────────────

    @Test
    fun `start without prior attach returns false and transitions to ERROR with a clear message`() {
        val recorder = ARRecorder()
        val started = recorder.start(outFile)
        assertFalse(started)
        assertEquals(ARRecorder.State.ERROR, recorder.state)
        val msg = recorder.errorMessage
        assertNotNull("errorMessage should be populated", msg)
        assertTrue(
            "expected message to mention session attachment, got: $msg",
            msg!!.contains("session", ignoreCase = true),
        )
    }

    @Test
    fun `attach does not change state`() {
        val recorder = ARRecorder()
        recorder.attach(FakeSession())
        assertEquals(ARRecorder.State.IDLE, recorder.state)
        assertNull(recorder.errorMessage)
        assertNull(recorder.recordingFile)
    }

    @Test
    fun `attach with same session twice is observably idempotent`() {
        val recorder = ARRecorder()
        val session = FakeSession()
        recorder.attach(session)
        val before = Triple(recorder.state, recorder.errorMessage, recorder.recordingFile)
        recorder.attach(session)
        val after = Triple(recorder.state, recorder.errorMessage, recorder.recordingFile)
        assertEquals(before, after)
    }

    // ── start() success ──────────────────────────────────────────────────────

    @Test
    fun `start when attached and playback is NONE transitions to RECORDING`() {
        val recorder = ARRecorder()
        val session = FakeSession(playbackStatus = PlaybackStatus.NONE)
        recorder.attach(session)

        val started = recorder.start(outFile)

        assertTrue("start() should succeed", started)
        assertEquals(ARRecorder.State.RECORDING, recorder.state)
        assertEquals(outFile, recorder.recordingFile)
        assertNull(recorder.errorMessage)
        assertEquals(1, session.startRecordingCount)
    }

    @Test
    fun `start creates the parent directory of the recording file`() {
        val recorder = ARRecorder()
        recorder.attach(FakeSession())
        val nested = File(tempDir, "deep/nested/dir/session.mp4")
        try {
            assertFalse(nested.parentFile!!.exists())

            val started = recorder.start(nested)

            assertTrue(started)
            assertTrue(
                "parent directory should be created by start()",
                nested.parentFile!!.exists(),
            )
        } finally {
            nested.parentFile?.deleteRecursively()
        }
    }

    // ── start() blocked while in playback ────────────────────────────────────

    @Test
    fun `start while session is in playback OK returns false and goes to ERROR`() {
        assertStartRefusedDuringPlayback(PlaybackStatus.OK)
    }

    @Test
    fun `start while session is in playback FINISHED returns false and goes to ERROR`() {
        assertStartRefusedDuringPlayback(PlaybackStatus.FINISHED)
    }

    @Test
    fun `start while session is in playback IO_ERROR returns false and goes to ERROR`() {
        assertStartRefusedDuringPlayback(PlaybackStatus.IO_ERROR)
    }

    private fun assertStartRefusedDuringPlayback(status: PlaybackStatus) {
        val recorder = ARRecorder()
        val session = FakeSession(playbackStatus = status)
        recorder.attach(session)

        val started = recorder.start(outFile)

        assertFalse(started)
        assertEquals(ARRecorder.State.ERROR, recorder.state)
        assertEquals(0, session.startRecordingCount)
        val msg = recorder.errorMessage
        assertNotNull(msg)
        assertTrue(
            "expected error to mention playback, got: $msg",
            msg!!.contains("playback", ignoreCase = true),
        )
    }

    // ── stop() ──────────────────────────────────────────────────────────────

    @Test
    fun `stop while RECORDING returns the file and transitions to IDLE`() {
        val recorder = ARRecorder()
        val session = FakeSession()
        recorder.attach(session)
        recorder.start(outFile)
        assertEquals(ARRecorder.State.RECORDING, recorder.state)

        val returned = recorder.stop()

        assertEquals(ARRecorder.State.IDLE, recorder.state)
        assertEquals(outFile, returned)
        // recordingFile is preserved so the caller can list/share the MP4 afterwards.
        assertEquals(outFile, recorder.recordingFile)
        assertEquals(1, session.stopRecordingCount)
    }

    @Test
    fun `stop while IDLE leaves state IDLE and surfaces no error`() {
        // Note: with an attached session, stop() forwards to session.stopRecording()
        // unconditionally — there is no internal IDLE guard. The user-visible contract
        // ("safe to call when idle — it's a no-op") relies on ARCore tolerating the
        // call, so what we pin down here is the recorder's externally observable
        // behaviour: state stays IDLE and no error is raised.
        val recorder = ARRecorder()
        val session = FakeSession()
        recorder.attach(session)

        val returned = recorder.stop()

        assertNull(returned)
        assertEquals(ARRecorder.State.IDLE, recorder.state)
        assertNull(recorder.errorMessage)
    }

    @Test
    fun `stop with no attached session returns the recordingFile and stays IDLE`() {
        // Documents the current early-return branch in stop() when sessionRef is null.
        val recorder = ARRecorder()
        val returned = recorder.stop()
        assertNull(returned)
        assertEquals(ARRecorder.State.IDLE, recorder.state)
    }

    // ── Double-start path (relies on ARCore throwing) ────────────────────────

    @Test
    fun `start while already RECORDING surfaces RecordingFailedException as ERROR`() {
        val recorder = ARRecorder()
        // First start succeeds; second start triggers the configured failure on
        // session.startRecording() — exactly the path the production class catches.
        val session = FakeSession(
            // After the first call, flip to throwing.
            startRecordingException = lazy { RecordingFailedException("already recording") },
            failOnStartRecordingFromCall = 2,
        )
        recorder.attach(session)
        assertTrue(recorder.start(outFile))

        val secondAttempt = recorder.start(outFile)

        assertFalse(secondAttempt)
        assertEquals(ARRecorder.State.ERROR, recorder.state)
        val msg = recorder.errorMessage
        assertNotNull(msg)
        assertTrue(
            "expected message to surface the underlying failure, got: $msg",
            msg!!.contains("already recording", ignoreCase = true),
        )
    }

    // ── ARCore exception paths ──────────────────────────────────────────────

    @Test
    fun `start propagates RecordingFailedException as ERROR with a message`() {
        val recorder = ARRecorder()
        val session = FakeSession(
            startRecordingException = lazy { RecordingFailedException("disk full") },
            failOnStartRecordingFromCall = 1,
        )
        recorder.attach(session)

        val started = recorder.start(outFile)

        assertFalse(started)
        assertEquals(ARRecorder.State.ERROR, recorder.state)
        assertNotNull(recorder.errorMessage)
        assertTrue(recorder.errorMessage!!.contains("disk full"))
    }

    @Test
    fun `stop propagates RecordingFailedException as ERROR with a message`() {
        val recorder = ARRecorder()
        val session = FakeSession(
            stopRecordingException = lazy { RecordingFailedException("io issue") },
        )
        recorder.attach(session)
        assertTrue(recorder.start(outFile))

        val returned = recorder.stop()

        assertNull(returned)
        assertEquals(ARRecorder.State.ERROR, recorder.state)
        assertNotNull(recorder.errorMessage)
        assertTrue(recorder.errorMessage!!.contains("io issue"))
    }

    // ── clearError() ─────────────────────────────────────────────────────────

    @Test
    fun `clearError on ERROR resets state to IDLE and nulls the message`() {
        val recorder = ARRecorder()
        // Force an ERROR state by calling start() without attach().
        recorder.start(outFile)
        assertEquals(ARRecorder.State.ERROR, recorder.state)
        assertNotNull(recorder.errorMessage)

        recorder.clearError()

        assertEquals(ARRecorder.State.IDLE, recorder.state)
        assertNull(recorder.errorMessage)
    }

    @Test
    fun `clearError when not in ERROR is a no-op`() {
        val recorder = ARRecorder()
        // IDLE → no-op
        recorder.clearError()
        assertEquals(ARRecorder.State.IDLE, recorder.state)
        assertNull(recorder.errorMessage)

        // RECORDING → no-op (state must not regress)
        recorder.attach(FakeSession())
        recorder.start(outFile)
        assertEquals(ARRecorder.State.RECORDING, recorder.state)
        recorder.clearError()
        assertEquals(ARRecorder.State.RECORDING, recorder.state)
    }

    // ── attach mid-RECORDING ─────────────────────────────────────────────────

    @Test
    fun `attach with a new session mid-RECORDING swaps the session pointer without changing state`() {
        // Documents the actual behaviour: attach() only writes to the AtomicReference and never
        // touches the state machine. So the recorder remains in RECORDING after a swap, and a
        // subsequent stop() will route to the NEW session — the original session never gets a
        // stopRecording() call. This is the contract; tests pin it down.
        val recorder = ARRecorder()
        val first = FakeSession()
        val second = FakeSession()
        recorder.attach(first)
        assertTrue(recorder.start(outFile))
        assertEquals(ARRecorder.State.RECORDING, recorder.state)

        recorder.attach(second)
        assertEquals(ARRecorder.State.RECORDING, recorder.state)
        assertEquals(outFile, recorder.recordingFile)

        recorder.stop()
        assertEquals(0, first.stopRecordingCount)
        assertEquals(1, second.stopRecordingCount)
    }

    // ── RecordingConfig wiring ───────────────────────────────────────────────

    @Test
    fun `start without recordingRotation does not call setRecordingRotation`() {
        val recorder = ARRecorder()
        val session = FakeSession()
        recorder.attach(session)

        recorder.start(outFile, recordingRotation = null)

        val captured = ShadowRecordingConfig.lastInstance
        assertNotNull("a RecordingConfig should have been built", captured)
        assertEquals(outFile.absolutePath, captured!!.mp4Path)
        assertEquals(true, captured.autoStopOnPause)
        assertNull(
            "setRecordingRotation must not be called when rotation is null",
            captured.rotation,
        )
        assertSame("the shadow should record the same Session instance", session, captured.session)
    }

    @Test
    fun `start with recordingRotation calls setRecordingRotation with that exact value`() {
        val recorder = ARRecorder()
        val session = FakeSession()
        recorder.attach(session)

        recorder.start(outFile, recordingRotation = 90)

        val captured = ShadowRecordingConfig.lastInstance
        assertNotNull(captured)
        assertEquals(outFile.absolutePath, captured!!.mp4Path)
        assertEquals(true, captured.autoStopOnPause)
        assertEquals(90, captured.rotation)
    }

    @Test
    fun `start always sets autoStopOnPause to true`() {
        val recorder = ARRecorder()
        val session = FakeSession()
        recorder.attach(session)
        recorder.start(outFile, recordingRotation = 270)
        assertEquals(true, ShadowRecordingConfig.lastInstance!!.autoStopOnPause)
    }

    // ── Test doubles ─────────────────────────────────────────────────────────

    /**
     * Hand-rolled [Session] subclass for unit tests.
     *
     * `Session` declares a `protected Session()` no-arg constructor specifically so subclasses
     * can be created in tests without going through the JNI-backed `Session(Context)` path.
     * We only override the three methods [ARRecorder] actually calls.
     */
    private class FakeSession(
        private val playbackStatus: PlaybackStatus = PlaybackStatus.NONE,
        private val startRecordingException: Lazy<RecordingFailedException>? = null,
        /**
         * 1-based call index at which [startRecording] starts throwing. `null` means never.
         * Lets us simulate "first call OK, second call fails" for the double-start scenario.
         */
        private val failOnStartRecordingFromCall: Int? = null,
        private val stopRecordingException: Lazy<RecordingFailedException>? = null,
    ) : Session() {

        var startRecordingCount: Int = 0
            private set
        var stopRecordingCount: Int = 0
            private set
        var lastConfig: RecordingConfig? = null
            private set

        override fun getPlaybackStatus(): PlaybackStatus = playbackStatus

        override fun startRecording(config: RecordingConfig?) {
            startRecordingCount += 1
            lastConfig = config
            val failFrom = failOnStartRecordingFromCall
            val ex = startRecordingException
            if (ex != null && failFrom != null && startRecordingCount >= failFrom) {
                throw ex.value
            }
        }

        override fun stopRecording() {
            stopRecordingCount += 1
            stopRecordingException?.let { throw it.value }
        }
    }

    /**
     * Robolectric shadow for [RecordingConfig].
     *
     * The production code calls `RecordingConfig(session)` (which would normally hit
     * `nativeCreateRecordingConfig`) followed by chained setters. The shadow replaces every
     * native-backed method with an in-memory record that the tests can inspect.
     */
    @Implements(RecordingConfig::class)
    class ShadowRecordingConfig {

        // The actual RecordingConfig object this shadow is attached to — wired by
        // Robolectric via @RealObject. Used to return `this` from fluent setters.
        @RealObject
        @JvmField
        var realConfig: RecordingConfig? = null

        var session: Session? = null
            private set
        var mp4Path: String? = null
            private set
        var autoStopOnPause: Boolean? = null
            private set
        var rotation: Int? = null
            private set

        @Suppress("unused") // Called by Robolectric via @Implementation.
        @Implementation
        fun __constructor__(session: Session) {
            this.session = session
            register(this)
        }

        @Implementation
        fun setMp4DatasetFilePath(path: String?): RecordingConfig {
            this.mp4Path = path
            return realConfig!!
        }

        @Implementation
        fun setAutoStopOnPause(autoStop: Boolean): RecordingConfig {
            this.autoStopOnPause = autoStop
            return realConfig!!
        }

        @Implementation
        fun setRecordingRotation(rotation: Int): RecordingConfig {
            this.rotation = rotation
            return realConfig!!
        }

        companion object {
            // Tracks the most recently constructed RecordingConfig instance, accessed by tests.
            @Volatile
            var lastInstance: ShadowRecordingConfig? = null
                private set

            fun reset() { lastInstance = null }

            private fun register(shadow: ShadowRecordingConfig) {
                lastInstance = shadow
            }
        }
    }
}
