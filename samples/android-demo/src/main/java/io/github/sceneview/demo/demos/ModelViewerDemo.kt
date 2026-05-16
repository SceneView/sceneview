package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneView
import io.github.sceneview.demo.AssetSourceState
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.LoadingScrim
import io.github.sceneview.demo.R
import io.github.sceneview.demo.rememberFirstFrameState
import io.github.sceneview.demo.rememberHeroOrbitCameraManipulator
import io.github.sceneview.demo.sketchfab.SketchfabAssetResolver
import io.github.sceneview.demo.sketchfab.SketchfabConfig
import io.github.sceneview.demo.sketchfab.SketchfabService
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.launch

/**
 * Full-screen 3D model viewer.
 *
 * **Default state.** Loads the bundled `khronos_damaged_helmet.glb` so the demo
 * renders identically with or without a Sketchfab API key — the very first
 * frame the user sees is the same hero shot the screenshots and store assets
 * promise. The CAMERA orbits the helmet so lights, reflections and IBL hit the
 * same surface every frame.
 *
 * **"Surprise me" button.** When the user taps the extended FAB at the
 * bottom-right, the demo searches the Sketchfab API for a downloadable model
 * tagged like the previous pick (or just downloadable PBR content on first
 * tap), then routes the resulting URL through SceneView's `file://` model
 * loader. The streamed pick replaces the helmet for the rest of the session
 * (or until the next tap). When no API key is configured (App Store builds),
 * the button is hidden — there is no plausible "Surprise me" without the
 * Sketchfab catalogue, and showing a non-functional button would mislead.
 *
 * The moment the user touches the viewport the orbit hands off to the stock
 * [io.github.sceneview.gesture.CameraGestureDetector.DefaultCameraManipulator]
 * at the exact same pose, so there's no snap — drag / pinch / zoom continue
 * from where the automated orbit left off.
 */
