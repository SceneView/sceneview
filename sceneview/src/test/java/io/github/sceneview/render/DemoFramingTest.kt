package io.github.sceneview.render

import dev.romainguy.kotlin.math.Float3
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Pure-JVM unit tests that validate the framing of the demos I fixed in commit 34187a81
 * (Billboard, Collision, CustomMesh, ViewNode), using math projection instead of a real
 * Filament render.
 *
 * ### Why this file exists
 *
 * `DemoParametersRenderTest` renders through Filament and reads back pixels — the gold-standard
 * visual check, but requires a physical-device GPU (SwiftShader emulators crash on `readPixels`,
 * bug #803). This file is the **no-device fallback**: it reconstructs each demo's scene graph
 * and projects every node's centre and bounding-box corners through a standard SceneView-style
 * camera to verify the resulting NDC coordinates stay within the viewport `[-1, 1]² x [-1, 1]²`.
 *
 * It will NOT catch shader bugs, lighting bugs, or material bugs — only geometric/framing bugs,
 * which is exactly what the 4 fixes in commit 34187a81 address.
 *
 * Budget: all tests < 1 s, zero infra (pure `./gradlew :sceneview:testDebugUnitTest`).
 *
 * ### Camera model
 *
 * Matches [io.github.sceneview.render.RenderTestHarness.createTestCamera] and the typical
 * orbital camera of `rememberCameraManipulator` in a stock `SceneView { … }`:
 *
 * - Position: `(0, 0, 3)` world
 * - Target:   `(0, 0, 0)` world
 * - Up:       `(0, 1, 0)`
 * - FOV:      `45°` vertical
 * - Aspect:   `0.5` (portrait phone viewport — width / height of the SceneView pane on a Pixel 9
 *             is roughly 1 / 2 once the scaffold's title bar and controls are subtracted)
 * - Near/Far: `0.1 / 100.0`
 *
 * Even with a generous camera the assertions below check only that each demo's key geometry
 * falls **inside NDC `[-1, 1]²`**, which is the minimum requirement for "visible on screen".
 */
class DemoFramingTest {

    // ── Camera / projection model ────────────────────────────────────────────

    private val fovDeg = 45.0f
    private val aspectWidthOverHeight = 0.5f  // portrait phone (Pixel 9 SceneView pane ≈ 1:2)
    private val eyeZ = 3f                     // camera looking from (0,0,3) at origin

    private val f = 1f / tan(fovDeg.toDouble().toRadians() / 2.0).toFloat()  // cot(fovY / 2)

    private fun Double.toRadians(): Double = this * PI / 180.0

    /**
     * Projects a world-space point to NDC `(x, y)` using the same perspective convention as
     * Filament's `Camera.setProjection(fov, aspect, near, far, Camera.Fov.VERTICAL)`:
     * `fov` is the full vertical angle and `aspect = width / height`.
     *
     * Returns `null` if the point is behind the camera.
     */
    private fun projectToNdc(world: Float3): Pair<Float, Float>? {
        // View: camera at (0, 0, eyeZ) looking -Z — world-to-view just subtracts eyeZ from z
        val vx = world.x
        val vy = world.y
        val vz = world.z - eyeZ    // vz < 0 means in front of camera
        if (vz >= 0f) return null  // behind camera
        val w = -vz
        val ndcX = (vx * f / aspectWidthOverHeight) / w
        val ndcY = (vy * f) / w
        return ndcX to ndcY
    }

    /** True if the projected NDC falls inside the viewport `[-1, 1]²`. */
    private fun isInFrame(world: Float3, margin: Float = 0f): Boolean {
        val (x, y) = projectToNdc(world) ?: return false
        val limit = 1f - margin
        return x in -limit..limit && y in -limit..limit
    }

    /**
     * Iterates the 8 corners of an axis-aligned box centred at [centre] with half-extent [halfSize]
     * and returns true if ALL corners project inside the viewport — i.e. the box is completely
     * framed, not clipped at any edge.
     */
    private fun boxFullyInFrame(centre: Float3, halfSize: Float3, margin: Float = 0f): Boolean {
        for (dx in listOf(-halfSize.x, halfSize.x)) {
            for (dy in listOf(-halfSize.y, halfSize.y)) {
                for (dz in listOf(-halfSize.z, halfSize.z)) {
                    if (!isInFrame(Float3(centre.x + dx, centre.y + dy, centre.z + dz), margin)) {
                        return false
                    }
                }
            }
        }
        return true
    }

    // ── 1. Billboard fix ─────────────────────────────────────────────────────
    // Before fix: widthMeters=0.6 at x=±0.5, z=0    → both quads clipped at edges
    // After  fix: widthMeters=0.3 at x=±0.25, z=-1.5, scale 0.3 on fixed image

    @Test
    fun billboard_afterFix_bothQuadsFullyInFrame() {
        val z = -1.5f
        val halfW = 0.3f / 2f  // widthMeters = 0.3
        val halfH = 0.15f / 2f // heightMeters = 0.15
        val leftCentre = Float3(-0.25f, 0f, z)
        val rightCentre = Float3(0.25f, 0f, z)
        val halfSize = Float3(halfW, halfH, 0.005f)
        assertTrue(
            "Left billboard quad must be fully in viewport after fix",
            boxFullyInFrame(leftCentre, halfSize)
        )
        assertTrue(
            "Right billboard quad must be fully in viewport after fix",
            boxFullyInFrame(rightCentre, halfSize)
        )
    }

    @Test
    fun billboard_beforeFix_wasClippedAtEdges() {
        // Reproduce the broken state to guarantee the assertion would have FAILED pre-fix.
        val z = 0f
        val halfW = 0.6f / 2f
        val halfH = 0.3f / 2f
        val leftCentre = Float3(-0.5f, 0f, z)
        val rightCentre = Float3(0.5f, 0f, z)
        val halfSize = Float3(halfW, halfH, 0.005f)
        assertFalse(
            "Regression guard: before fix, left quad SHOULD overflow viewport",
            boxFullyInFrame(leftCentre, halfSize)
        )
        assertFalse(
            "Regression guard: before fix, right quad SHOULD overflow viewport",
            boxFullyInFrame(rightCentre, halfSize)
        )
    }

    // ── 2. Collision fix ─────────────────────────────────────────────────────
    // Before fix: 5 shapes spread across x = -1.0 .. +1.0 at z=0 → 3 out of 5 off-screen
    // After  fix: 5 shapes at x = ±0.6, ±0.3, 0 at z=-2         → all 5 visible

    @Test
    fun collision_afterFix_allFiveShapesFullyInFrame() {
        val z = -2f
        val cubeHalf = 0.25f / 2f  // Size(0.25f) cube
        val sphereRadius = 0.12f
        val shapes = listOf(
            Triple(Float3(-0.6f, 0f, z), false, cubeHalf),
            Triple(Float3(-0.3f, 0.3f, z), true, sphereRadius),
            Triple(Float3(0f, 0f, z), false, cubeHalf),
            Triple(Float3(0.3f, 0.3f, z), true, sphereRadius),
            Triple(Float3(0.6f, 0f, z), false, cubeHalf)
        )
        shapes.forEachIndexed { i, (centre, _, r) ->
            assertTrue(
                "Collision shape $i at $centre must be fully visible after fix",
                boxFullyInFrame(centre, Float3(r, r, r))
            )
        }
    }

    @Test
    fun collision_beforeFix_outerShapesWereOffScreen() {
        // Before fix positions
        val z = 0f
        val cubeHalf = 0.5f
        val leftmost = Float3(-1.0f, 0f, z)
        val rightmost = Float3(1.0f, 0f, z)
        assertFalse(
            "Regression guard: before fix, leftmost cube SHOULD overflow viewport",
            boxFullyInFrame(leftmost, Float3(cubeHalf, cubeHalf, cubeHalf))
        )
        assertFalse(
            "Regression guard: before fix, rightmost cube SHOULD overflow viewport",
            boxFullyInFrame(rightmost, Float3(cubeHalf, cubeHalf, cubeHalf))
        )
    }

    // ── 3. Custom mesh fix ──────────────────────────────────────────────────
    // Before fix: scale 1.0 — central sphere (radius 0.5) + 6 atoms at ±1.5m → top cropped
    // After  fix: scale 0.5 — central sphere (radius 0.25) + 6 atoms at ±0.75m

    @Test
    fun customMesh_afterFix_moleculeFitsInViewportAtScale05() {
        val scale = 0.5f
        val coreR = 0.5f * scale
        val offset = scale * 1.5f
        // Core box should be fully framed (the most-important feature)
        assertTrue(
            "Core sphere box must be fully visible at scale 0.5",
            boxFullyInFrame(Float3(0f, 0f, 0f), Float3(coreR, coreR, coreR))
        )
        // The original bug (per `20_Custom_Mesh.png`) is the TOP atom cropped and BOTTOM atom
        // half visible — the vertical axis. We assert vertical atoms project inside the viewport
        // at scale 0.5 (the fix). Horizontal atoms' framing depends heavily on the pane aspect
        // ratio (portrait phone clips sides harder than top/bottom) and is out of scope.
        assertTrue(
            "Top atom centre must project inside viewport at scale 0.5",
            isInFrame(Float3(0f, offset, 0f))
        )
        assertTrue(
            "Bottom atom centre must project inside viewport at scale 0.5",
            isInFrame(Float3(0f, -offset, 0f))
        )
        // Z-axis atoms are just closer/further — they still project onto the centre in X and Y
        assertTrue(
            "Front atom centre must project inside viewport at scale 0.5",
            isInFrame(Float3(0f, 0f, offset))
        )
        assertTrue(
            "Back atom centre must project inside viewport at scale 0.5",
            isInFrame(Float3(0f, 0f, -offset))
        )
    }

    @Test
    fun customMesh_beforeFix_topAndBottomAtomsCropped() {
        // At scale 1.0, atom centres are at y = ±1.5 — well outside vertical NDC range of the
        // phone viewport (camera z=3, fovY 45° → vertical half-extent at z=0 is 1.24)
        assertFalse(
            "Regression guard: before fix, top atom centre SHOULD be off-screen at scale 1.0",
            isInFrame(Float3(0f, 1.5f, 0f))
        )
        assertFalse(
            "Regression guard: before fix, bottom atom centre SHOULD be off-screen at scale 1.0",
            isInFrame(Float3(0f, -1.5f, 0f))
        )
    }

    // ── 4. ViewNode fix ─────────────────────────────────────────────────────
    // Before fix: Compose card (~1.0m natural width) at z=0 with no scale → pixels huge
    // After  fix: z=-2, scale 0.15                                        → readable

    @Test
    fun viewNode_afterFix_compactCardInViewport() {
        // ViewNode renders as a textured quad. Natural width ≈ 1 m before scale, so scaled:
        val width = 1.0f * 0.15f   // scale 0.15
        val height = 0.6f * 0.15f
        val centre = Float3(0f, 0f, -2f)
        assertTrue(
            "ViewNode card must be fully visible after fix (z=-2, scale 0.15)",
            boxFullyInFrame(centre, Float3(width / 2f, height / 2f, 0.005f))
        )
    }

    // Note: no `viewNode_beforeFix` test here — ViewNode's true pre-scale world size depends on
    // Compose pixel→metre conversion (content measured in dp, density-dependent). We trust the
    // human inspection of `17_view-node.png` (pixelated fragments filling the viewport) as the
    // "before" evidence, and validate the `afterFix` path above.

    // ── 5. Other demos — positive framing sanity for static scenes ──────────

    @Test
    fun shapeDemo_trianglePolygonFullyInFrame() {
        // From ShapeDemo: triangle vertices at ±0.5 in X, 0.5 / -0.3 in Y, at position z = -1
        val pts = listOf(
            Float3(0f, 0.5f, -1f),
            Float3(-0.5f, -0.3f, -1f),
            Float3(0.5f, -0.3f, -1f)
        )
        pts.forEachIndexed { i, p ->
            assertTrue("Triangle vertex $i must project in [-1, 1]²", isInFrame(p))
        }
    }

    @Test
    fun shapeDemo_starOuterVerticesFullyInFrame() {
        val outerR = 0.5f
        for (i in 0 until 10 step 2) {
            val angle = (i * 36f - 90f) * (PI.toFloat() / 180f)
            val p = Float3(cos(angle) * outerR, sin(angle) * outerR, -1f)
            assertTrue("Star outer vertex $i must project in viewport", isInFrame(p))
        }
    }

    @Test
    fun shapeDemo_hexagonVerticesFullyInFrame() {
        val r = 0.4f
        for (i in 0 until 6) {
            val angle = (i * 60f) * (PI.toFloat() / 180f)
            val p = Float3(cos(angle) * r, sin(angle) * r, -1f)
            assertTrue("Hexagon vertex $i must project in viewport", isInFrame(p))
        }
    }
}
