package io.github.sceneview.node

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.filament.Filament
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.utils.Utils
import io.github.sceneview.createEglContext
import io.github.sceneview.createEngine
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Size
import io.github.sceneview.safeDestroy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [ViewNode] — construction, property setters, geometry updates, destroy safety,
 * scene lifecycle callbacks, and [ViewNode.WindowManager].
 */
@RunWith(AndroidJUnit4::class)
class ViewNodeTest {

    private lateinit var engine: com.google.android.filament.Engine
    private lateinit var materialLoader: MaterialLoader
    private lateinit var windowManager: ViewNode.WindowManager

    @Before
    fun setup() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            Gltfio.init(); Filament.init(); Utils.init()
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            engine = createEngine(createEglContext())
            materialLoader = MaterialLoader(engine, ctx)
            windowManager = ViewNode.WindowManager(ctx)
        }
    }

    @After
    fun teardown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            windowManager.destroy()
            materialLoader.destroy()
            engine.safeDestroy()
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun makeViewNode(): ViewNode = ViewNode(
        engine = engine,
        windowManager = windowManager,
        materialLoader = materialLoader,
        view = View(InstrumentationRegistry.getInstrumentation().targetContext)
    )

    // ── Construction & defaults ───────────────────────────────────────────────

    @Test
    fun viewNode_creation_withView_doesNotCrash() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val node = makeViewNode()
            node.destroy()
        }
    }

    @Test
    fun viewNode_creation_hasCorrectDefaults() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val node = makeViewNode()

            assertEquals("pxPerUnits default should be 250", 250.0f, node.pxPerUnits, 0.001f)
            assertEquals("viewSize.x default should be 0", 0.0f, node.viewSize.x, 0.001f)
            assertEquals("viewSize.y default should be 0", 0.0f, node.viewSize.y, 0.001f)
            assertNotNull("layout should not be null", node.layout)
            assertNotNull("stream should not be null", node.stream)
            assertNotNull("texture should not be null", node.texture)

            node.destroy()
        }
    }

    // ── Property setters ──────────────────────────────────────────────────────

    @Test
    fun viewNode_pxPerUnits_setter_storesValue() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val node = makeViewNode()

            node.pxPerUnits = 500.0f

            assertEquals("pxPerUnits should be stored", 500.0f, node.pxPerUnits, 0.001f)

            node.destroy()
        }
    }

    @Test
    fun viewNode_viewSize_setter_storesValue() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val node = makeViewNode()

            node.viewSize = Size(100.0f, 50.0f)

            assertEquals("viewSize.x should be stored", 100.0f, node.viewSize.x, 0.001f)
            assertEquals("viewSize.y should be stored", 50.0f, node.viewSize.y, 0.001f)

            node.destroy()
        }
    }

    // ── Geometry updates ──────────────────────────────────────────────────────

    @Test
    fun viewNode_pxPerUnits_updatesGeometrySize() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val node = makeViewNode()

            node.viewSize = Size(500.0f, 250.0f)
            node.pxPerUnits = 500.0f

            // geometry.size = viewSize / pxPerUnits = (500/500, 250/500) = (1.0, 0.5)
            assertEquals("geometry width should be 1.0", 1.0f, node.geometry.size.x, 0.01f)
            assertEquals("geometry height should be 0.5", 0.5f, node.geometry.size.y, 0.01f)

            node.destroy()
        }
    }

    @Test
    fun viewNode_viewSize_updatesGeometrySize() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val node = makeViewNode()

            node.pxPerUnits = 250.0f
            node.viewSize = Size(500.0f, 500.0f)

            // geometry.size = viewSize / pxPerUnits = (500/250, 500/250) = (2.0, 2.0)
            assertEquals("geometry width should be 2.0", 2.0f, node.geometry.size.x, 0.01f)
            assertEquals("geometry height should be 2.0", 2.0f, node.geometry.size.y, 0.01f)

            node.destroy()
        }
    }

    // ── Destroy safety ────────────────────────────────────────────────────────

    @Test
    fun viewNode_destroy_doesNotCrash() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val node = makeViewNode()
            node.destroy() // must not SIGABRT
        }
    }

    @Test
    fun viewNode_destroy_rapidCycle_doesNotCrash() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            repeat(20) {
                val node = makeViewNode()
                node.destroy()
            }
        }
    }

    @Test
    fun viewNode_destroy_unregistersFromMaterialLoader() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val node = makeViewNode()
            node.destroy()
            // Verified implicitly: teardown's materialLoader.destroy() must not crash,
            // which would happen if the MaterialInstance were still tracked but its
            // underlying texture had already been destroyed.
        }
    }

    @Test
    fun viewNode_destroy_withCustomPxPerUnits_doesNotCrash() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val node = makeViewNode()
            node.pxPerUnits = 100.0f
            node.destroy() // must not SIGABRT
        }
    }

    // ── Scene lifecycle ───────────────────────────────────────────────────────

    @Test
    fun viewNode_onAddedToScene_addsLayoutToWindowManager() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val filamentScene = engine.createScene()
            val node = makeViewNode()

            node.onAddedToScene(filamentScene)

            assertTrue(
                "windowManager.layout should have the node layout as a child",
                windowManager.layout.childCount > 0
            )

            node.destroy()
            engine.destroyScene(filamentScene)
        }
    }

    // ── WindowManager ─────────────────────────────────────────────────────────

    @Test
    fun windowManager_creation_doesNotCrash() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val wm = ViewNode.WindowManager(ctx)
            wm.destroy()
        }
    }

    @Test
    fun windowManager_addView_increasesChildCount() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val wm = ViewNode.WindowManager(ctx)
            val view = View(ctx)

            val before = wm.layout.childCount
            wm.addView(view)

            assertEquals("child count should increase by 1", before + 1, wm.layout.childCount)

            wm.destroy()
        }
    }

    @Test
    fun windowManager_removeView_decreasesChildCount() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val wm = ViewNode.WindowManager(ctx)
            val view = View(ctx)

            wm.addView(view)
            val countAfterAdd = wm.layout.childCount
            wm.removeView(view)

            assertEquals("child count should decrease by 1", countAfterAdd - 1, wm.layout.childCount)

            wm.destroy()
        }
    }

    @Test
    fun windowManager_pause_withoutAttach_doesNotCrash() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val wm = ViewNode.WindowManager(ctx)
            wm.pause() // must not throw
            wm.destroy()
        }
    }

    @Test
    fun windowManager_destroy_withoutAttach_doesNotCrash() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val wm = ViewNode.WindowManager(ctx)
            wm.destroy() // must not throw
        }
    }

    @Test
    fun windowManager_multipleDestroy_isIdempotent() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val wm = ViewNode.WindowManager(ctx)
            wm.destroy()
            wm.destroy() // calling twice must not throw
        }
    }
}
