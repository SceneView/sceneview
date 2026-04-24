package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sceneview.ExperimentalSceneViewApi
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.environment.rememberHDREnvironment
import io.github.sceneview.node.ModelNode as ModelNodeImpl
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

/**
 * Demonstrates model animation playback controls: play/pause, speed, and loop mode.
 *
 * The `autoAnimate` parameter on `ModelNode` is only read once at node creation, and
 * the composable's reactive `animationName` path doesn't re-key on speed/loop
 * changes — so we drive the animation state imperatively through a `LaunchedEffect`
 * that watches (isPlaying, speed, loop) and calls `playAnimation` / `stopAnimation`
 * on a captured node reference. That gives the three chips a real effect.
 */
@OptIn(ExperimentalSceneViewApi::class)
@Composable
fun AnimationDemo(onBack: () -> Unit) {
    var isPlaying by remember { mutableStateOf(true) }
    var speed by remember { mutableFloatStateOf(1f) }
    var loop by remember { mutableStateOf(true) }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val modelInstance = rememberModelInstance(modelLoader, "models/animated_dragon.glb")

    // HDR environment for IBL — disable skybox so the studio lightbox doesn't dominate
    // the viewport. We only want the lighting contribution, not the wrap-around image.
    val hdrEnvironment = rememberHDREnvironment(
        environmentLoader,
        "environments/studio_warm_2k.hdr",
        createSkybox = false,
    )
    val fallbackEnvironment = rememberEnvironment(environmentLoader)
    val activeEnvironment = hdrEnvironment ?: fallbackEnvironment

    // Captured ref to the ModelNode once it's created — used by the LaunchedEffect
    // below to drive play/pause/speed/loop imperatively.
    val modelNodeRef = remember { androidx.compose.runtime.mutableStateOf<ModelNodeImpl?>(null) }

    // Reactive animation control: re-runs whenever any of the three controls change.
    // Relies on a stable node ref plus the modelInstance being loaded.
    LaunchedEffect(modelNodeRef.value, isPlaying, speed, loop) {
        val node = modelNodeRef.value ?: return@LaunchedEffect
        if (node.animationCount <= 0) return@LaunchedEffect
        // Stop any currently-playing animations before applying new settings.
        for (i in 0 until node.animationCount) node.stopAnimation(i)
        if (isPlaying) {
            for (i in 0 until node.animationCount) {
                node.playAnimation(i, speed = speed, loop = loop)
            }
        }
    }

    DemoScaffold(
        title = "Animation",
        onBack = onBack,
        controls = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Playback", style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = { isPlaying = !isPlaying }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Speed: ${"%.1f".format(speed)}x",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = speed,
                onValueChange = { speed = it },
                valueRange = 0.25f..3f,
                steps = 10
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = loop,
                    onClick = { loop = true },
                    label = { Text("Loop") }
                )
                FilterChip(
                    selected = !loop,
                    onClick = { loop = false },
                    label = { Text("Once") }
                )
            }
        }
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            environmentLoader = environmentLoader,
            environment = activeEnvironment,
            cameraManipulator = rememberCameraManipulator()
        ) {
            modelInstance?.let { instance ->
                ModelNode(
                    modelInstance = instance,
                    scaleToUnits = 0.5f,
                    // autoAnimate = false so the ModelNode init doesn't fire-and-forget
                    // all animations — we drive them from the LaunchedEffect above so
                    // the speed / loop / play-pause controls have real effect.
                    autoAnimate = false,
                    apply = { modelNodeRef.value = this },
                )
                // Clean up the ref when the node leaves composition.
                DisposableEffect(instance) {
                    onDispose { modelNodeRef.value = null }
                }
            }
        }
    }
}
