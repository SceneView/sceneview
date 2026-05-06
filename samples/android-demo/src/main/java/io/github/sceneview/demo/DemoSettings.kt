package io.github.sceneview.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Global demo-app settings that govern "showcase" behaviour vs deterministic "QA" behaviour.
 *
 * By default every demo that can do so plays a smooth camera-orbit idle animation, runs GLB
 * skeletal animations, and shows polished transitions — the "wow" state a first-time user
 * sees when browsing the sample app. QA tests flip [qaMode] to `true` so those animations
 * go dormant and screenshot captures are deterministic (same frame every time).
 *
 * Wire a demo control to [qaMode] when it owns an auto-rotate / auto-orbit / idle
 * animation that would otherwise produce different pixels on every run.
 *
 * **Turning QA mode on at runtime** — long-press the top-bar title of any demo (see
 * [DemoScaffold]) to open the settings drawer with a toggle. Instrumentation tests that
 * need determinism can set it programmatically before launching a demo:
 *
 * ```kotlin
 * DemoSettings.qaMode = true
 * ```
 */
object DemoSettings {
    /**
     * `true` = deterministic mode (no auto-orbit, no idle camera drift, no implicit
     * motion). `false` = full "wow" showcase mode. Default `false`.
     */
    var qaMode: Boolean by mutableStateOf(false)
}
