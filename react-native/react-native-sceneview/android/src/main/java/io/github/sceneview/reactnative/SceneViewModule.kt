package io.github.sceneview.reactnative

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

/**
 * React Native package that registers SceneView and ARSceneView view managers.
 *
 * Add to your app's `getPackages()`:
 * ```kotlin
 * override fun getPackages() = listOf(
 *     MainReactPackage(),
 *     SceneViewPackage()
 * )
 * ```
 */
class SceneViewPackage : ReactPackage {

    override fun createNativeModules(
        reactContext: ReactApplicationContext
    ): List<NativeModule> = listOf(
        ARRecorderModule(reactContext),
    )

    override fun createViewManagers(
        reactContext: ReactApplicationContext
    ): List<ViewManager<*, *>> = listOf(
        SceneViewManager(),
        ARSceneViewManager()
    )
}
