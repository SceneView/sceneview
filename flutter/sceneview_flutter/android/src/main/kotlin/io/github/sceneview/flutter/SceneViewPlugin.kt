package io.github.sceneview.flutter

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.ar.arcore.getUpdatedPlanes
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.collision.HitResult
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

/**
 * Flutter plugin entry point for SceneView on Android.
 *
 * Registers two platform view types:
 * - `io.github.sceneview.flutter/sceneview`   -- 3D scene (wraps SceneView Compose)
 * - `io.github.sceneview.flutter/arsceneview` -- AR scene (wraps ARSceneView Compose)
 */
class SceneViewPlugin : FlutterPlugin, ActivityAware {

    private var activity: Activity? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        binding.platformViewRegistry.registerViewFactory(
            "io.github.sceneview.flutter/sceneview",
            SceneViewFactory(binding)
        )
        binding.platformViewRegistry.registerViewFactory(
            "io.github.sceneview.flutter/arsceneview",
            ARSceneViewFactory(binding)
        )
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {}

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }
    override fun onDetachedFromActivityForConfigChanges() { activity = null }
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }
    override fun onDetachedFromActivity() { activity = null }
}

// ---------------------------------------------------------------------------
// Model descriptor passed from Dart via method channel
// ---------------------------------------------------------------------------

private data class FlutterModelNode(
    val path: String,
    val position: Position = Position(0f, 0f, 0f),
    val rotation: Rotation = Rotation(0f, 0f, 0f),
    val scale: Float = 1.0f,
    val autoAnimate: Boolean = true
)

// ---------------------------------------------------------------------------
// 3D SceneView
// ---------------------------------------------------------------------------

class SceneViewFactory(
    private val binding: FlutterPlugin.FlutterPluginBinding
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val params = args as? Map<String, Any?> ?: emptyMap()
        return SceneViewPlatformView(context, viewId, params, binding)
    }
}

