package io.github.sceneview.node

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.WindowManager.LayoutParams
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.setViewTreeFullyDrawnReporterOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.annotation.LayoutRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.Stream
import com.google.android.filament.Texture
import io.github.sceneview.collision.HitResult
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Size
import io.github.sceneview.node.ViewNode.WindowManager
import io.github.sceneview.safeDestroyStream
import io.github.sceneview.safeDestroyTexture

/**
 * A Node that can display an Android [View]
 *
 * This node contains a View for the rendering engine to render.
 *
 * Manages a [FrameLayout] that is attached directly to a [WindowManager] that other views can be
 * added and removed from.
 *
 * To render a [View], the [View] must be attached to a [WindowManager] so that it can be properly
 * drawn. This class encapsulates a [FrameLayout] that is attached to a [WindowManager] that other
 * views can be added to as children. This allows us to safely and correctly draw the [View]
 * associated with a [RenderableManager] [Entity] and a [MaterialInstance] while keeping them
 * isolated from the rest of the activities View hierarchy.
 *
 * Additionally, this manages the lifecycle of the window to help ensure that the window is
 * added/removed from the WindowManager at the appropriate times.
 *
 * @param view The 2D Android [View] that is rendered by this [ViewNode]
 * @param unlit True to disable all lights influences on the rendered view
 * @param invertFrontFaceWinding Inverts the winding order of front faces.
 * Inverting the winding order of front faces is useful when rendering mirrored reflections
 * (water, mirror surfaces, front camera in AR, etc.).
 * True to invert front faces, false otherwise
 */
