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
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

/**
 * Demonstrates model animation playback controls: play/pause, speed, and loop mode.
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
    // Always call rememberEnvironment so the composable structure stays stable across
    // recompositions; the HDR result is preferred when available, otherwise the neutral
    // default environment fills in while the HDR is loading.
    val hdrEnvironment = rememberHDREnvironment(
        environmentLoader,
        "environments/studio_warm_2k.hdr",
        createSkybox = false,
    )
    val fallbackEnvironment = rememberEnvironment(environmentLoader)
    val activeEnvironment = hdrEnvironment ?: fallbackEnvironment

    DemoScaffold(
        title = "Animation",
        onBack = onBack,
        controls = {
            // Play / Pause
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

            // Speed slider
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

            // Loop toggle
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
                    autoAnimate = isPlaying,
                    animationSpeed = speed,
                    animationLoop = loop
                )
            }
        }
    }
}
