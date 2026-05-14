package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.R
import io.github.sceneview.demo.sketchfab.SampleAssets
import io.github.sceneview.demo.sketchfab.SketchfabAssetResolver
import io.github.sceneview.demo.sketchfab.SketchfabSlug
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Personal-solar-system AR demo — 8 themed planets orbit around the user.
 *
 * On the first tracked AR frame, an [Anchor] is created at world origin (the camera's
 * pose at session-start in ARCore — i.e. wherever the user is standing). Eight model
 * instances are placed as children of an [AnchorNode] in a circle of radius 1.5 m,
 * at evenly-spaced angles (360° / 8 = 45° apart). Each model has:
 *
 * - its own **orbital speed** (between 0.05 and 0.30 rad/s) — slow at the outer
 *   "planets", fast at the inner ones, so the formation looks like a solar system
 *   rather than a rigid ring;
 * - either a **baked animation** (4 streamed animated creatures — butterfly,
 *   hummingbird, bee, koi — plus the bundled `animated_dragon`, oriented along the
 *   orbit tangent so they "fly the orbit") **or** a **local Y spin** (between 0.7
 *   and 2.0 rad/s) for static GLBs (helmet, lantern, toy car) so they feel alive
 *   without their own rig;
 * - a **distinct height** between -0.5 m and +0.5 m relative to the user's eye level.
 *
 * The user stays fixed in AR and can turn around to watch each model pass by.
 *
 * Animation is driven by `withFrameNanos` so the orbit advances at the display's
 * refresh rate. Per-recompose state (`orbitSeconds`) is hoisted via `mutableLongStateOf`
 * so the `ModelNode` positions/rotations recompute every frame.
 *
 * ### Streaming pipeline (Stage 2, issue #1152)
 *
 * Four of the eight planets are now streamed via [SketchfabAssetResolver] from the
 * curated `solar` category in [SampleAssets]. When [SketchfabConfig.apiKey] is
 * missing (App Store / first-launch / no-network) the resolver returns the bundled
 * fallback declared in the registry — so the demo always renders eight planets,
 * just sometimes with duplicate visuals. The four static GLBs (helmet, lantern,
 * toy car, dragon) are always read straight from `assets/models/` and have no
 * network dependency.
 *
 * This replaces the previous "2 duplicate dragons + 2 duplicate soldiers" workaround
 * that the bundle-only design was forced into (the old #978 audit flagged the dups
 * as a quality issue — the formation looked like clones rather than a solar system).
 */
private data class Planet(
    /**
     * Streamed Sketchfab slug (`solar` category) when non-null. The resolver
     * gives us either the downloaded GLB or the registered bundled fallback —
     * the demo just hands the resulting [File] to `rememberModelInstance`.
     */
    val streamedSlug: SketchfabSlug? = null,
    /**
     * Bundled asset path under `assets/`. Used when [streamedSlug] is null —
     * the three pure-bundled planets (helmet, lantern, toy car, animated dragon).
     */
    val bundledAssetPath: String? = null,
    val scaleToUnits: Float,
    val initialAngleRad: Float,
    val orbitSpeed: Float,   // rad/s around the user
    val spinSpeed: Float,    // rad/s local Y axis — ignored when hasBakedAnimation = true
    val height: Float,       // y offset, m
    // True when the model has its own baked animation (wing flap, walk cycle, etc.).
    // For these, we skip the local Y spin and instead orient the model along the
    // orbit tangent so it "flies/walks the orbit" naturally — the baked animation
    // does the rest of the movement.
    val hasBakedAnimation: Boolean,
) {
    init {
        require((streamedSlug == null) != (bundledAssetPath == null)) {
            "Planet must define exactly one of streamedSlug or bundledAssetPath."
        }
    }
}

