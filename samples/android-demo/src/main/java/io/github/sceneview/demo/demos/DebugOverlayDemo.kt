@file:OptIn(ExperimentalSceneViewApi::class)

package io.github.sceneview.demo.demos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sceneview.ExperimentalSceneViewApi
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.utils.rememberDebugStats
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Demonstrates [io.github.sceneview.utils.DebugOverlay] and
 * [io.github.sceneview.utils.rememberDebugStats] — a real-time performance stats overlay
 * showing FPS, frame time, node count, estimated triangles, and a rolling FPS sparkline.
 *
 * The demo doubles as a stress-test:
 *  - Presets spawn 1, 10, 100, 500, or 1000 procedural [SphereNode]s arranged in a
 *    10×10×N grid.
 *  - **Stress test** ramps from 1 → 5000 nodes over 10 s so users can watch the FPS
 *    sparkline collapse in real time.
 *
 * Auto-fit camera distance is recomputed whenever the spawn target changes so a single
 * sphere is just as visible as the full 10×10×10 grid.
 *
 * Spawn is **progressive**: nodes are added in batches of `SPAWN_BATCH_SIZE` per frame
 * via a `LaunchedEffect`, with a "Spawning X / N…" progress bar — preset buttons stay
 * disabled until the spawn finishes to avoid double-clicks creating duplicate work.
 *
 * The overlay is always visible (it *is* the demo).
 */