class SceneViewPlatformView(
    private val context: Context,
    private val viewId: Int,
    private val params: Map<String, Any?>,
    private val binding: FlutterPlugin.FlutterPluginBinding,
) : PlatformView, MethodChannel.MethodCallHandler {

    private val channel = MethodChannel(
        binding.binaryMessenger,
        "io.github.sceneview.flutter/scene_$viewId"
    )

    // Reactive state for Compose -- updated via method channel
    private val modelNodes = mutableStateListOf<FlutterModelNode>()
    private var environmentPath by mutableStateOf<String?>(null)

    private val composeView = ComposeView(context).apply {
        setContent {
            val engine = rememberEngine()
            val modelLoader = rememberModelLoader(engine)
            val environmentLoader = rememberEnvironmentLoader(engine)

            val cameraNode = rememberCameraNode(engine) {
                position = Position(y = 0f, z = 3.0f)
            }

            val environment = environmentPath?.let { path ->
                rememberEnvironment(environmentLoader) {
                    environmentLoader.createHDREnvironment(path)
                        ?: io.github.sceneview.createEnvironment(environmentLoader)
                }
            }

            SceneView(
                modifier = Modifier.fillMaxSize(),
                surfaceType = SurfaceType.TextureSurface,
                engine = engine,
                modelLoader = modelLoader,
                cameraNode = cameraNode,
                environment = environment ?: rememberEnvironment(environmentLoader),
            ) {
                modelNodes.forEachIndexed { index, model ->
                    val instance = rememberModelInstance(modelLoader, model.path)
                    instance?.let {
                        ModelNode(
                            modelInstance = it,
                            scaleToUnits = model.scale,
                            autoAnimate = model.autoAnimate,
                            position = model.position,
                            rotation = model.rotation,
                            apply = {
                                val nodeName = model.path.substringAfterLast('/').substringBeforeLast('.').ifEmpty { "node_$index" }
                                onTouch = { _: MotionEvent, _: HitResult ->
                                    Handler(Looper.getMainLooper()).post {
                                        channel.invokeMethod("onTap", nodeName)
                                    }
                                    false // don't consume — allow camera gestures
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    init {
        channel.setMethodCallHandler(this)
    }

    override fun getView(): View = composeView

    override fun dispose() {
        channel.setMethodCallHandler(null)
        modelNodes.clear()
        // Detach the ComposeView from any parent and dispose its composition
        // so that Filament resources (engine, loaders) are released.
        composeView.disposeComposition()
        (composeView.parent as? android.view.ViewGroup)?.removeView(composeView)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "loadModel" -> {
                val modelPath = call.argument<String>("modelPath") ?: run {
                    result.error("INVALID_ARG", "modelPath is required", null)
                    return
                }
                val scale = call.argument<Double>("scale")?.toFloat() ?: 1.0f
                val x = call.argument<Double>("x")?.toFloat() ?: 0f
                val y = call.argument<Double>("y")?.toFloat() ?: 0f
                val z = call.argument<Double>("z")?.toFloat() ?: 0f
                val rotationX = call.argument<Double>("rotationX")?.toFloat() ?: 0f
                val rotationY = call.argument<Double>("rotationY")?.toFloat() ?: 0f
                val rotationZ = call.argument<Double>("rotationZ")?.toFloat() ?: 0f

                modelNodes.add(FlutterModelNode(
                    path = modelPath,
                    position = Position(x, y, z),
                    rotation = Rotation(rotationX, rotationY, rotationZ),
                    scale = scale,
                ))
                result.success(null)
            }
            "addGeometry" -> {
                // Geometry nodes are not yet supported via the bridge.
                // SceneView's composable geometry API requires Compose DSL context
                // which cannot be driven easily from a method channel.
                result.success(null)
            }
            "addLight" -> {
                // Light configuration is handled by Scene's default mainLightNode.
                // Custom light manipulation requires Compose DSL context.
                result.success(null)
            }
            "clearScene" -> {
                modelNodes.clear()
                result.success(null)
            }
            "setEnvironment" -> {
                environmentPath = call.argument<String>("hdrPath")
                result.success(null)
            }
            "setCameraControlMode" -> {
                // v4.3.0 camera modes (#1053). Android's SceneView composable
                // already uses an orbit manipulator by default; `pan` and
                // `firstPerson` are iOS-first additions. Acknowledged here so
                // cross-platform Dart code does not throw — the per-mode
                // switch for Android is tracked in #1051.
                result.success(null)
            }
            "setAutoCenterContent" -> {
                // v4.3.0 content auto-centring (#1053) is iOS-first; the
                // Android library-level implementation is tracked in #1051.
                // Acknowledged so cross-platform Dart code does not throw.
                result.success(null)
            }
            "startRecording", "stopRecording", "saveRecordingToPhotoLibrary" -> {
                // ARRecorder is iOS-only via ReplayKit. The Dart `ARRecorder`
                // already guards against non-iOS platforms, so reaching here
                // would only happen on a direct channel call.
                result.error(
                    "UNSUPPORTED",
                    "ARRecorder is not supported on Android via the Flutter bridge (issue #1051).",
                    null,
                )
            }
            else -> result.notImplemented()
        }
    }
}

// ---------------------------------------------------------------------------
// AR SceneView
// ---------------------------------------------------------------------------

class ARSceneViewFactory(
    private val binding: FlutterPlugin.FlutterPluginBinding
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val params = args as? Map<String, Any?> ?: emptyMap()
        return ARSceneViewPlatformView(context, viewId, params, binding)
    }
}

class ARSceneViewPlatformView(
    private val context: Context,
    private val viewId: Int,
    private val params: Map<String, Any?>,
    private val binding: FlutterPlugin.FlutterPluginBinding,
) : PlatformView, MethodChannel.MethodCallHandler {

    private val channel = MethodChannel(
        binding.binaryMessenger,
        "io.github.sceneview.flutter/scene_$viewId"
    )

    private val modelNodes = mutableStateListOf<FlutterModelNode>()

    // Track which planes have already been reported to avoid duplicate callbacks.
    private val reportedPlaneIds = mutableSetOf<String>()

    private val composeView = ComposeView(context).apply {
        setContent {
            val engine = rememberEngine()
            val modelLoader = rememberModelLoader(engine)

            io.github.sceneview.ar.ARSceneView(
                modifier = Modifier.fillMaxSize(),
                surfaceType = SurfaceType.TextureSurface,
                engine = engine,
                modelLoader = modelLoader,
                planeRenderer = true,
                onSessionUpdated = { _, frame ->
                    val updatedPlanes = frame.getUpdatedPlanes()
                    for (plane in updatedPlanes) {
                        val planeId = System.identityHashCode(plane).toString()
                        if (reportedPlaneIds.add(planeId)) {
                            val planeType = when (plane.type) {
                                com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING -> "horizontal_upward"
                                com.google.ar.core.Plane.Type.HORIZONTAL_DOWNWARD_FACING -> "horizontal_downward"
                                com.google.ar.core.Plane.Type.VERTICAL -> "vertical"
                                else -> "unknown"
                            }
                            Handler(Looper.getMainLooper()).post {
                                channel.invokeMethod("onPlaneDetected", planeType)
                            }
                        }
                    }
                },
            ) {
                modelNodes.forEachIndexed { index, model ->
                    val instance = rememberModelInstance(modelLoader, model.path)
                    instance?.let {
                        ModelNode(
                            modelInstance = it,
                            scaleToUnits = model.scale,
                            autoAnimate = model.autoAnimate,
                            position = model.position,
                            rotation = model.rotation,
                            apply = {
                                val nodeName = model.path.substringAfterLast('/').substringBeforeLast('.').ifEmpty { "node_$index" }
                                onTouch = { _: MotionEvent, _: HitResult ->
                                    Handler(Looper.getMainLooper()).post {
                                        channel.invokeMethod("onTap", nodeName)
                                    }
                                    false // don't consume — allow camera gestures
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    init {
        channel.setMethodCallHandler(this)
    }

    override fun getView(): View = composeView

    override fun dispose() {
        channel.setMethodCallHandler(null)
        modelNodes.clear()
        reportedPlaneIds.clear()
        // Detach the ComposeView from any parent and dispose its composition
        // so that Filament/ARCore resources are released.
        composeView.disposeComposition()
        (composeView.parent as? android.view.ViewGroup)?.removeView(composeView)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "loadModel" -> {
                val modelPath = call.argument<String>("modelPath") ?: run {
                    result.error("INVALID_ARG", "modelPath is required", null)
                    return
                }
                val scale = call.argument<Double>("scale")?.toFloat() ?: 1.0f
                val x = call.argument<Double>("x")?.toFloat() ?: 0f
                val y = call.argument<Double>("y")?.toFloat() ?: 0f
                val z = call.argument<Double>("z")?.toFloat() ?: 0f
                val rotationX = call.argument<Double>("rotationX")?.toFloat() ?: 0f
                val rotationY = call.argument<Double>("rotationY")?.toFloat() ?: 0f
                val rotationZ = call.argument<Double>("rotationZ")?.toFloat() ?: 0f

                modelNodes.add(FlutterModelNode(
                    path = modelPath,
                    position = Position(x, y, z),
                    rotation = Rotation(rotationX, rotationY, rotationZ),
                    scale = scale,
                ))
                result.success(null)
            }
            "addGeometry" -> {
                result.success(null)
            }
            "addLight" -> {
                result.success(null)
            }
            "clearScene" -> {
                modelNodes.clear()
                reportedPlaneIds.clear()
                result.success(null)
            }
            "setEnvironment" -> {
                // AR scenes use camera feed as background; environment HDR
                // affects lighting but not the skybox.
                result.success(null)
            }
            "startRecording", "stopRecording", "saveRecordingToPhotoLibrary" -> {
                // ARRecorder is iOS-only via ReplayKit (#1053). Android's
                // `io.github.sceneview.ar.recording.ARRecorder` records an
                // ARCore dataset (a replayable session capture, not a video)
                // and needs Session/Frame access the platform-view bridge
                // does not expose. Tracked in #1051. The Dart `ARRecorder`
                // already guards non-iOS platforms.
                result.error(
                    "UNSUPPORTED",
                    "ARRecorder is not supported on Android via the Flutter bridge (issue #1051).",
                    null,
                )
            }
            else -> result.notImplemented()
        }
    }
}
