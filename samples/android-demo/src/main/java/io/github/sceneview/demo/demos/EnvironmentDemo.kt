package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneView
import io.github.sceneview.createEnvironment
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.DemoSettings
import io.github.sceneview.demo.LoadingScrim
import io.github.sceneview.environment.Environment
import io.github.sceneview.math.Rotation
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener

/**
 * Demonstrates HDR environment switching.
 *
 * A reflective model (damaged helmet) is loaded so the user can clearly see how each HDR
 * environment affects reflections and overall scene lighting. Selecting a different chip
 * recreates the [Environment] from the corresponding HDR asset file.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnvironmentDemo(onBack: () -> Unit) {
    data class EnvOption(val label: String, val file: String)

    val environments = remember {
        listOf(
            EnvOption("Studio", "environments/studio_2k.hdr"),
            EnvOption("Studio Warm", "environments/studio_warm_2k.hdr"),
            EnvOption("Outdoor Cloudy", "environments/outdoor_cloudy_2k.hdr"),
            EnvOption("Chinese Garden", "environments/chinese_garden_2k.hdr"),
            EnvOption("Sunset", "environments/sunset_2k.hdr"),
            EnvOption("Rooftop Night", "environments/rooftop_night_2k.hdr")
        )
    }
    var selectedEnv by remember { mutableStateOf(environments[0]) }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)

    // Recreate the environment each time the user selects a different HDR.
    val environment: Environment = remember(environmentLoader, selectedEnv) {
        environmentLoader.createHDREnvironment(assetFileLocation = selectedEnv.file)
            ?: createEnvironment(environmentLoader)
    }
    DisposableEffect(environment) {
        onDispose { environmentLoader.destroyEnvironment(environment) }
    }

    val modelInstance = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")

    // Slow hero rotation that starts only after the helmet finishes loading (no
    // snap-on-load) and pauses on the user's first gesture so manual orbit / pinch
    // / drag don't fight the animation.
    val (yaw, onGesture) = io.github.sceneview.demo.rememberPausableHeroYaw(
        trigger = modelInstance != null, durationMillis = 18_000, staticYaw = 30f,
    )

    DemoScaffold(
        title = "Environment Gallery",
        onBack = onBack,
        controls = {
            Text(
                text = "HDR Environment",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                environments.forEach { env ->
                    FilterChip(
                        selected = selectedEnv == env,
                        onClick = { selectedEnv = env },
                        label = { Text(env.label) }
                    )
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
                environment = environment,
                onGestureListener = rememberOnGestureListener(
                    onSingleTapUp = { _, _ -> onGesture() },
                    onDoubleTap = { _, _ -> onGesture() },
                    onScroll = { _, _, _, _ -> onGesture() },
                ),
            ) {
                modelInstance?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        scaleToUnits = 0.5f,
                        rotation = Rotation(y = yaw),
                    )
                }
            }
            LoadingScrim(loading = modelInstance == null, label = "Loading helmet…")
        }
    }
}
