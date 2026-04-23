package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.SceneViewColors
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import kotlin.math.cos
import kotlin.math.sin

/**
 * Demonstrates LineNode (single segment) and PathNode (polyline through points).
 * Controls toggle visibility and adjust the number of points in the path.
 */
@Composable
fun LinesPathsDemo(onBack: () -> Unit) {
    var showLine by remember { mutableStateOf(true) }
    var showPath by remember { mutableStateOf(true) }
    var pointCount by remember { mutableFloatStateOf(12f) }
    // Filament's LINES primitive is hardware-capped at 1 GPU pixel on most backends, so a line
    // "width" slider cannot drive the native line width. Instead we render per-point sphere beads
    // whose radius is controlled by this slider — visually equivalent to a thick dotted-line stroke
    // at the cost of one SphereNode per point. Set to 0 to disable the beads entirely.
    var lineWidth by remember { mutableFloatStateOf(0.03f) }

    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)

    DemoScaffold(
        title = "Lines & Paths",
        onBack = onBack,
        controls = {
            Text("Visibility", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(showLine, onClick = { showLine = !showLine }, label = { Text("Line") })
                FilterChip(showPath, onClick = { showPath = !showPath }, label = { Text("Path") })
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Path Points: ${pointCount.toInt()}",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = pointCount,
                onValueChange = { pointCount = it },
                valueRange = 3f..30f,
                steps = 27
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Line Width: ${"%.2f".format(lineWidth)} m",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = lineWidth,
                onValueChange = { lineWidth = it },
                valueRange = 0f..0.1f
            )
        }
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            materialLoader = materialLoader,
            cameraManipulator = rememberCameraManipulator()
        ) {
            // On-brand line colors — Primary blue for the line segment, Accent purple for the
            // path polyline. Same hero gradient as the product.
            val lineMaterial = remember(materialLoader) {
                materialLoader.createColorInstance(SceneViewColors.Primary)
            }
            val pathMaterial = remember(materialLoader) {
                materialLoader.createColorInstance(SceneViewColors.Accent)
            }

            // Single line segment — 1 px on most GPUs, so we also draw a pair of spheres at the
            // endpoints (scaled by lineWidth) to give the line a visible thickness.
            if (showLine) {
                val lineStart = Position(x = -1.0f, y = -0.5f, z = 0f)
                val lineEnd = Position(x = 1.0f, y = 0.5f, z = 0f)
                val lineOrigin = Position(x = 0f, y = 0.4f)
                LineNode(
                    start = lineStart,
                    end = lineEnd,
                    materialInstance = lineMaterial,
                    position = lineOrigin
                )
                if (lineWidth > 0f) {
                    // Interpolate beads along the segment — makes the line look thick / dotted
                    val beadCount = 10
                    for (i in 0..beadCount) {
                        val t = i.toFloat() / beadCount
                        SphereNode(
                            radius = lineWidth,
                            materialInstance = lineMaterial,
                            position = Position(
                                x = lineOrigin.x + lineStart.x + (lineEnd.x - lineStart.x) * t,
                                y = lineOrigin.y + lineStart.y + (lineEnd.y - lineStart.y) * t,
                                z = lineOrigin.z + lineStart.z + (lineEnd.z - lineStart.z) * t
                            )
                        )
                    }
                }
            }

            // Polyline path forming a spiral / circle pattern
            if (showPath) {
                val count = pointCount.toInt()
                val pathPoints = remember(count) {
                    (0 until count).map { i ->
                        val angle = (i.toFloat() / count) * 2f * Math.PI.toFloat()
                        val radius = 0.5f
                        Position(
                            x = cos(angle) * radius,
                            y = sin(angle) * radius,
                            z = 0f
                        )
                    }
                }
                val pathOrigin = Position(x = 0f, y = -0.3f)
                PathNode(
                    points = pathPoints,
                    closed = true,
                    materialInstance = pathMaterial,
                    position = pathOrigin
                )
                if (lineWidth > 0f) {
                    // Thick-path representation: one sphere bead at each path point, scaled by
                    // the lineWidth slider. Gives the path a visible stroke width independent
                    // of GPU line-rasterisation limits.
                    pathPoints.forEach { p ->
                        SphereNode(
                            radius = lineWidth,
                            materialInstance = pathMaterial,
                            position = Position(
                                x = pathOrigin.x + p.x,
                                y = pathOrigin.y + p.y,
                                z = pathOrigin.z + p.z
                            )
                        )
                    }
                }
            }
        }
    }
}