class ViewNode(
    engine: Engine,
    val windowManager: WindowManager,
    private val materialLoader: MaterialLoader,
    view: View,
    unlit: Boolean = false,
    invertFrontFaceWinding: Boolean = false,
) : PlaneNode(engine = engine) {

    // Updated when the view is added to the view manager
    var pxPerUnits = 250.0f
        set(value) {
            field = value
            updateGeometrySize()
        }

    var viewSize = Size(0.0f)
        set(value) {
            field = value
            updateGeometrySize()
        }

    val layout: Layout = Layout(view.context).apply {
        addView(view)
    }

    private val surfaceTexture = SurfaceTexture(0).also { it.detachFromGLContext() }
    private val surface = Surface(surfaceTexture)

    val stream: Stream = Stream.Builder()
        .stream(surfaceTexture)
        .build(engine)

    val texture: Texture = Texture.Builder()
        .sampler(Texture.Sampler.SAMPLER_EXTERNAL)
        .format(Texture.InternalFormat.RGB8)
        .build(engine)
        .apply {
            setExternalStream(engine, stream)
        }

    override var materialInstance: MaterialInstance = materialLoader.createViewInstance(viewTexture = texture,
        unlit = unlit,
        invertFrontFaceWinding = invertFrontFaceWinding
    ).also {
        setMaterialInstanceAt(0, it)
    }
        set(value) {
            val old = field
            materialLoader.destroyMaterialInstance(old)
            field = value
            setMaterialInstanceAt(0, value)
        }

    constructor(
        engine: Engine,
        windowManager: WindowManager,
        materialLoader: MaterialLoader,
        @LayoutRes viewLayoutRes: Int,
        unlit: Boolean = false,
        invertFrontFaceWinding: Boolean = false
    ) : this(
        engine = engine,
        windowManager = windowManager,
        materialLoader = materialLoader,
        view = LayoutInflater.from(materialLoader.context).inflate(viewLayoutRes, null, false),
        unlit = unlit,
        invertFrontFaceWinding = invertFrontFaceWinding
    )

    /**
     * Set the Jetpack Compose UI content for this view.
     * Initial composition will occur when the view becomes attached to a window or when
     * createComposition is called, whichever comes first.
     *
     * @param content the themed composable.
     * E.g.
     * ```
     * MaterialTheme {
     *     // In Compose world
     *     Text("Hello Compose!")
     * }
     */
    constructor(
        engine: Engine,
        windowManager: WindowManager,
        materialLoader: MaterialLoader,
        unlit: Boolean = false,
        invertFrontFaceWinding: Boolean = false,
        content: @Composable () -> Unit
    ) : this(
        engine = engine,
        windowManager = windowManager,
        materialLoader = materialLoader,
        view = ComposeView(materialLoader.context).apply {
            // Dispose of the Composition when the view's LifecycleOwner
            // is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent(content)
        },
        unlit = unlit,
        invertFrontFaceWinding = invertFrontFaceWinding
    )

    fun updateGeometrySize() {
        updateGeometry(size = viewSize / pxPerUnits)
    }

    override fun onAddedToScene(scene: Scene) {
        super.onAddedToScene(scene)

        windowManager.addView(layout)
    }

    override fun onRemovedFromScene(scene: Scene) {
        super.onRemovedFromScene(scene)

        windowManager.removeView(layout)
    }

    override fun onTouchEvent(e: MotionEvent, hitResult: HitResult): Boolean {
        return super.onTouchEvent(e, hitResult)
    }

    override fun destroy() {
        windowManager.removeView(layout)

        super.destroy()

        materialLoader.destroyMaterialInstance(materialInstance)
        engine.safeDestroyTexture(texture)
        engine.safeDestroyStream(stream)

    }

    /**
     * Used to render an Android view to a native open GL texture that can then be rendered by
     * Filament.
     *
     * To correctly draw a hardware accelerated animated view to a surface texture, the view MUST be
     * attached to a window and drawn to a real DisplayListCanvas, which is a hidden class.
     * To achieve this, the following is done:
     *
     *  - Attach [Layout] to the [WindowManager].
     *  - Override dispatchDraw.
     *  - Call super.dispatchDraw with the real DisplayListCanvas
     *  - Draw the clear color the DisplayListCanvas so that it isn't visible on screen.
     *  - Draw the view to the SurfaceTexture every frame. This must be done every frame, because
     *  the view will not be marked as dirty when child views are animating when hardware
     *  accelerated.
     */
    inner class Layout @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
        defStyleRes: Int = 0
    ) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)

            // Only called when we first get View size
            surfaceTexture.setDefaultBufferSize(width, height)

        }

        override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
            super.onSizeChanged(width, height, oldWidth, oldHeight)

            viewSize = Size(width.toFloat(), height.toFloat())
        }

        override fun dispatchDraw(canvas: Canvas) {
            if (!isAttachedToWindow) return

            // Sanity that the surface is valid.
            val viewSurface = surface.takeIf { it.isValid } ?: return
            val surfaceCanvas = viewSurface.lockCanvas(null)
            surfaceCanvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            super.dispatchDraw(surfaceCanvas)
            viewSurface.unlockCanvasAndPost(surfaceCanvas)
        }
    }

    class WindowManager(context: Context) {

        private val windowManager =
            context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager

        val layout by lazy {
            FrameLayout(context).also {
                context.findActivity()?.let { activity ->
                    it.setViewTreeLifecycleOwner(activity)
                    it.setViewTreeSavedStateRegistryOwner(activity)
                    it.setViewTreeViewModelStoreOwner(activity)
                    it.setViewTreeFullyDrawnReporterOwner(activity)
                    it.setViewTreeOnBackPressedDispatcherOwner(activity)
                }
            }
        }

        fun addView(view: View) = layout.addView(view)
        fun addView(view: View, params: FrameLayout.LayoutParams) = layout.addView(view, params)

        fun removeView(view: View) = layout.removeView(view)

        /**
         * An owner View can only be added to the WindowManager after the activity has finished
         * resuming.
         * Therefore, we must use post to ensure that the window is only added after resume is finished.
         */
        fun resume(ownerView: View) {
            // A ownerView can only be added to the WindowManager after the activity has finished resuming.
            // Therefore, we must use post to ensure that the window is only added after resume is finished.
            ownerView.post {
                if (ownerView.isAttachedToWindow) {
                    tryAttachingView()
                }
            }
        }

        /**
         * The [layout] must be removed from the [windowManager] before the activity is destroyed, or
         * the window will be leaked. Therefore we add/remove the ownerView in resume/pause.
         */
        fun pause() {
            tryDetachingView()
        }

        fun destroy() {
            tryDetachingView()
        }

        private fun tryAttachingView() = runCatching {
            if (layout.parent == null) {
                windowManager.addView(layout, LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.TYPE_APPLICATION_PANEL,
                    LayoutParams.FLAG_NOT_FOCUSABLE
                            or LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            or LayoutParams.FLAG_NOT_TOUCHABLE
                            or LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    title = "ViewNodeWindowManager"
                })
            }
        }


        private fun tryDetachingView() = runCatching {
            if (layout.parent != null) {
                windowManager.removeView(layout)
            }
        }
    }
}


private fun Context.findActivity(): ComponentActivity? {
    return generateSequence(this) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<ComponentActivity>()
        .firstOrNull()
}