// 8 themed planets — 4 streamed via the resolver (animated creatures from the
// `solar` category of SampleAssets), 4 bundled in the APK as offline fallback +
// to keep variety when the Sketchfab key is missing. The streamed entries fall
// back to their registered bundled GLB when offline, so the demo always renders
// eight orbiting models — no broken/black slots, no clones (the old 7-slot
// design duplicated the dragon and soldier just to fill the ring).
private val ORBITAL_PLANETS: List<Planet> = run {
    // The four streamed entries — order matches the SampleAssets `solar` category
    // (butterfly, hummingbird, bee, fish). We look them up by uid so a registry
    // re-ordering doesn't silently break the per-slot orbit tuning below.
    val butterfly = SampleAssets.byUid["78d8345fffe54a55ae62fadcf9eaece6"]
    val hummingbird = SampleAssets.byUid["9c54b62d3c2f4f0db8e7a3a8a78a4d92"]
    val bee = SampleAssets.byUid["6cb9f9a4c6e94f9da5b7c8a85e8a5c2d"]
    val koi = SampleAssets.byUid["d1ca3a3ddf3845abb98f4e5d62ae34c6"]

    // Per-slot tuning kept compatible with the previous bundle-only design — same
    // orbit radius (1.5 m), same height spread (±0.5 m), same speed range
    // (0.05–0.30 rad/s) so the visual rhythm doesn't change.
    listOf(
        // Slot 0 — bundled helmet, static spinning (hero anchor at angle 0).
        Planet(
            bundledAssetPath = "models/khronos_damaged_helmet.glb",
            scaleToUnits = 0.20f,
            initialAngleRad = 0f,
            orbitSpeed = 0.08f,
            spinSpeed = 0.7f,
            height = 0.0f,
            hasBakedAnimation = false,
        ),
        // Slot 1 — streamed butterfly, baked anim, flies the orbit tangent.
        Planet(
            streamedSlug = butterfly,
            scaleToUnits = 0.30f,
            initialAngleRad = 2f * PI.toFloat() / 8f * 1,
            orbitSpeed = 0.20f,
            spinSpeed = 0f,
            height = -0.2f,
            hasBakedAnimation = true,
        ),
        // Slot 2 — bundled lantern, static spinning.
        Planet(
            bundledAssetPath = "models/khronos_lantern.glb",
            scaleToUnits = 0.20f,
            initialAngleRad = 2f * PI.toFloat() / 8f * 2,
            orbitSpeed = 0.06f,
            spinSpeed = 0.9f,
            height = 0.4f,
            hasBakedAnimation = false,
        ),
        // Slot 3 — streamed hummingbird, baked anim.
        Planet(
            streamedSlug = hummingbird,
            scaleToUnits = 0.18f,
            initialAngleRad = 2f * PI.toFloat() / 8f * 3,
            orbitSpeed = 0.15f,
            spinSpeed = 0f,
            height = -0.4f,
            hasBakedAnimation = true,
        ),
        // Slot 4 — bundled toy car, static spinning (kid-friendly anchor).
        Planet(
            bundledAssetPath = "models/khronos_toy_car.glb",
            scaleToUnits = 0.20f,
            initialAngleRad = 2f * PI.toFloat() / 8f * 4,
            orbitSpeed = 0.10f,
            spinSpeed = 2.0f,
            height = 0.2f,
            hasBakedAnimation = false,
        ),
        // Slot 5 — streamed bee, baked anim.
        Planet(
            streamedSlug = bee,
            scaleToUnits = 0.12f,
            initialAngleRad = 2f * PI.toFloat() / 8f * 5,
            orbitSpeed = 0.25f,
            spinSpeed = 0f,
            height = -0.5f,
            hasBakedAnimation = true,
        ),
        // Slot 6 — bundled animated dragon (baked walk cycle).
        Planet(
            bundledAssetPath = "models/animated_dragon.glb",
            scaleToUnits = 0.30f,
            initialAngleRad = 2f * PI.toFloat() / 8f * 6,
            orbitSpeed = 0.05f,
            spinSpeed = 0f,
            height = 0.5f,
            hasBakedAnimation = true,
        ),
        // Slot 7 — streamed koi fish, baked anim (closes the ring).
        Planet(
            streamedSlug = koi,
            scaleToUnits = 0.35f,
            initialAngleRad = 2f * PI.toFloat() / 8f * 7,
            orbitSpeed = 0.30f,
            spinSpeed = 0f,
            height = 0.3f,
            hasBakedAnimation = true,
        ),
    )
}

private const val ORBIT_RADIUS = 1.5f

