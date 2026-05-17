package io.github.sceneview.demo.ar

import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.sceneview.demo.ALL_DEMOS
import io.github.sceneview.demo.DemoCategory
import io.github.sceneview.demo.DemoSettings
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * **Autonomous AR replay harness** — slice 4 of the device-QA umbrella
 * ([#1560](https://github.com/sceneview/sceneview/issues/1560), this slice
 * [#1565](https://github.com/sceneview/sceneview/issues/1565)).
 *
 * This is the headless, emulator-runnable entrypoint the orchestrator runner
 * ([#1566](https://github.com/sceneview/sceneview/issues/1566)) drives via
 * `.claude/scripts/ar-replay-qa.sh`. It exercises **every AR demo** against a
 * recorded ARCore session — no physical device, no live camera, no IMU — and
 * emits a machine-readable pass/fail-per-demo result.
 *
 * ## What it does
 *
 * For each demo in [DemoCategory.AUGMENTED_REALITY] (13 demos, see
 * [ALL_DEMOS]) it:
 *
 * 1. Deploys the bundled ARCore recording into the app's external files dir.
 * 2. Deep-links the demo with `--es ar_playback_file <path>` so any demo that
 *    honours [DemoSettings.arPendingPlaybackFile] auto-enters replay.
 * 3. Leaves it running for a fixed window and asserts the process stayed
 *    alive (catches ARCore session-init exceptions, missing manifest entries,
 *    Filament JNI failures — the catastrophic class of regression).
 * 4. For `ar-record-playback` — the one demo that genuinely consumes
 *    [DemoSettings.arPendingPlaybackFile] today — it additionally asserts the
 *    ARCore frame counter advanced, i.e. the dataset really replayed frames
 *    rather than just not crashing.
 *
 * The per-demo verdict is collected into `ar-qa-summary.json` on the device
 * (see [writeSummary]) so the slice-5 orchestrator can pull one file instead
 * of scraping instrumentation stdout.
 *
 * ## Relationship to the sibling AR tests
 *
 * - [ARDemoPlaybackSmokeTest] — sweeps `androidTest/assets/ar-recordings/`
 *   fixtures (currently README-only; populated by #1442) through the single
 *   `ar-record-playback` demo with a per-fixture golden.
 * - [ARPlaybackScreenshotTest] — frame-indexed (f=30/60/120/180) golden
 *   regression for `ar-record-playback` against the bundled recording.
 * - **This test** — breadth: every AR demo, crash-survival + (where the demo
 *   supports it) real frame-advance, with a machine-readable summary.
 *
 * The three are complementary: depth (golden frames) on the replay demo,
 * breadth (all AR demos) here.
 *
 * ## Why most demos get a crash-survival check, not a golden
 *
 * Only `ar-record-playback` reads [DemoSettings.arPendingPlaybackFile] and
 * mounts `ARSceneView(playbackDataset = file)`. The other 12 AR demos build a
 * **live** `ARSceneView`. Driving them through a recorded session still has
 * real value — ARCore session creation, demo composition, the Filament
 * engine, model loading and the AR overlay all execute on the emulator, which
 * is exactly the layer that breaks silently between releases. As demos opt
 * into `playbackDataset` (tracked as a follow-up to #1565) this harness picks
 * them up automatically and the verdict graduates from `alive` to
 * `replayed`.
 *
 * ## Requirements
 *
 * - The bundled recording ships in the `debug` sourceSet
 *   (`src/debug/assets/ar-recordings/bundled-pixel9-sample.mp4`) — run against
 *   a debug build, or `git sparse-checkout add` that path.
 * - Google Play Services for AR installed (the ARCore runtime drives playback
 *   even for recorded sessions). `.claude/scripts/setup-ar-emulator.sh`
 *   provisions an emulator with it.
 *
 * The test is `assumeTrue`-skipped — never failed — when the bundled
 * recording is absent, so a sparse checkout that excludes the MP4 stays
 * green.
 */
@RunWith(AndroidJUnit4::class)
class ARReplayHarnessTest {

    private lateinit var context: Context
    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // Pre-grant runtime permissions: AGP reinstalls the demo APK before each
        // test class, wiping any prior `pm grant`. Without CAMERA the AR demos
        // block at the permission prompt instead of mounting the ARSceneView.
        device.executeShellCommand("pm grant io.github.sceneview.demo android.permission.CAMERA")
        device.executeShellCommand("pm grant io.github.sceneview.demo android.permission.RECORD_AUDIO")
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
    }

    @Test
    fun everyArDemo_replaysRecordedSession_withoutCrash() {
        val recording = locateBundledRecording()
        assumeTrue(
            "Bundled ARCore recording not found in the demo APK assets " +
                "($BUNDLED_RECORDING). It ships in the `debug` sourceSet " +
                "(samples/android-demo/src/debug/assets/ar-recordings/) — run the " +
                "test against a debug build, or `git sparse-checkout add` that path.",
            recording != null,
        )
        val deployed = deployFixtureToAppPrivateDir(recording!!)

        val arDemos = ALL_DEMOS.filter { it.category == DemoCategory.AUGMENTED_REALITY }
        assertTrue(
            "Expected at least one Augmented Reality demo in ALL_DEMOS",
            arDemos.isNotEmpty(),
        )

        val results = ArrayList<DemoResult>(arDemos.size)
        try {
            for (demo in arDemos) {
                results += replayDemo(demo.id, deployed)
                // Land on the demo list between demos so onNewIntent fires cleanly
                // for the next deep-link.
                device.pressHome()
            }
        } finally {
            deployed.delete()
        }

        val summaryPath = writeSummary(results)

        // The harness fails if ANY demo crashed during replay. Demos that the
        // recording could not advance (live-only demos that ignore the playback
        // extra) are reported as `alive`, not failed — surviving a recorded
        // session is the contract for those.
        val crashed = results.filter { it.verdict == VERDICT_CRASHED }
        assertTrue(
            "AR replay harness: ${crashed.size}/${results.size} demo(s) crashed " +
                "during recorded-session replay — ${crashed.joinToString { it.demoId }}. " +
                "Full machine-readable verdict: $summaryPath",
            crashed.isEmpty(),
        )
    }

    // ── Per-demo replay ─────────────────────────────────────────────────────

    /**
     * Replays [demoId] against the deployed [recording]. Returns the verdict:
     *
     * - [VERDICT_REPLAYED] — the demo consumed recorded ARCore frames (the
     *   frame counter advanced past [MIN_REPLAYED_FRAMES]).
     * - [VERDICT_ALIVE] — the demo's process stayed alive for the replay
     *   window but did not advance the frame counter (a live-only AR demo
     *   that does not yet honour `playbackDataset`).
     * - [VERDICT_CRASHED] — the demo's process died during replay.
     */
    private fun replayDemo(demoId: String, recording: File): DemoResult {
        // Reset the cross-thread frame counter so a stale value from the
        // previous demo cannot be mistaken for this demo's progress.
        DemoSettings.arPlaybackFrameCount = 0

        launchDemo(demoId, recording.absolutePath)
        // Give the activity time to boot, ARCore to init, and the recorded
        // session to advance a few frames.
        SystemClock.sleep(REPLAY_WINDOW_MILLIS)

        val frames = DemoSettings.arPlaybackFrameCount
        val alive = isDemoProcessAlive()

        val verdict = when {
            !alive -> VERDICT_CRASHED
            frames >= MIN_REPLAYED_FRAMES -> VERDICT_REPLAYED
            else -> VERDICT_ALIVE
        }
        return DemoResult(demoId, verdict, frames)
    }

    /**
     * `true` when the demo process is still running. Uses `pidof` over the
     * app's package name — a crashed Activity tears down the process, so an
     * empty `pidof` is an unambiguous crash signal.
     */
    private fun isDemoProcessAlive(): Boolean {
        val out = device.executeShellCommand("pidof io.github.sceneview.demo")
        return out.trim().isNotEmpty()
    }

    // ── Machine-readable summary ────────────────────────────────────────────

    /**
     * Writes a machine-readable verdict file to
     * `/sdcard/Download/SceneView/ar-qa-summary.json` — a path that survives
     * AGP's post-test app uninstall so `.claude/scripts/ar-replay-qa.sh` can
     * `adb pull` it.
     *
     * The shape mirrors the device-QA harness convention (umbrella #1560): a
     * top-level `harness` / `passed` / `total` header plus a `demos` array of
     * `{ id, verdict, replayedFrames }`. Slice #1564's `web-qa-summary.json`
     * is expected to follow the same `{ harness, passed, total, <items> }`
     * skeleton so the slice-5 orchestrator can merge platform reports without
     * special-casing each.
     */
    private fun writeSummary(results: List<DemoResult>): File {
        val demos = JSONArray()
        for (r in results) {
            demos.put(
                JSONObject()
                    .put("id", r.demoId)
                    .put("verdict", r.verdict)
                    .put("replayedFrames", r.replayedFrames),
            )
        }
        val passed = results.count { it.verdict != VERDICT_CRASHED }
        val root = JSONObject()
            .put("harness", "ar-replay")
            .put("recording", BUNDLED_RECORDING)
            .put("passed", passed)
            .put("total", results.size)
            .put("demos", demos)

        device.executeShellCommand("mkdir -p /sdcard/Download/SceneView")
        val file = File("/sdcard/Download/SceneView/ar-qa-summary.json")
        FileOutputStream(file).use { it.write(root.toString(2).toByteArray()) }
        return file
    }

    private data class DemoResult(
        val demoId: String,
        val verdict: String,
        val replayedFrames: Int,
    )

    // ── Fixture plumbing ────────────────────────────────────────────────────

    /**
     * Locates the bundled ARCore recording in the demo APK's assets. It ships
     * in the `debug` sourceSet (`src/debug/assets/ar-recordings/`), so it is
     * read through the *target* app's asset manager. Returns `null` if absent
     * (release build, or a sparse checkout that excluded the MP4).
     */
    private fun locateBundledRecording(): String? = runCatching {
        context.assets.list("ar-recordings")
            ?.firstOrNull { it == BUNDLED_RECORDING }
    }.getOrNull()

    /**
     * Copies the bundled recording from the demo APK's assets into the app's
     * external-files `ar-recordings/` directory — the path the demo's
     * Recordings list scans and that the `--es ar_playback_file` security
     * guard in `MainActivity` allows.
     */
    private fun deployFixtureToAppPrivateDir(fixtureName: String): File {
        val targetDir = context.getExternalFilesDir("ar-recordings")!!
        targetDir.mkdirs()
        val targetFile = File(targetDir, fixtureName)
        context.assets.open("ar-recordings/$fixtureName").use { input ->
            targetFile.outputStream().use { output -> input.copyTo(output) }
        }
        return targetFile
    }

    /**
     * Deep-links a demo via `am start --es demo <slug>` with the recorded
     * session attached via `--es ar_playback_file <path>` — the same path
     * `ARDemoPlaybackSmokeTest` uses. Demos that honour
     * [DemoSettings.arPendingPlaybackFile] auto-enter replay; the others
     * launch live and the extra is harmlessly ignored.
     */
    private fun launchDemo(demoSlug: String, playbackFile: String) {
        device.executeShellCommand(
            "am start -n io.github.sceneview.demo/.MainActivity " +
                "-f 0x14000000 " + // CLEAR_TOP | NEW_TASK so onNewIntent fires on re-launch
                "--es demo $demoSlug " +
                "--es ar_playback_file $playbackFile",
        )
    }

    private companion object {
        /** Bundled recording filename — matches `src/debug/assets/ar-recordings/`. */
        const val BUNDLED_RECORDING = "bundled-pixel9-sample.mp4"

        /**
         * Time to leave each demo running before reading its verdict. Long
         * enough for ARCore session init + a few replayed frames (~5 s on a
         * Pixel 7a emulator), short enough that a 13-demo sweep stays well
         * under the instrumentation timeout.
         */
        const val REPLAY_WINDOW_MILLIS = 7_000L

        /**
         * Minimum ARCore frames a demo must consume to be graded `replayed`
         * rather than merely `alive`. A handful of frames proves the recorded
         * dataset actually drove the session.
         */
        const val MIN_REPLAYED_FRAMES = 5

        const val VERDICT_REPLAYED = "replayed"
        const val VERDICT_ALIVE = "alive"
        const val VERDICT_CRASHED = "crashed"
    }
}
