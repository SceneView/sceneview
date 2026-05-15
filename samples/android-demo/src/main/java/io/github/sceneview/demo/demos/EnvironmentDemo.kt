package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneView
import io.github.sceneview.createEnvironment
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.R
import io.github.sceneview.demo.LoadingScrim
import io.github.sceneview.demo.rememberHeroOrbitCameraManipulator
import io.github.sceneview.environment.Environment
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

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
    data class IntensityOption(val label: String, val lux: Float?)

    val environments = remember {
        listOf(
            EnvOption("Studio", "environments/studio_2k.hdr"),
            EnvOption("Studio Warm", "environments/studio_warm_2k.hdr"),
            EnvOption("Outdoor Cloudy", "environments/outdoor_cloudy_2k.hdr"),
            EnvOption("Chinese Garden", "environments/chinese_garden_2k.hdr"),
            EnvOption("Sunset", "environments/sunset_2k.hdr"),
            EnvOption("Rooftop Night", "environments/rooftop_night_2k.hdr"),
            EnvOption("Night Sky", "environments/night_sky_2k.hdr")
        )
    }
    // `lux = null` keeps the v4.1.0 balanced 10k default (#1075). The 30k preset
    // demonstrates the `indirectLightApply` override (#1124) for bright outdoor HDRIs.
    val intensities = remember {
        listOf(
            IntensityOption("Default (10k)", null),
            IntensityOption("Bright (30k)", 30_000f),
            IntensityOption("Dim (3k)", 3_000f),
        )
    }
    var selectedEnv by remember { mutableStateOf(environments[0]) }
    var selectedIntensity by remember { mutableStateOf(intensities[0]) }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)

    // Recreate the environment when the HDR or the intensity override changes.
    // `indirectLightApply` is the v4.1.0 hook (#1124) that lets callers tweak the
    // Filament `IndirectLight.Builder` without copying the buffer-loading boilerplate.
    val environment: Environment = remember(environmentLoader, selectedEnv, selectedIntensity) {
        environmentLoader.createHDREnvironment(
            assetFileLocation = selectedEnv.file,
            indirectLightApply = {
                selectedIntensity.lux?.let { intensity(it) }
            }
        ) ?: createEnvironment(environmentLoader)
    }
    DisposableEffect(environment) {
        onDispose { environmentLoader.destroyEnvironment(environment) }
    }

    val modelInstance = rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")

    // Camera orbits the helmet; the helmet itself stays fixed — otherwise an A/B
    // comparison between HDRs is unreadable because both the reflected highlight and
    // the helmet's face would rotate simultaneously. Static pose + moving camera lets
    // the viewer see how each HDR paints the same surface from different angles.
    val cameraManipulator = rememberHeroOrbitCameraManipulator(
        trigger = modelInstance != null,
        radius = 2.0f,
        yHeight = 0.3f,
        durationMillis = 18_000,
        staticYaw = 30f,
    )

    DemoScaffold(
        title = stringResource(R.string.demo_environment_title),
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "IBL Intensity (indirectLightApply)",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                intensities.forEach { option ->
                    FilterChip(
                        selected = selectedIntensity == option,
                        onClick = { selectedIntensity = option },
                        label = { Text(option.label) }
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
                cameraManipulator = cameraManipulator,
            ) {
                modelInstance?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        scaleToUnits = 0.5f,
                    )
                }
            }
            LoadingScrim(loading = modelInstance == null, label = "Loading helmet…")
        }
    }
}