@Composable
fun OrbitalARDemo(onBack: () -> Unit) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val context = LocalContext.current

    // Resolve every streamed slug exactly once per composition. The resolver
    // returns the streamed GLB or the bundled fallback (we never block on the
    // network — see `SketchfabAssetResolver.resolve` Kdoc). The value flips from
    // `null` (download / fallback-copy still running on IO) to a real [File]
    // once the resolver returns. Bundled-only planets contribute `null` here
    // and go straight through `rememberModelInstance(assetPath)` below.
    //
    // ORBITAL_PLANETS is a `val` constant, so the call order of `produceState`
    // is stable across recompositions — Compose's positional memoisation stays
    // valid.
    val streamedFiles: List<File?> = ORBITAL_PLANETS.mapIndexed { index, planet ->
        val slug = planet.streamedSlug
        if (slug == null) {
            null
        } else {
            produceState<File?>(initialValue = null, key1 = slug.uid, key2 = index) {
                value = runCatching {
                    SketchfabAssetResolver.getInstance(context).resolve(slug)
                }.getOrNull()
            }.value
        }
    }

    // The user's initial-pose anchor. Created lazily on the first tracked frame, since
    // ARCore world origin is undefined until tracking begins. After that, all 8 planets
    // ride this anchor — turning the phone shows them passing by in world space.
    var userAnchor by remember { mutableStateOf<Anchor?>(null) }
    var isTracking by remember { mutableStateOf(false) }

    // Elapsed seconds since the anchor was created, advanced by withFrameNanos. Drives
    // orbit + spin animation. Stored as nanos to avoid float-precision drift over long
    // sessions (a 10 min orbital run would lose ms-resolution stored as plain Float).
    var orbitNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(userAnchor) {
        if (userAnchor == null) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos != 0L) orbitNanos += (nanos - lastNanos)
                lastNanos = nanos
            }
        }
    }
    val orbitSeconds = orbitNanos / 1_000_000_000f

    DemoScaffold(
        title = stringResource(R.string.demo_ar_orbital_title),
        onBack = onBack,
        controls = {
            Text(
                text = "8 models orbit around you in a personal solar system. " +
                    "Stand still and turn the phone — each model passes by at " +
                    "a different speed and height.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ARSceneView(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                materialLoader = materialLoader,
                planeRenderer = false,
                sessionConfiguration = { _: Session, config: Config ->
                    // Plane detection off — the formation lives in world space around the
                    // user, not on a plane. Disabling planes is cheaper and gives a cleaner
                    // visual (no overlay polygons in front of the orbiting models).
                    config.planeFindingMode = Config.PlaneFindingMode.DISABLED
                    config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                },
                onSessionUpdated = { session: Session, frame: Frame ->
                    isTracking = frame.camera.trackingState == TrackingState.TRACKING
                    // Drop the world-origin anchor on the first tracked frame. ARCore's
                    // world origin = the camera's pose at session-start, which is exactly
                    // what we want: the formation sits where the user is standing.
                    if (isTracking && userAnchor == null) {
                        userAnchor = runCatching {
                            session.createAnchor(Pose.IDENTITY)
                        }.getOrNull()
                    }
                }
            ) {
                val anchor = userAnchor
                if (anchor != null) {
                    AnchorNode(anchor = anchor) {
                        ORBITAL_PLANETS.forEachIndexed { index, planet ->
                            // For bundled planets we pass the asset path directly;
                            // for streamed planets we pass the resolved File as a
                            // `file://` URI (rememberModelInstance dispatches via
                            // ModelLoader.loadModelInstance for non-asset schemes).
                            // While the streamed File is still null (download in
                            // flight) we render nothing for that slot — the orbit
                            // formation rebuilds the moment the resolver returns.
                            val fileLocation: String? = when {
                                planet.bundledAssetPath != null -> planet.bundledAssetPath
                                else -> streamedFiles.getOrNull(index)?.let { "file://${it.absolutePath}" }
                            }
                            val instance = if (fileLocation != null) {
                                rememberModelInstance(modelLoader, fileLocation)
                            } else null
                            if (instance != null) {
                                // Modulo before sin/cos so a long-running session
                                // (~290 h+) doesn't lose Float precision (#978).
                                val orbitAngle =
                                    (planet.initialAngleRad + planet.orbitSpeed * orbitSeconds) %
                                            (2f * PI.toFloat())
                                // Models with a baked animation (dragon, soldier) face the
                                // tangent of the orbit (= direction of motion) instead of
                                // spinning on Y — a flying dragon spinning on itself breaks
                                // the illusion. For position (R·cos θ, h, R·sin θ) on a CCW
                                // orbit, the tangent is (-sin θ, 0, cos θ); for a glTF model
                                // whose forward is -Z, that maps to a Y-rotation of θ + π.
                                val rotationY = if (planet.hasBakedAnimation) {
                                    Math.toDegrees(orbitAngle.toDouble()).toFloat() + 180f
                                } else {
                                    Math.toDegrees(
                                        (planet.spinSpeed * orbitSeconds).toDouble()
                                    ).toFloat() % 360f
                                }
                                ModelNode(
                                    modelInstance = instance,
                                    scaleToUnits = planet.scaleToUnits,
                                    centerOrigin = Position(0f, 0f, 0f),
                                    position = Position(
                                        x = cos(orbitAngle) * ORBIT_RADIUS,
                                        y = planet.height,
                                        z = sin(orbitAngle) * ORBIT_RADIUS,
                                    ),
                                    rotation = Rotation(y = rotationY),
                                    autoAnimate = true,
                                )
                            }
                        }
                    }
                }
            }

            // Status pill — top-center, mirrors the ARPlacement / ARInstantPlacement style.
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
                color = Color.Black.copy(alpha = 0.7f),
                contentColor = Color.White,
                tonalElevation = 4.dp,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = when {
                        !isTracking -> "Initializing AR — look around to start tracking"
                        userAnchor == null -> "Locking world anchor…"
                        else -> "Turn around — ${ORBITAL_PLANETS.size} models orbiting"
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
