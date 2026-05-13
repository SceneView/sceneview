package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.SceneViewColors
import io.github.sceneview.sample.rememberMaterialInstance
import io.github.sceneview.math.Position
import io.github.sceneview.math.Position2
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Demonstrates [ShapeNode] — 2D polygon paths triangulated into 3D geometry.
 * A chip selector switches between triangle, star, and hexagon shapes.
 */
@Composable
fun ShapeDemo(onBack: () -> Unit) {
    var selectedShape by remember { mutableStateOf("Triangle") }

    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)

    val trianglePath = remember {
        listOf(
            Position2(0f, 0.5f),
            Position2(-0.5f, -0.3f),
            Position2(0.5f, -0.3f)
        )
    }

    val starPath = remember {
        buildList {
            val outerR = 0.5f
            val innerR = 0.2f
            for (i in 0 until 10) {
                val angle = (i * 36f - 90f) * (PI.toFloat() / 180f)
                val r = if (i % 2 == 0) outerR else innerR
                add(Position2(cos(angle) * r, sin(angle) * r))
            }
        }
    }

    val hexagonPath = remember {
        buildList {
            val r = 0.4f
            for (i in 0 until 6) {
                val angle = (i * 60f) * (PI.toFloat() / 180f)
                add(Position2(cos(angle) * r, sin(angle) * r))
            }
        }
    }

    // On-brand ramp — Primary blue (triangle), Accent purple (star), TintLight (hexagon).
    // Each instance is owned by `rememberMaterialInstance` (DisposableEffect-backed),
    // so the JNI handle dies the moment this composable leaves the composition
    // instead of waiting for MaterialLoader.destroy(). Same affordance as
    // `rememberModelInstance` — see #937.
    val triangleMaterial = rememberMaterialInstance(materialLoader, SceneViewColors.Primary)
    val starMaterial = rememberMaterialInstance(materialLoader, SceneViewColors.Accent)
    val hexagonMaterial = rememberMaterialInstance(materialLoader, SceneViewColors.TintLight)
    val shapeMaterials = mapOf(
        "Triangle" to triangleMaterial,
        "Star" to starMaterial,
        "Hexagon" to hexagonMaterial,
    )
    val shapePaths = mapOf(
        "Triangle" to trianglePath,
        "Star" to starPath,
        "Hexagon" to hexagonPath,
    )

    DemoScaffold(
        title = "All Shapes",
        onBack = onBack,
        controls = {
            Text("Shape", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Triangle", "Star", "Hexagon").forEach { shape ->
                    FilterChip(
                        selected = selectedShape == shape,
                        onClick = { selectedShape = shape },
                        label = { Text(shape) }
                    )
                }
            }
        }
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            materialLoader = materialLoader,
            // Dolly closer — shape sits at z = -1 with radius ≤ 0.5 m, default camera
            // at z = 4 rendered it tiny. z = 1.5 puts the shape at 2.5 m.
            cameraManipulator = rememberCameraManipulator(
                orbitHomePosition = Position(0f, 0f, 1.5f),
                targetPosition = Position(0f, 0f, -1f),
            ),
        ) {
            // key(selectedShape) forces a fresh ShapeNodeImpl when the user picks a
            // different polygon. The library's `ShapeNode` composable updates vertex /
            // index buffers in place via `updateGeometry`, but the underlying Filament
            // renderable is locked to `primitiveCount = 1` at construction time — so
            // swapping a 3-vertex triangle for a 10-point star kept the draw count at 3
            // and produced a black viewport. Re-keying rebuilds the whole renderable so
            // the new triangulation's draw count is picked up correctly.
            key(selectedShape) {
                ShapeNode(
                    polygonPath = shapePaths.getValue(selectedShape),
                    materialInstance = shapeMaterials.getValue(selectedShape),
                    position = Position(y = 0f, z = -1f)
                )
            }
        }
    }
}