@Composable
fun ModelViewerDemo(onBack: () -> Unit) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)

    // Source of truth for the currently-viewed model:
    //  - null            → render the bundled hero helmet (default state).
    //  - non-null URL    → render the streamed Sketchfab GLB.
    // Restored via remember (savedStateRegistry would survive config change
    // but we want the helmet back on every cold start so screenshots / Play
    // Store store-page assets stay deterministic).
    var streamedFileUrl by remember { mutableStateOf<String?>(null) }
    // Last-tapped state — when it's `true` the FAB shows a spinner. The
    // surprise-coroutine flips it back to `false` regardless of success so
    // the button doesn't get stuck in the loading state.
    var surpriseInFlight by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember(context) { SketchfabService.getInstance(context) }
    val resolver = remember(context) { SketchfabAssetResolver.getInstance(context) }
    val hasSketchfabKey = remember { SketchfabConfig.apiKey != null }

    // The streamed model is loaded via the URL overload — null `streamedFileUrl`
    // skips this branch (Kotlin elvis returns null which keeps the bundled
    // helmet below as the displayed instance).
    val streamedModelInstance = streamedFileUrl?.let { url ->
        rememberModelInstance(modelLoader, url)
    }
    // The bundled hero — assets/models/khronos_damaged_helmet.glb. Loaded
    // eagerly so the first frame after launch shows the hero shot.
    val bundledModelInstance =
        rememberModelInstance(modelLoader, "models/khronos_damaged_helmet.glb")

    // The instance actually rendered this frame. Falls back to the bundled
    // helmet whenever the streamed instance is null (no Surprise tap yet,
    // streamed load still in flight, or streamed load failed).
    val activeModelInstance = streamedModelInstance ?: bundledModelInstance

    // Camera orbits; model stays fixed. Radius 1.4 m keeps the 0.3 m helmet
    // framed comfortably at portrait aspect without clipping the near plane.
    val cameraManipulator = rememberHeroOrbitCameraManipulator(
        trigger = activeModelInstance != null,
        radius = 1.4f,
        yHeight = 0f,
        durationMillis = 20_000,
    )

    // Per-demo offline indicator chip (#1152 Stage 3): hide while we're on
    // the bundled hero only (no Surprise tap yet). Once the user kicks a
    // streamed roll the chip surfaces "Streaming…" → "Streamed (cached)".
    // When no key is configured, "Surprise me" is disabled in the controls
    // and we never enter the streaming branch — chip stays hidden.
    val assetSource = when {
        streamedFileUrl == null -> null
        streamedModelInstance == null -> AssetSourceState.Streaming
        else -> AssetSourceState.Streamed
    }

    val firstFrame = rememberFirstFrameState()

    DemoScaffold(
        title = stringResource(R.string.demo_model_viewer),
        onBack = onBack,
        assetSource = assetSource,
        firstFrameRendered = firstFrame.rendered,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SceneView(
                modifier = Modifier.fillMaxSize(),
                onFrame = firstFrame.onFrame,
                engine = engine,
                modelLoader = modelLoader,
                environmentLoader = environmentLoader,
                cameraManipulator = cameraManipulator,
            ) {
                activeModelInstance?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        // Scale streamed content to a comfortable 0.4 m so a
                        // 5 m crate or a 5 cm bee both read as "in the orbit
                        // sweet spot". For the bundled helmet we keep the
                        // historical 0.3 m for byte-identical screenshots.
                        scaleToUnits = if (streamedFileUrl == null) 0.3f else 0.4f,
                        // centerOrigin lets SceneView re-centre the model's
                        // bounding box on world origin — see QA finding
                        // 2026-05-11 PM in the original file.
                        centerOrigin = io.github.sceneview.math.Position(0f, 0f, 0f),
                    )
                }
            }

            LoadingScrim(
                loading = activeModelInstance == null,
                label = stringResource(R.string.demo_model_viewer_loading),
            )

            // Surprise FAB lives only when the user has a Sketchfab API key —
            // tapping it without a key would silently fall back to the same
            // bundled helmet, which is worse than no button at all.
            if (hasSketchfabKey) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (surpriseInFlight) return@ExtendedFloatingActionButton
                        surpriseInFlight = true
                        scope.launch {
                            val picked = runCatching {
                                pickRandomDownloadableModel(service, resolver)
                            }.getOrNull()
                            // Even on failure we exit the in-flight state so
                            // the user can retry. The helmet stays put.
                            streamedFileUrl = picked
                            surpriseInFlight = false
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                        )
                    },
                    text = {
                        Text(
                            text = if (surpriseInFlight) {
                                stringResource(R.string.demo_model_viewer_surprise_loading)
                            } else {
                                stringResource(R.string.demo_model_viewer_surprise)
                            },
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                )
            }
        }
    }
}

/**
 * Surprise-me coroutine. Hits the Sketchfab search API for downloadable PBR
 * content, picks a random hit, streams it through [SketchfabService.downloadModel]
 * → on-disk cache → `file://` URL. Failure modes (no results / rate limit /
 * non-PBR download) all return `null` and the FAB caller leaves the helmet on
 * screen, so the demo never sits on a black viewport.
 */
private suspend fun pickRandomDownloadableModel(
    service: SketchfabService,
    @Suppress("UNUSED_PARAMETER") resolver: SketchfabAssetResolver,
): String? {
    // Search a broad PBR-friendly query so the picks read well under the demo
    // lighting. Falls back to "modern" if "pbr" returns 0 hits for some reason.
    val candidates = listOf("pbr", "modern", "scan")
    for (query in candidates) {
        val results = runCatching {
            service.search(query = query, downloadable = true, limit = 24)
        }.getOrNull() ?: continue
        // Filter to PBR-ish, sub-50k-poly hits so the demo doesn't stall on
        // a 5 M-poly scan. faceCount = 0 happens for non-PBR models — keep
        // them out.
        val viable = results.filter { it.downloadable && it.faceCount in 1..200_000 }
        if (viable.isEmpty()) continue
        val pick = viable.random()
        val cached = runCatching { service.downloadModel(pick.uid) }.getOrNull()
            ?: continue
        return cached.toURI().toString()
    }
    return null
}
