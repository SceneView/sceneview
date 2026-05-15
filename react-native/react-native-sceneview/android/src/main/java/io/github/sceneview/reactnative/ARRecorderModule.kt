package io.github.sceneview.reactnative

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * React Native bridge for AR session recording (v4.3.0, issue #1053).
 *
 * Exposed to JS as `NativeModules.RNARRecorder`.
 *
 * On iOS this is backed by SceneViewSwift's `ARRecorder` (ReplayKit screen
 * capture). On Android, ARCore's `io.github.sceneview.ar.recording.ARRecorder`
 * records a *replayable session dataset* — a fundamentally different artifact
 * from a video, and one that needs `Session`/`Frame` access the Fabric
 * platform-view bridge does not currently expose.
 *
 * Rather than silently no-op, every method rejects with `UNSUPPORTED` so the
 * JS `ARRecorder` class (which already gates `Platform.OS !== 'ios'`) and any
 * direct caller get an honest, actionable error. The Android bridge is
 * tracked in issue #1051.
 */
class ARRecorderModule(
    reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "RNARRecorder"

    private fun rejectUnsupported(promise: Promise) {
        promise.reject(
            "UNSUPPORTED",
            "ARRecorder is not supported on Android via the React Native bridge. " +
                "ARCore session recording is tracked in issue #1051.",
        )
    }

    @ReactMethod
    fun start(promise: Promise) = rejectUnsupported(promise)

    @ReactMethod
    fun stop(outputPath: String?, promise: Promise) = rejectUnsupported(promise)

    @ReactMethod
    fun saveToPhotoLibrary(movPath: String, promise: Promise) = rejectUnsupported(promise)
}
