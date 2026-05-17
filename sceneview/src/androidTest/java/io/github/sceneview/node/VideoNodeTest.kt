package io.github.sceneview.node

import android.media.MediaPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.filament.Filament
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.utils.Utils
import io.github.sceneview.createEglContext
import io.github.sceneview.createEngine
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.safeDestroy
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for #1497 and #1539.
 *
 * Before the #1497 fix, [VideoNode.destroy] destroyed the
 * [com.google.android.filament.MaterialInstance] and then immediately freed the external
 * [com.google.android.filament.Texture] / [Stream] — the exact ordering that triggers a native
 * SIGABRT (`Invalid texture still bound to MaterialInstance`), because MaterialInstance
 * reclamation is coupled to the render loop, not to the `destroy()` call site. The fix drains
 * the frame pipeline between destroying the MaterialInstance and freeing the external
 * texture/stream, so the MI is fully reclaimed first.
 *
 * #1539 extends the same guarantee to the [VideoNode.materialInstance] runtime setter, which
 * previously freed the superseded MaterialInstance with no drain in between.
 *
 * These tests pin that ordering by exercising the real teardown path against a live Filament
 * [com.google.android.filament.Engine].
 */
@RunWith(AndroidJUnit4::class)
class VideoNodeTest {

    private lateinit var engine: com.google.android.filament.Engine
    private lateinit var materialLoader: MaterialLoader

    @Before
    fun setup() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            Gltfio.init(); Filament.init(); Utils.init()
            val context = InstrumentationRegistry.getInstrumentation().context
            val eglContext = createEglContext()
            engine = createEngine(eglContext)
            materialLoader = MaterialLoader(engine, context)
        }
    }

    @After
    fun teardown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            materialLoader.destroy()
            engine.safeDestroy()
        }
    }

    /**
     * A freshly created [VideoNode] must tear down without a native SIGABRT — i.e. the external
     * texture must not be freed while its MaterialInstance is still GPU-bound.
     */
    @Test
    fun destroy_doesNotCrash() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val player = MediaPlayer()
            val node = VideoNode(materialLoader = materialLoader, player = player)
            node.destroy() // must not SIGABRT
            player.release()
        }
    }

    /**
     * Rapid add/destroy churn must stay crash-free and leave no MaterialInstance tracked by
     * [MaterialLoader] — otherwise the `materialLoader.destroy()` in teardown would double-free.
     */
    @Test
    fun destroy_rapidCycle_doesNotCrash() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val player = MediaPlayer()
            repeat(20) {
                val node = VideoNode(materialLoader = materialLoader, player = player)
                node.destroy()
            }
            player.release()
        }
    }

    /**
     * A node created with an explicit fixed [io.github.sceneview.math.Size] must also destroy
     * cleanly (the auto-size code path is skipped, but teardown ordering is identical).
     */
    @Test
    fun destroy_withExplicitSize_doesNotCrash() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val player = MediaPlayer()
            val node = VideoNode(
                materialLoader = materialLoader,
                player = player,
                size = io.github.sceneview.math.Size(0.5f, 0.5f)
            )
            node.destroy()
            player.release()
        }
    }

    /**
     * A node configured with a chroma-key colour (a distinct material variant) must destroy
     * cleanly — exercising the green-screen MaterialInstance path through the same teardown.
     */
    @Test
    fun destroy_withChromaKey_doesNotCrash() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val player = MediaPlayer()
            val node = VideoNode(
                materialLoader = materialLoader,
                player = player,
                chromaKeyColor = 0xFF00FF00.toInt()
            )
            node.destroy()
            player.release()
        }
    }

    /**
     * Reassigning [VideoNode.materialInstance] must drain the frame pipeline before freeing the
     * old MaterialInstance — the external [com.google.android.filament.Texture] stays bound to
     * the old MI until the next frame, so freeing it eagerly is the same
     * `Invalid texture still bound to MaterialInstance` SIGABRT #1497 fixed for `destroy()`
     * (#1539). The node must still tear down cleanly afterwards.
     */
    @Test
    fun materialInstanceReassign_doesNotCrash() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val player = MediaPlayer()
            val node = VideoNode(materialLoader = materialLoader, player = player)
            // Swap in a fresh MaterialInstance built against the same external texture — the old
            // MI must be drained-then-destroyed, not freed while the texture is still bound.
            node.materialInstance = materialLoader.createVideoInstance(node.texture)
            node.destroy() // must not SIGABRT and must not double-free the old MI
            player.release()
        }
    }

    /**
     * Rapid [VideoNode.materialInstance] churn must stay crash-free — each reassignment routes
     * the superseded MaterialInstance through drain-then-destroy exactly once (#1539).
     */
    @Test
    fun materialInstanceReassign_rapidCycle_doesNotCrash() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val player = MediaPlayer()
            val node = VideoNode(materialLoader = materialLoader, player = player)
            repeat(20) {
                node.materialInstance = materialLoader.createVideoInstance(node.texture)
            }
            node.destroy()
            player.release()
        }
    }
}