@Composable
fun DebugOverlayDemo(onBack: () -> Unit) {
    // Total node-count target the user requested. Spawn ramps `currentCount` up to this.
    var targetCount by remember { mutableIntStateOf(1) }
    var currentCount by remember { mutableIntStateOf(0) }
    var stressRunning by remember { mutableStateOf(false) }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val stats = rememberDebugStats()

    // Rolling FPS history — 120 samples (~2 s of real-world data at 60 fps, but the
    // sparkline width matters more than wall-clock time so we just show the most recent
    // 120 frame samples). Updated each frame; bypasses Compose state to avoid one
    // recomposition per frame — the overlay reads it on its own 250 ms tick instead.
    val fpsHistory = remember { FloatArray(FPS_HISTORY_SIZE) }
    var fpsHead by remember { mutableIntStateOf(0) }
    var historySize by remember { mutableIntStateOf(0) }

    // Auto-fit camera. Recomputed on each `targetCount` change before the spawn ramp
    // begins, so the very first frame already shows a sensible framing instead of
    // popping after the user manually zooms.
    val cameraDistance = remember(targetCount) { autoFitDistance(targetCount) }
    val cameraNode = rememberCameraNode(engine)
    SideEffect {
        cameraNode.position = Position(z = cameraDistance)
    }

    // Progressive spawn: incrementally bring `currentCount` toward `targetCount` so the
    // user sees nodes appear in real time instead of staring at a frozen UI thread.
    LaunchedEffect(targetCount) {
        if (currentCount > targetCount) {
            // Shrinking — drop instantly (cheap, no frame-spread needed).
            currentCount = targetCount
            return@LaunchedEffect
        }
        while (isActive && currentCount < targetCount) {
            val next = (currentCount + SPAWN_BATCH_SIZE).coerceAtMost(targetCount)
            currentCount = next
            // ~16 ms ≈ one frame at 60 Hz. Yields the dispatcher so Filament can render
            // the just-added batch before we add the next one — that's what gives the
            // visible "filling up" effect.
            delay(SPAWN_FRAME_DELAY_MS)
        }
    }

    DemoScaffold(
        title = "Debug Overlay",
        onBack = onBack,
        controls = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Nodes: $currentCount / $targetCount",
                    style = MaterialTheme.typography.labelLarge
                )

                // Show a thin progress bar while we ramp up so users have feedback.
                val spawning = currentCount < targetCount
                if (spawning) {
                    LinearProgressIndicator(
                        progress = {
                            if (targetCount > 0) currentCount.toFloat() / targetCount else 1f
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Spawning $currentCount / $targetCount…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Disable preset buttons during spawn / stress test so we don't
                    // race ourselves into duplicated targets.
                    val controlsEnabled = !spawning && !stressRunning
                    listOf(1, 10, 100, 500, 1000).forEach { preset ->
                        OutlinedButton(
                            onClick = { targetCount = preset },
                            enabled = controlsEnabled,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(preset.toString(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedButton(
                        onClick = { targetCount = 1 },
                        enabled = controlsEnabled,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Reset", style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Stress test: ramp 1 → 5000 over STRESS_DURATION_MS so the sparkline
                // shows a textbook performance cliff. The button toggles itself off
                // when the ramp completes (see LaunchedEffect below).
                Button(
                    onClick = {
                        stressRunning = !stressRunning
                        if (stressRunning) {
                            // Start fresh from a small count so the ramp is visible.
                            targetCount = 1
                        }
                    },
                    enabled = !stressRunning || stressRunning, // always enabled (toggle)
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        if (stressRunning) "Stop stress test" else "Stress test (1 → 5000)",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    ) {
        // Drive the stress-test ramp from a coroutine so the UI stays responsive.
        LaunchedEffect(stressRunning) {
            if (!stressRunning) return@LaunchedEffect
            val start = System.nanoTime()
            while (isActive && stressRunning) {
                val elapsedMs = (System.nanoTime() - start) / 1_000_000L
                val progress = (elapsedMs.toFloat() / STRESS_DURATION_MS).coerceIn(0f, 1f)
                // Linear ramp: 1 → STRESS_TARGET.
                val want = (1 + (STRESS_TARGET - 1) * progress).toInt()
                if (want != targetCount) targetCount = want
                if (progress >= 1f) {
                    stressRunning = false
                    break
                }
                delay(STRESS_TICK_MS)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            SceneView(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                environmentLoader = environmentLoader,
                cameraNode = cameraNode,
                cameraManipulator = rememberCameraManipulator(),
                onFrame = { frameTimeNanos ->
                    stats.onFrame(frameTimeNanos, nodeCount = currentCount)
                    // Push the just-computed FPS into the ring buffer. We read
                    // stats.fps after onFrame() so we get the freshest value.
                    fpsHistory[fpsHead] = stats.fps
                    fpsHead = (fpsHead + 1) % fpsHistory.size
                    if (historySize < fpsHistory.size) historySize++
                }
            ) {
                // Spawn `currentCount` procedural spheres in a 10×10×N grid centered on
                // origin. SphereNode is a built-in SDK primitive (24×24 tessellation =
                // ~1152 tris each), so the perf cost comes purely from draw calls +
                // transforms + frustum culling — exactly what the stress test exercises.
                repeat(currentCount) { i ->
                    key(i) {
                        val pos = remember(i) {
                            Position(
                                x = ((i % 10) - 5) * NODE_SPACING,
                                y = (((i / 10) % 10) - 5) * NODE_SPACING,
                                z = -((i / 100)) * NODE_SPACING,
                            )
                        }
                        SphereNode(
                            radius = NODE_RADIUS,
                            position = pos
                        )
                    }
                }
            }

            DebugOverlay(
                stats = stats,
                fpsHistory = fpsHistory,
                fpsHistoryHead = fpsHead,
                fpsHistorySize = historySize,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Local extended overlay                                                     */
/* -------------------------------------------------------------------------- */

/**
 * Demo-local extended overlay — wraps the SDK's [io.github.sceneview.utils.DebugOverlay]
 * idea but adds:
 *  - a triangle-count estimate (1152 tris × node count for a 24×24 UV-sphere),
 *  - a rolling FPS sparkline (last [FPS_HISTORY_SIZE] samples).
 *
 * Lives in the demo (not the SDK) because the triangle estimate is sphere-specific —
 * the SDK doesn't expose engine-level draw-call/triangle counters yet, so we can't make
 * it accurate for arbitrary scenes.
 */
@Composable
private fun DebugOverlay(
    stats: io.github.sceneview.utils.DebugStats,
    fpsHistory: FloatArray,
    fpsHistoryHead: Int,
    fpsHistorySize: Int,
    modifier: Modifier = Modifier,
) {
    // Periodic recomposition so the text updates even when no other state changes.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            tick++
        }
    }
    @Suppress("UNUSED_EXPRESSION") tick

    Column(
        modifier = modifier
            .background(Color(0xAA000000.toInt()))
            .padding(8.dp)
    ) {
        val mono = FontFamily.Monospace
        val fps = stats.fps
        val fpsColor = when {
            fps >= 55f -> Color(0xFF4CAF50)
            fps >= 30f -> Color(0xFFFFC107)
            else -> Color(0xFFE53935)
        }
        BasicText(
            text = "FPS: %.1f".format(fps),
            style = TextStyle(color = fpsColor, fontSize = 12.sp, fontFamily = mono)
        )
        BasicText(
            text = "Frame: %.1f ms".format(stats.frameTimeMs),
            style = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = mono)
        )
        BasicText(
            text = "Nodes: %d".format(stats.nodeCount),
            style = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = mono)
        )
        // Estimated tris: SphereNode default tessellation is 24 stacks × 24 slices,
        // which yields 24*24*2 = 1152 triangles. Format with thousands grouping so a
        // 5760000-tri stress-test reads as "5,760,000" instead of a soup of digits.
        val tris = stats.nodeCount.toLong() * TRIS_PER_SPHERE
        BasicText(
            text = "Tris: %s".format(formatThousands(tris)),
            style = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = mono)
        )

        // Sparkline. 60-frame ring buffer scaled to 120 fps max so a 60 fps run sits at
        // half height. We don't draw the ring "as is" — we walk from the oldest sample
        // to the newest so the line moves left-to-right naturally.
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(top = 4.dp),
        ) {
            val w = size.width
            val h = size.height
            if (fpsHistorySize <= 1) return@Canvas
            val maxFps = 120f
            val path = Path()
            val n = fpsHistorySize
            val capacity = fpsHistory.size
            val start = if (n < capacity) 0 else fpsHistoryHead
            for (i in 0 until n) {
                val idx = (start + i) % capacity
                val v = fpsHistory[idx].coerceIn(0f, maxFps)
                val x = if (n == 1) 0f else w * i / (n - 1)
                val y = h - (v / maxFps) * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            // 60 fps reference line.
            val refY = h - (60f / maxFps) * h
            drawLine(
                color = Color(0x55FFFFFF),
                start = Offset(0f, refY),
                end = Offset(w, refY),
                strokeWidth = 1f,
            )
            drawPath(
                path = path,
                color = Color(0xFF4CAF50),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Tunables / helpers                                                         */
/* -------------------------------------------------------------------------- */

private const val NODE_SPACING = 0.18f
private const val NODE_RADIUS = 0.04f

/** SphereNode default tessellation = 24 stacks × 24 slices → 1152 triangles. */
private const val TRIS_PER_SPHERE = 1152L

/** Spawn ramp tunables — visible "filling up" without dropping the UI thread. */
private const val SPAWN_BATCH_SIZE = 16
private const val SPAWN_FRAME_DELAY_MS = 16L

/** FPS sparkline — 120 samples at 60 fps ≈ 2 s of history. */
private const val FPS_HISTORY_SIZE = 120

/** Stress test: linear ramp 1 → 5000 over 10 s. */
private const val STRESS_TARGET = 5000
private const val STRESS_DURATION_MS = 10_000L
private const val STRESS_TICK_MS = 50L

/**
 * Camera distance that frames a 10×10×N sphere grid (sphere radius [NODE_RADIUS],
 * spacing [NODE_SPACING]) with ~1.2× padding.
 *
 * Approach:
 *  1. Compute the half-extents of the bounding box on each axis from the spawn pattern.
 *  2. Take the max half-extent → bounding-sphere radius.
 *  3. Camera distance = radius / tan(fov/2) × 1.2 (padding).
 *
 * Filament's default vertical FOV is ~45°. We're conservative and use 35° because the
 * actual aspect ratio shrinks the visible cone on phones in portrait mode.
 */
private fun autoFitDistance(nodeCount: Int): Float {
    if (nodeCount <= 0) return 1.5f
    // Grid layout: x in [0..9] (mod 10), y in [0..9] (i/10 mod 10), z layer = i/100.
    // For nodeCount n: zLayers = ceil(n / 100). For 1 node: just one cell, but we still
    // want some breathing room — clamp the smallest extent so a single sphere isn't
    // glued to the lens.
    val xExtent = if (nodeCount >= 10) 9 else (nodeCount - 1)
    val yExtent = if (nodeCount >= 100) 9 else (((nodeCount - 1) / 10).coerceAtLeast(0))
    val zLayers = ((nodeCount - 1) / 100).coerceAtLeast(0)
    val halfX = (xExtent * NODE_SPACING) / 2f + NODE_RADIUS
    val halfY = (yExtent * NODE_SPACING) / 2f + NODE_RADIUS
    val halfZ = (zLayers * NODE_SPACING) / 2f + NODE_RADIUS
    val radius = sqrt(halfX * halfX + halfY * halfY + halfZ * halfZ)
        .coerceAtLeast(NODE_RADIUS * 4f)
    // 35° vertical FOV (radians) → tan(17.5°) ≈ 0.3153.
    val fovRadHalf = Math.toRadians(17.5).toFloat()
    val raw = radius / tan(fovRadHalf) * 1.2f
    return max(0.6f, raw)
}

private fun formatThousands(v: Long): String {
    if (v < 1000) return v.toString()
    // Build "1,234,567" without depending on a locale (deterministic across devices).
    val s = v.toString()
    val sb = StringBuilder()
    val first = s.length % 3
    if (first > 0) sb.append(s, 0, first)
    var i = first
    while (i < s.length) {
        if (sb.isNotEmpty()) sb.append(',')
        sb.append(s, i, i + 3)
        i += 3
    }
    return sb.toString()
}
