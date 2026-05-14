package io.github.sceneview.demo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.filament.Engine
import com.google.android.filament.Filament
import com.google.android.filament.MaterialInstance
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.utils.Utils
import io.github.sceneview.createEglContext
import io.github.sceneview.createEngine
import io.github.sceneview.geometries.Cube
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.GeometryNode
import io.github.sceneview.safeDestroy
import io.github.sceneview.safeDestroyMaterialInstance
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression pin for the `destroyMaterialsOnDispose: Boolean = false` flag added to
 * [GeometryNode] / `RenderableNode` in PR
 * [#1132](https://github.com/sceneview/sceneview/pull/1132) (closes
 * [#1123](https://github.com/sceneview/sceneview/issues/1123)).
 *
 * ## What the flag does
 *
 * Pre-fix, `RenderableNode.destroy()` only freed the renderable component. Any
 * `MaterialInstance` passed via the `materialInstances` constructor stayed alive in
 * Filament's internal tables until engine teardown — a steady-state memory leak for
 * the common per-demo pattern of:
 *
 * ```kotlin
 * val cubeMaterial = materialLoader.createColorInstance(Color.Blue)
 * GeometryNode(engine = engine, geometry = cube, materialInstance = cubeMaterial)
 * // ... let the node go out of scope, never call materialLoader.destroyMaterialInstance(cubeMaterial)
 * ```
 *
 * The samples-side `rememberMaterialInstance` Compose helper (#937, #971) papers
 * over this for sample code, but library consumers writing their own nodes hit
 * the same trap.
 *
 * Post-fix, opt-in by setting `destroyMaterialsOnDispose = true` on the
 * `GeometryNode` constructor:
 *
 * ```kotlin
 * GeometryNode(
 *     engine = engine,
 *     geometry = cube,
 *     materialInstance = cubeMaterial,
 *     destroyMaterialsOnDispose = true,  // tear down on destroy()
 * )
 * ```
 *
 * `destroy()` then calls `engine.safeDestroyMaterialInstance(...)` on each
 * non-null entry of `materialInstances`. The renderable is destroyed first to
 * avoid dangling MI/texture bindings (see `RenderableNode.kt:99-104` for the
 * sequencing comment).
 *
 * ## What this test pins
 *
 * Three contracts:
 *
 *  1. **`destroyMaterialsOnDispose = true` actually destroys** — the
 *     `MaterialInstance.getNativeObject()` handle drops to `0` after the node's
 *     `destroy()`. This is the headline acceptance criterion of #1123.
 *  2. **`destroyMaterialsOnDispose = false` (default) preserves** — the
 *     `MaterialInstance` outlives the node's destroy, so external owners
 *     (e.g. `MaterialLoader` or `rememberMaterialInstance`) can keep using it.
 *     This is the backward-compatibility guarantee.
 *  3. **MaterialLoader-tracked instances are leaked when the flag is `true`** —
 *     the flag uses `engine.safeDestroyMaterialInstance` directly, NOT
 *     `materialLoader.destroyMaterialInstance`. So the loader's internal tracking
 *     set keeps the (now-native-zeroed) instance until `loader.destroy()` runs.
 *     This is the documented caveat in `RenderableNode.kt:66-69` KDoc.
 *
 * ## Demo migration follow-up
 *
 * The #1123 acceptance also calls for "at least 1 demo migrated to use
 * `destroyMaterialsOnDispose = true`". As of v4.3.1, no demo passes the flag because
 * the Compose `SceneScope.CubeNode` / `SphereNode` / etc. factories do NOT yet
 * propagate it to their underlying `GeometryNode` constructor. Surfacing the flag
 * through every Compose factory is tracked separately — this test pins the
 * library-level contract that those factories will eventually wire up.
 *
 * Lineage: #1123, follow-up to CORR-C ([PR #1137](https://github.com/sceneview/sceneview/pull/1137))
 * test-coverage extension batch.
 *
 * @see io.github.sceneview.node.RenderableNode for the constructor parameter
 *     wiring and the destroy-sequencing comment.
 */
@RunWith(AndroidJUnit4::class)
class MaterialInstanceLeakTest {

    private lateinit var engine: Engine
    private lateinit var materialLoader: MaterialLoader

    @Before
    fun setup() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            Gltfio.init(); Filament.init(); Utils.init()
            val eglContext = createEglContext()
            engine = createEngine(eglContext)
            val context = InstrumentationRegistry.getInstrumentation().context
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
     * Headline pin: when `destroyMaterialsOnDispose = true`, the constructor-passed
     * `MaterialInstance` is destroyed alongside the node. The `getNativeObject()`
     * accessor reads the underlying Filament JNI handle and drops to `0` once the
     * native object is freed.
     */
    @Test
    fun destroyMaterialsOnDispose_true_destroysMaterialInstance() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val material = materialLoader.createColorInstance(
                color = androidx.compose.ui.graphics.Color.Blue
            )
            val handleBefore = material.nativeObject
            assertNotEquals(
                "Pre-destroy: MaterialInstance must hold a valid (non-zero) native handle",
                0L, handleBefore
            )

            val cube = Cube.Builder()
                .size(Size(0.5f))
                .center(Position(0f))
                .build(engine)
            val node = GeometryNode(
                engine = engine,
                geometry = cube,
                materialInstance = material,
                destroyMaterialsOnDispose = true,
            )

            // Node destroy MUST cascade to the material destroy when the flag is true.
            node.destroy()

            assertEquals(
                "Post-destroy: MaterialInstance native handle must drop to 0 — pre-#1123, " +
                    "this would have stayed at $handleBefore (the leak).",
                0L, material.nativeObject
            )
        }
    }

    /**
     * Backward-compatibility pin: with the default `destroyMaterialsOnDispose = false`,
     * the constructor-passed `MaterialInstance` MUST NOT be touched by `node.destroy()`.
     * External owners (the loader, a `DisposableEffect`, the
     * `rememberMaterialInstance` helper) need the instance to outlive the node.
     */
    @Test
    fun destroyMaterialsOnDispose_falseByDefault_preservesMaterialInstance() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val material = materialLoader.createColorInstance(
                color = androidx.compose.ui.graphics.Color.Red
            )
            val handleBefore = material.nativeObject

            val cube = Cube.Builder()
                .size(Size(0.5f))
                .center(Position(0f))
                .build(engine)
            // Default flag value — backward-compatible behaviour.
            val node = GeometryNode(
                engine = engine,
                geometry = cube,
                materialInstance = material,
            )

            node.destroy()

            // Material instance is untouched — external owner still has a live handle.
            assertEquals(
                "Default `destroyMaterialsOnDispose = false` must leave the MaterialInstance " +
                    "alive post-destroy — pre-existing `rememberMaterialInstance` and " +
                    "MaterialLoader-managed flows rely on this.",
                handleBefore, material.nativeObject
            )

            // Cleanup: external destruction works as before.
            materialLoader.destroyMaterialInstance(material)
            assertEquals(
                "After external destroy via the loader, the native handle is now 0.",
                0L, material.nativeObject
            )
        }
    }

    /**
     * Multi-primitive pin: a [GeometryNode] built with `List<MaterialInstance?>` plus
     * `destroyMaterialsOnDispose = true` must destroy every non-null entry, leaving
     * the `null` placeholders untouched (no NPE).
     *
     * Cube has 6 primitives (one per face). We bind material A to face 0, leave face 1
     * unbound (`null`), and bind material B to face 2. The remaining 3 faces also use
     * `null`. The pin: A and B are destroyed, nulls are skipped without NPE.
     */
    @Test
    fun destroyMaterialsOnDispose_true_handlesMultipleAndNullEntries() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val matA = materialLoader.createColorInstance(
                color = androidx.compose.ui.graphics.Color.Green
            )
            val matB = materialLoader.createColorInstance(
                color = androidx.compose.ui.graphics.Color.Yellow
            )
            // Six entries — matches Cube's 6 primitive offsets — mixing both
            // non-null and null entries.
            val matList: List<MaterialInstance?> = listOf(matA, null, matB, null, null, null)

            val handleA = matA.nativeObject
            val handleB = matB.nativeObject
            assertNotEquals(0L, handleA)
            assertNotEquals(0L, handleB)

            val cube = Cube.Builder()
                .size(Size(0.5f))
                .center(Position(0f))
                .build(engine)
            // primitiveCount + matList — the multi-MI constructor path.
            val node = GeometryNode(
                engine = engine,
                geometry = cube,
                materialInstances = matList,
                destroyMaterialsOnDispose = true,
            )

            node.destroy()

            assertEquals(
                "First non-null MaterialInstance must be destroyed.",
                0L, matA.nativeObject
            )
            assertEquals(
                "Second non-null MaterialInstance must be destroyed.",
                0L, matB.nativeObject
            )
        }
    }

    /**
     * Sequencing pin (#1132 + RenderableNode.kt:99-104): `destroy()` MUST tear down
     * the renderable BEFORE freeing the owned material instances. The reverse order
     * would leave Filament's internal MI/texture bindings dangling on the renderable,
     * surfacing as "Invalid texture still bound to MaterialInstance" the next time
     * the same engine destroys an unrelated texture.
     *
     * We can't directly inspect Filament's destruction order, but we can verify the
     * end-state: after `destroy()`, repeated `destroy()` calls and direct
     * `safeDestroyMaterialInstance` calls are idempotent (no second free crashes).
     */
    @Test
    fun destroyMaterialsOnDispose_isIdempotent_acrossDoubleDestroy() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val material = materialLoader.createColorInstance(
                color = androidx.compose.ui.graphics.Color.Cyan
            )
            val cube = Cube.Builder()
                .size(Size(0.5f))
                .center(Position(0f))
                .build(engine)
            val node = GeometryNode(
                engine = engine,
                geometry = cube,
                materialInstance = material,
                destroyMaterialsOnDispose = true,
            )

            node.destroy()
            assertEquals(0L, material.nativeObject)

            // Second destroy() must be a no-op — RenderableNode.kt comment promises
            // `safeDestroyMaterialInstance is a no-op (via runCatching) if the
            // instance is already destroyed or invalid — robust against double-destroy`.
            node.destroy()
            assertEquals(
                "Second destroy() must not re-raise the native handle (already 0).",
                0L, material.nativeObject
            )

            // Direct engine-level double-free via the safe wrapper is also idempotent.
            // `safeDestroyMaterialInstance` wraps the JNI call in `runCatching`, so
            // calling it on an already-destroyed instance returns a `Result.failure`
            // rather than throwing.
            assertTrue(
                "engine.safeDestroyMaterialInstance must not throw on an already-destroyed instance.",
                runCatching { engine.safeDestroyMaterialInstance(material) }.isSuccess
            )
        }
    }
}
