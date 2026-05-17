package io.github.sceneview.render

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue

/**
 * Runtime capability gate for the headless Filament render tests.
 *
 * ### Why this exists
 *
 * The five render-test classes in this package
 * ([RenderSmokeTest], [LightingRenderTest], [GeometryRenderTest],
 * [VisualVerificationTest], [DemoParametersRenderTest]) drive [RenderTestHarness],
 * which reads back the framebuffer through Filament's **async `readPixels` callback**.
 * That callback never fires on the Apple-Silicon emulator
 * (gfxstream OpenGL ES → Metal translator), so on the default CI emulator the readback
 * blocks until [RenderTestHarness.capturePixels] times out — and the failure crashes the
 * test process, killing every remaining test. This is the harness-side limitation tracked
 * in [#803](https://github.com/sceneview/sceneview/issues/803).
 *
 * Historically the whole package was blanket `@Ignore`'d. That made the tests **orphan
 * code** — compiled but never executed, so they could not catch regressions and silently
 * drifted (see [#912](https://github.com/sceneview/sceneview/issues/912)).
 *
 * ### What this does instead
 *
 * Each render-test class now calls [assumeGpuReadbackAvailable] from an `@Before` method.
 * Instead of being permanently ignored, the tests:
 *
 * - **RUN** on a runner that advertises working GPU pixel readback (a hardware-GPU device,
 *   or any environment that explicitly opts in via the flag below);
 * - **cleanly SKIP** — JUnit "assumption failed", *not* "ignored", *not* "failed" — on the
 *   SwiftShader / Apple-Silicon emulator where the readback is broken.
 *
 * They are therefore wired to actually execute the moment the environment supports it,
 * which is exactly what [#912](https://github.com/sceneview/sceneview/issues/912) asks for.
 *
 * ### How to opt in
 *
 * GPU pixel readback cannot be safely probed at runtime — the broken path is a hard
 * process crash, not a recoverable error — so it is gated on an explicit opt-in. Pass
 * either as an instrumentation argument or as a system property:
 *
 * ```
 * ./gradlew :sceneview:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.gpuReadback=true
 * ```
 *
 * CI runs the render-test classes only on the hardware-accelerated GPU matrix leg, which
 * sets `gpuReadback=true`. The default Apple-Silicon emulator leg leaves it unset, so the
 * classes report as skipped rather than as orphan or failing.
 */
internal object RenderTestCapabilities {

    /**
     * Instrumentation-argument / system-property key that opts a runner into the render
     * tests. Truthy values: `true`, `1`, `yes` (case-insensitive).
     */
    const val GPU_READBACK_FLAG = "gpuReadback"

    /** Truthy string values accepted for [GPU_READBACK_FLAG]. */
    private val TRUTHY = setOf("true", "1", "yes")

    /**
     * Whether the current runner has been declared capable of Filament GPU pixel readback.
     *
     * Reads [GPU_READBACK_FLAG] from, in order of precedence:
     * 1. the instrumentation runner arguments
     *    (`-Pandroid.testInstrumentationRunnerArguments.gpuReadback=true`), then
     * 2. a JVM system property of the same name.
     */
    val isGpuReadbackAvailable: Boolean
        get() {
            val fromArgs = runCatching {
                InstrumentationRegistry.getArguments().getString(GPU_READBACK_FLAG)
            }.getOrNull()
            val raw = fromArgs ?: System.getProperty(GPU_READBACK_FLAG)
            return raw?.trim()?.lowercase() in TRUTHY
        }

    /**
     * Skips the calling test (JUnit assumption) unless the runner advertises working GPU
     * pixel readback.
     *
     * Call this from an `@Before` method of any class that uses [RenderTestHarness].
     * On an incapable runner the test is reported as **skipped**, never **failed** and
     * never silently **ignored** — so the suite stays honest about what it could not
     * verify. See [#803](https://github.com/sceneview/sceneview/issues/803) for the
     * underlying harness limitation.
     */
    fun assumeGpuReadbackAvailable() {
        assumeTrue(
            "Skipped: Filament GPU pixel readback unavailable on this runner " +
                "(async readPixels callback never fires on the Apple-Silicon / SwiftShader " +
                "emulator — see #803). Run on a hardware-GPU device, or pass " +
                "-Pandroid.testInstrumentationRunnerArguments.$GPU_READBACK_FLAG=true.",
            isGpuReadbackAvailable
        )
    }
}
