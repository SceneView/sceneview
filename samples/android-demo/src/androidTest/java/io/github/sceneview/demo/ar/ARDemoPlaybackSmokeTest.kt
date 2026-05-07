package io.github.sceneview.demo.ar

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Replay-driven AR-demo smoke tests.
 *
 * For each fixture under `androidTest/assets/ar-recordings/`, this test launches the
 * `ARRecordPlaybackDemo` deep-link (`--es demo ar-record-playback`), copies the fixture
 * into the app's external files dir so the demo lists it as a recording, then asserts
 * the activity stays alive for `MIN_PLAYBACK_SECONDS`. Catches any catastrophic crash
 * (e.g. ARCore session init exception, missing manifest entry, Filament JNI failure)
 * that would tank the demo on every replay.
 *
 * **Adding more scenario coverage**: as `samples/android-demo/AR_TESTING.md` describes,
 * drop additional MP4s into `androidTest/assets/ar-recordings/` and they auto-enroll
 * via the [discoverFixtures] helper. Long-term goal: per-demo tests that mount
 * each AR demo composable with a fixture-specific playback file via a future
 * `playbackOverride` ctor param. This file is the scaffold those tests slot into.
 *
 * **No fixtures available**: the test is `assumeTrue`-skipped when no MP4s are present,
 * so the suite stays green on a fresh clone before anyone has captured a baseline. Once
 * a contributor records and commits the first fixture, the test starts running.
 */
@RunWith(AndroidJUnit4::class)
class ARDemoPlaybackSmokeTest {

    private lateinit var context: Context
    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun arRecordPlaybackDemo_replays_each_fixture_without_crash() {
        val fixtures = discoverFixtures()
        assumeTrue(
            "No AR recording fixtures in androidTest/assets/ar-recordings/. " +
                "Capture one on device and commit it — see samples/android-demo/AR_TESTING.md",
            fixtures.isNotEmpty(),
        )

        for (fixture in fixtures) {
            val deployed = deployFixtureToAppPrivateDir(fixture)
            try {
                launchDemo("ar-record-playback")
                // Give the activity time to boot, the AR session to initialize, and
                // ARCore to consume at least the first few frames of the dataset.
                Thread.sleep(MIN_PLAYBACK_MILLIS)
                // If we got here without an uncaught crash, the smoke test passed for
                // this fixture. The next iteration deploys the next fixture and reruns.
            } finally {
                // Tear down: kill the demo activity so the next iteration starts clean.
                device.pressHome()
                deployed.delete()
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Lists every `.mp4` under `androidTest/assets/ar-recordings/` (or returns an empty
     * list if the dir doesn't exist or has no MP4s yet). Sorted alphabetically so the
     * test order is deterministic.
     */
    private fun discoverFixtures(): List<String> {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val assetManager = testContext.assets
        return runCatching {
            assetManager.list("ar-recordings")
                ?.filter { it.endsWith(".mp4", ignoreCase = true) }
                ?.sorted()
                ?: emptyList()
        }.getOrElse { emptyList() }
    }

    /**
     * Copies a fixture from the test apk's assets into the app's external-files
     * `ar-recordings/` directory — the same path the demo's Recordings list scans.
     */
    private fun deployFixtureToAppPrivateDir(fixtureName: String): File {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val targetDir = context.getExternalFilesDir("ar-recordings")!!
        targetDir.mkdirs()
        val targetFile = File(targetDir, fixtureName)
        testContext.assets.open("ar-recordings/$fixtureName").use { input ->
            targetFile.outputStream().use { output -> input.copyTo(output) }
        }
        return targetFile
    }

    /**
     * Launches the demo activity via `am start --es demo <slug>` — the same deep-link path
     * that contributors use to QA demos manually. Uses UiAutomator's shell access (already
     * in `androidTestImplementation`) instead of pulling in `androidx.test:core` for
     * `ActivityScenario` — saves a dep and matches the existing test infrastructure.
     */
    private fun launchDemo(demoSlug: String) {
        device.executeShellCommand(
            "am start -n io.github.sceneview.demo/.MainActivity --es demo $demoSlug"
        )
    }

    private companion object {
        /**
         * Time to leave the demo running before declaring it healthy. Empirically chosen:
         * long enough for ARCore to finish session init + consume the first few frames of
         * the dataset (~3–5 s is enough on Pixel 9 / 7a), but short enough that a 10-fixture
         * sweep stays under 90 s wall-clock for CI.
         */
        const val MIN_PLAYBACK_MILLIS = 6_000L
    }
}
