package io.github.sceneview.demo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.github.sceneview.demo.demos.AnimationDemo
import io.github.sceneview.demo.demos.ARPlacementDemo
import io.github.sceneview.demo.demos.CameraControlsDemo
import io.github.sceneview.demo.demos.EnvironmentDemo
import io.github.sceneview.demo.demos.FogDemo
import io.github.sceneview.demo.demos.GeometryDemo
import io.github.sceneview.demo.demos.LightingDemo
import io.github.sceneview.demo.demos.MovableLightDemo
import io.github.sceneview.demo.demos.ModelViewerDemo
import io.github.sceneview.demo.demos.TextDemo
import io.github.sceneview.demo.demos.LinesPathsDemo
import io.github.sceneview.demo.demos.ImageDemo
import io.github.sceneview.demo.demos.BillboardDemo
import io.github.sceneview.demo.demos.VideoDemo
import io.github.sceneview.demo.demos.ViewNodeDemo
import io.github.sceneview.demo.demos.GestureEditingDemo
import io.github.sceneview.demo.demos.CollisionDemo
import io.github.sceneview.demo.demos.DynamicSkyDemo
import io.github.sceneview.demo.demos.MultiModelDemo
import io.github.sceneview.demo.demos.PhysicsDemo
import io.github.sceneview.demo.demos.PostProcessingDemo
import io.github.sceneview.demo.demos.CustomMeshDemo
import io.github.sceneview.demo.demos.ShapeDemo
import io.github.sceneview.demo.demos.ReflectionProbesDemo
import io.github.sceneview.demo.demos.SecondaryCameraDemo
import io.github.sceneview.demo.demos.DebugOverlayDemo
import io.github.sceneview.demo.demos.ARImageDemo
import io.github.sceneview.demo.demos.ARFaceDemo
import io.github.sceneview.demo.demos.ARCloudAnchorDemo
import io.github.sceneview.demo.demos.ARStreetscapeDemo
import io.github.sceneview.demo.demos.ARPoseDemo
import io.github.sceneview.demo.demos.ARRerunDemo
import io.github.sceneview.demo.demos.ARRecordPlaybackDemo
import io.github.sceneview.demo.demos.ARDepthOcclusionDemo
import io.github.sceneview.demo.demos.ARInstantPlacementDemo
import io.github.sceneview.demo.demos.ARTerrainAnchorDemo
import io.github.sceneview.demo.demos.ARRooftopAnchorDemo
import io.github.sceneview.demo.demos.ARImageStabilizationDemo
import io.github.sceneview.demo.demos.OrbitalARDemo
import io.github.sceneview.demo.theme.SceneViewDemoTheme
import io.github.sceneview.demo.ui.RootScreen
import io.github.sceneview.demo.update.InAppUpdateManager
import io.github.sceneview.demo.update.UpdateBanner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {

    // Exposed (internal) so SceneViewDemoApp can hand the manager to UpdateBanner —
    // the composable only renders the downloaded-and-ready chrome.
    internal lateinit var updateManager: InAppUpdateManager

    /**
     * Latest demo id parsed from a deep-link intent (`sceneview://demo/<id>`
     * today, `https://sceneview.github.io/open?demo=<id>` once App-Links
     * verification ships). Updated from both `onCreate` (cold start) and
     * [onNewIntent] (warm start with the activity already in the
     * background). The Compose UI observes this and navigates when it
     * sees a non-null value, then resets it via [consumePendingDemo] so
     * a configuration change doesn't replay the same navigation.
     */
    private val pendingDemoId = MutableStateFlow<String?>(null)
    val pendingDemoIdFlow: StateFlow<String?> get() = pendingDemoId.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updateManager = InAppUpdateManager(this)
        // Two ingress channels: (1) `--es demo <id>` from `adb shell am` for QA / instrumented
        // tests, (2) URL deep-links via the public sceneview://demo/<id> scheme parsed by
        // DeepLinkRouter. The QA channel takes precedence so a tester running adb against a
        // running app can deterministically navigate without competing with a stale URL intent.
        // Same allow-list (ALL_DEMOS) gates both ingress channels — without it
        // any app could call `am start ... --es demo whatever` and steer
        // navigation through PlaceholderDemo. See #958.
        pendingDemoId.value = DeepLinkRouter.validate(intent?.getStringExtra("demo"))
            ?: DeepLinkRouter.parse(intent?.data)
        // QA mode ingress: `--ez qa_mode true` freezes auto-rotation / orbit / animations
        // so screenshot tests get a deterministic frame. Same setting reachable via the
        // long-press gesture on the demo title bar (see DemoScaffold). Off by default.
        // QA-mode tracks the latest intent, not "stickily true forever" — otherwise any
        // app on the device could flip it on once via `--ez qa_mode true` and leave the
        // showcase frozen until process death.
        DemoSettings.qaMode = intent?.getBooleanExtra("qa_mode", false) ?: false
        // Optional path to an ARCore playback fixture (.mp4). Confined to the app's own
        // external-files dir so a malicious deep link can't probe arbitrary device paths
        // (`/data/data/...`, photos, configs). The path is consumed once by
        // `ARRecordPlaybackDemo` then nulled.
        DemoSettings.arPendingPlaybackFile = intent?.getStringExtra("ar_playback_file")
            ?.takeIf { isWithinAppFilesDir(it) }
        setContent {
            SceneViewDemoTheme {
                SceneViewDemoApp(activity = this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Replace the activity intent so subsequent re-creations see the new
        // deep link, not the original launcher intent.
        setIntent(intent)
        // Same dual-ingress policy as onCreate — `--es demo` first, URL second.
        // Both go through DeepLinkRouter.validate / .parse so unknown ids are
        // dropped rather than routed to PlaceholderDemo. See #958.
        pendingDemoId.value = DeepLinkRouter.validate(intent.getStringExtra("demo"))
            ?: DeepLinkRouter.parse(intent.data)
        DemoSettings.qaMode = intent.getBooleanExtra("qa_mode", false)
        DemoSettings.arPendingPlaybackFile = intent.getStringExtra("ar_playback_file")
            ?.takeIf { isWithinAppFilesDir(it) }
    }

    fun consumePendingDemo() {
        pendingDemoId.value = null
    }

    /**
     * Returns `true` if the given path is inside this app's external-files directory
     * (the only location where AR fixtures legitimately live). Anything else — system
     * paths, other apps' data, photos, downloads — gets rejected. Without this guard,
     * any app on the device could craft a deep link with `--es ar_playback_file <path>`
     * and trick the demo into opening arbitrary files (Logcat would log the path,
     * leaking it). MP4 parsing itself is safe (ARCore rejects non-datasets), but
     * defence-in-depth.
     */
    private fun isWithinAppFilesDir(path: String): Boolean {
        val base = getExternalFilesDir(null)?.absolutePath ?: return false
        val canonical = runCatching { java.io.File(path).canonicalPath }.getOrNull() ?: return false
        return canonical.startsWith(base)
    }

    override fun onResume() {
        super.onResume()
        // Two phases (#890): handle a partially-downloaded update from a prior session,
        // then proactively check the Play Console for a newer release. Without
        // checkForUpdate() the flexible-update flow never starts, so the UpdateBanner
        // composable below also never lights up — making the entire in-app-update
        // pipeline a phantom on production builds.
        updateManager.checkForStalledUpdate()
        updateManager.checkForUpdate()
    }

    override fun onDestroy() {
        super.onDestroy()
        updateManager.destroy()
    }
}

@Composable
fun SceneViewDemoApp(activity: MainActivity? = null) {
    val navController = rememberNavController()
    // Compose the update banner above the NavHost so a downloaded-and-ready Play
    // update is visible from any screen (#890). The banner is a no-op when state
    // is IDLE / CHECKING / UP_TO_DATE — it only renders during DOWNLOADING /
    // READY_TO_INSTALL so it doesn't take screen real estate from demos.
    activity?.updateManager?.let { mgr ->
        UpdateBanner(updateManager = mgr)
    }

    // Watch for deep-link intents. On a non-null id we either navigate
    // directly (the demo list is the start destination, so navigate adds
    // the demo screen on top — back returns to the list) and immediately
    // consume the pending id so a config change doesn't replay it.
    val pendingId by (activity?.pendingDemoIdFlow?.collectAsState()
        ?: remember { MutableStateFlow<String?>(null) }.collectAsState())
    // Capture the launch-time deep-link id ONCE, so NavHost picks the right
    // start destination on first composition. The LaunchedEffect below still
    // handles subsequent intents (onNewIntent → pendingDemoIdFlow updates).
    val initialDemo = remember { activity?.pendingDemoIdFlow?.value }
    LaunchedEffect(pendingId) {
        val id = pendingId ?: return@LaunchedEffect
        // If the cold-start `initialDemo` already matches `pendingId`, NavHost picked the
        // demo as its start destination — navigating here would push a SECOND instance,
        // destroying the first one's remember{} state (and any one-shot flags like
        // `DemoSettings.arPendingPlaybackFile` that were already consumed). Just clear
        // the pending id so config changes don't replay it.
        if (id != initialDemo) {
            navController.navigate("demo/$id")
        }
        activity?.consumePendingDemo()
    }

    NavHost(
        navController = navController,
        startDestination = if (initialDemo != null) "demo/$initialDemo" else "list",
        enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut() },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn() },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
    ) {
        composable("list") {
            // New 4-tab root (Explore / AR View / Samples / About). The legacy
            // category-grouped DemoListScreen lives untouched inside the
            // "Samples" tab so existing deep-link flows (`adb am start ... --es
            // demo <id>`) and the in-app update banner remain wired up.
            RootScreen(onDemoClick = { id -> navController.navigate("demo/$id") })
        }
        composable("demo/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: return@composable
            val onBack: () -> Unit = { navController.popBackStack() }
            DemoRouter(id = id, onBack = onBack)
        }
        // Note: the legacy `composable("about") { AboutScreen(...) }` route was
        // removed (alongside `AboutScreen.kt`) because RootScreen's bottom-tab
        // `RootTab.About → AboutTabContent()` is the only About surface — no
        // caller ever navigated to "about". Deletion verified via grep:
        // 0 `navigate("about")` calls anywhere. The @Ignore'd ScreenshotTest
        // for AboutScreen stays disabled per its own comment.
    }
}

