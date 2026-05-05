@file:OptIn(ExperimentalSceneViewApi::class)

package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sceneview.ExperimentalSceneViewApi
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.utils.DebugOverlay
import io.github.sceneview.utils.rememberDebugStats

/**
 * Demonstrates [DebugOverlay] and [rememberDebugStats] — a real-time performance stats overlay
 * showing FPS, frame time, and node count.
 *
 * The demo doubles as a stress-test: presets spawn 1, 10, 100, 500 or 1000 procedural
 * [SphereNode]s arranged in a 10×10×N grid, so users can watch the FPS counter drop in real time
 * and prove the overlay is reactive.
 *
 * The overlay is a standard Compose composable placed alongside (not inside) the [SceneView].
 * A toggle switch controls its visibility.
 */
@Composable
fun DebugOverlayDemo(onBack: () -> Unit) {
    var showOverlay by remember { mutableStateOf(true) }
    var nodeCount by remember { mutableIntStateOf(1) }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val stats = rememberDebugStats()

    DemoScaffold(
        title = "Debug Overlay",
        onBack = onBack,
        controls = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Nodes: $nodeCount",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(1, 10, 100, 500, 1000).forEach { preset ->
                        OutlinedButton(
                            onClick = { nodeCount = preset },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 8.dp,
                                vertical = 4.dp
                            )
                        ) {
                            Text(preset.toString(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedButton(
                        onClick = { nodeCount = 1 },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp,
                            vertical = 4.dp
                        )
                    ) {
                        Text("Reset", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = showOverlay,
                            onValueChange = { showOverlay = it },
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show Overlay", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = showOverlay, onCheckedChange = null)
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SceneView(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                environmentLoader = environmentLoader,
                cameraManipulator = rememberCameraManipulator(),
                onFrame = { frameTimeNanos ->
                    stats.onFrame(frameTimeNanos, nodeCount = nodeCount)
                }
            ) {
                // Spawn `nodeCount` procedural spheres in a 10×10×N grid centered on origin.
                // Spacing is tuned so 1000 nodes (10×10×10) fit roughly within the default
                // camera frustum without escaping the viewport. Using SphereNode (a built-in
                // SDK primitive) avoids any GLB I/O — the stress comes purely from the
                // rendering pipeline (draw calls + transforms + frustum culling).
                //
                // TODO(audit-2026-05-04): SDK doesn't expose triangle / drawcall / GPU
                // memory counters via the DebugStats API — surface those if/when the
                // engine adds an ENGINE_*_COUNT hook so the overlay can become richer.
                repeat(nodeCount) { i ->
                    key(i) {
                        val pos = remember(i) {
                            Position(
                                x = ((i % 10) - 5) * 0.18f,
                                y = (((i / 10) % 10) - 5) * 0.18f,
                                z = -((i / 100)) * 0.18f,
                            )
                        }
                        SphereNode(
                            radius = 0.04f,
                            position = pos
                        )
                    }
                }
            }

            if (showOverlay) {
                DebugOverlay(
                    stats = stats,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )
            }
        }
    }
}
