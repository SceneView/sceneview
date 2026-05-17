package io.github.sceneview.demo.demos

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.LoadingScrim
import io.github.sceneview.demo.R
import io.github.sceneview.demo.rememberFirstFrameState
import io.github.sceneview.demo.rememberHeroOrbitCameraManipulator
import io.github.sceneview.demo.sketchfab.SampleAssets
import io.github.sceneview.demo.sketchfab.SketchfabAssetResolver
import io.github.sceneview.demo.sketchfab.SketchfabSlug
import io.github.sceneview.environment.rememberHDREnvironment
import io.github.sceneview.math.Position
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

/**
 * Streamed showcase of the KHR_materials_* PBR extension family — sheen,
 * transmission, iridescence — sourced from Sketchfab's CC-BY PBR catalogue
 * (the same curated set declared in [SampleAssets]'s `materials` category).
 *
 * The previous version of this demo (parity with the iOS placeholder) was a
 * 5-sphere metallic/roughness spectrum that didn't actually exercise any of
 * the modern glTF material extensions. Stage 2 replaces it with curated
 * extension-bearing models so the demo answers "what do KHR_materials_sheen
 * / _transmission / _iridescence look like in SceneView?" at a glance.
 *
 * Lighting uses the studio HDR so reflections + IBL hit the streamed
 * extension materials cleanly — sheen, transmission, and iridescence all
 * rely heavily on environment lighting to read.
 *
 * Honours the umbrella's hard rules:
 *   - **No Sketchfab WebView / external link.** Local file URLs only.
 *   - **No network required to render something useful.** Empty key / cold
 *     cache → resolver stages the bundled fallback for each slug. The
 *     demo's fallback assets do not carry the actual extension materials
 *     (those are author-controlled) but they keep the viewport non-empty
 *     so the comparison UX stays understandable.
 *
 * @see SampleAssets for the curated `materials` category.
 * @see SketchfabAssetResolver for the resolve / fallback contract.
 */
@Composable
fun MaterialsDemo(onBack: () -> Unit) {
    val context = LocalContext.current
    val resolver = remember(context) { SketchfabAssetResolver.getInstance(context) }

    // Three curated `materials` slugs — sheen, transmission, iridescence.
    // Stage 2 keeps the count low so the offline-fallback footprint stays
    // bounded; Stage 3 will expand once a CI maintenance cron validates each
    // slug weekly.
    val slugs = remember { SampleAssets.byCategory["materials"].orEmpty() }
    var selectedIndex by remember { mutableStateOf(0) }
    val selectedSlug = slugs.getOrNull(selectedIndex)

    LaunchedEffect(resolver) {
        runCatching { resolver.prefetchAll("materials") }
    }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)

    // Studio HDR — extension materials (sheen / transmission / iridescence)
    // are heavily IBL-dependent and read flatly under default ambient light.
    // Skybox enabled so the user can see the same environment the materials
    // are reflecting / refracting.
    val hdrEnvironment = rememberHDREnvironment(
        environmentLoader,
        "environments/studio_2k.hdr",
        createSkybox = true,
    )
    val fallbackEnvironment = rememberEnvironment(environmentLoader)
    val activeEnvironment = hdrEnvironment ?: fallbackEnvironment

    val resolvedPath: String? by produceState<String?>(
        initialValue = null,
        key1 = resolver,
        key2 = selectedSlug?.uid,
    ) {
        value = null
        val slug = selectedSlug ?: return@produceState
        value = runCatching { resolver.resolve(slug) }
            .getOrNull()
            ?.toURI()
            ?.toString()
    }

    val modelInstance = resolvedPath?.let { path ->
        rememberModelInstance(modelLoader, path)
    }

    val firstFrame = rememberFirstFrameState()

    DemoScaffold(
        title = stringResource(R.string.demo_materials_title),
        onBack = onBack,
        firstFrameRendered = firstFrame.rendered,
        controls = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                slugs.forEachIndexed { index, slug ->
                    FilterChip(
                        selected = index == selectedIndex,
                        onClick = { selectedIndex = index },
                        // displayName + the KHR_* tag give the user a clear
                        // read of "which extension am I looking at" without
                        // needing a second line of copy. tags is a
                        // List<String> from the registry — we surface the
                        // first one (`KHR_materials_*`).
                        label = { Text(slug.displayName) },
                    )
                }
            }
            // Extension tag — the registry's `tags[0]` is the
            // `KHR_materials_*` extension name. Displayed below the chips so
            // the user can map the chip choice to the glTF extension being
            // demonstrated.
            selectedSlug?.let { slug ->
                val extension = slug.tags.firstOrNull().orEmpty()
                if (extension.isNotBlank()) {
                    Text(
                        text = extension,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(
                    text = stringResource(R.string.demo_materials_credit, slug.author),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
    ) {
        val cameraManipulator = rememberHeroOrbitCameraManipulator(
            trigger = modelInstance != null,
            radius = 1.2f,
            yHeight = 0f,
            durationMillis = 18_000,
        )
        Box(modifier = Modifier.fillMaxSize()) {
            SceneView(
                modifier = Modifier.fillMaxSize(),
                onFrame = firstFrame.onFrame,
                engine = engine,
                modelLoader = modelLoader,
                environmentLoader = environmentLoader,
                environment = activeEnvironment,
                cameraManipulator = cameraManipulator,
            ) {
                val instance = modelInstance
                val slug = selectedSlug
                if (instance != null && slug != null) {
                    ModelNode(
                        modelInstance = instance,
                        scaleToUnits = slug.scaleToUnits,
                        centerOrigin = Position(0f, 0f, 0f),
                    )
                }
            }
            LoadingScrim(
                loading = modelInstance == null,
                label = stringResource(R.string.demo_materials_loading),
            )
        }
    }
}