/**
 * Routes a demo [id] to the corresponding composable.
 * Demos not yet implemented show a placeholder.
 */
@Composable
fun DemoRouter(id: String, onBack: () -> Unit) {
    when (id) {
        // 3D Basics
        "model-viewer" -> ModelViewerDemo(onBack)
        "geometry" -> GeometryDemo(onBack)
        "animation" -> AnimationDemo(onBack)
        // Lighting & Environment
        "lighting" -> LightingDemo(onBack)
        "movable-light" -> MovableLightDemo(onBack)
        "fog" -> FogDemo(onBack)
        "environment" -> EnvironmentDemo(onBack)
        // Interaction
        "camera-controls" -> CameraControlsDemo(onBack)
        // Content
        "text" -> TextDemo(onBack)
        "lines-paths" -> LinesPathsDemo(onBack)
        "image" -> ImageDemo(onBack)
        "billboard" -> BillboardDemo(onBack)
        "video" -> VideoDemo(onBack)
        // Interaction
        "gesture-editing" -> GestureEditingDemo(onBack)
        "collision" -> CollisionDemo(onBack)
        "view-node" -> ViewNodeDemo(onBack)
        // Advanced
        "dynamic-sky" -> DynamicSkyDemo(onBack)
        "multi-model" -> MultiModelDemo(onBack)
        "physics" -> PhysicsDemo(onBack)
        "post-processing" -> PostProcessingDemo(onBack)
        "custom-mesh" -> CustomMeshDemo(onBack)
        "shape" -> ShapeDemo(onBack)
        "reflection-probes" -> ReflectionProbesDemo(onBack)
        "secondary-camera" -> SecondaryCameraDemo(onBack)
        "debug-overlay" -> DebugOverlayDemo(onBack)
        // Augmented Reality
        "ar-placement" -> ARPlacementDemo(onBack)
        "ar-image" -> ARImageDemo(onBack)
        "ar-face" -> ARFaceDemo(onBack)
        "ar-cloud-anchor" -> ARCloudAnchorDemo(onBack)
        "ar-streetscape" -> ARStreetscapeDemo(onBack)
        "ar-pose" -> ARPoseDemo(onBack)
        "ar-rerun" -> ARRerunDemo(onBack)
        "ar-record-playback" -> ARRecordPlaybackDemo(onBack)
        "ar-depth-occlusion" -> ARDepthOcclusionDemo(onBack)
        "ar-instant-placement" -> ARInstantPlacementDemo(onBack)
        "ar-terrain" -> ARTerrainAnchorDemo(onBack)
        "ar-rooftop" -> ARRooftopAnchorDemo(onBack)
        "ar-image-stabilization" -> ARImageStabilizationDemo(onBack)
        "ar-orbital" -> OrbitalARDemo(onBack)
        // Fallback
        else -> PlaceholderDemo(id = id, onBack = onBack)
    }
}

@Composable
fun PlaceholderDemo(id: String, onBack: () -> Unit) {
    val entry = ALL_DEMOS.find { it.id == id }
    DemoScaffold(
        title = entry?.title ?: id,
        onBack = onBack
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Coming soon",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
