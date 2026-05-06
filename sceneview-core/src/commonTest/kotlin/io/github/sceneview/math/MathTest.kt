package io.github.sceneview.math

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Mat4
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MathTest {

    private fun assertClose(expected: Float, actual: Float, epsilon: Float = 0.001f) {
        assertTrue(abs(expected - actual) < epsilon, "Expected $expected but got $actual")
    }

    @Test
    fun typeAliasesAreCorrectTypes() {
        val pos: Position = Float3(1f, 2f, 3f)
        val rot: Rotation = Float3(0f, 90f, 0f)
        val scl: Scale = Float3(1f, 1f, 1f)
        assertEquals(1f, pos.x)
        assertEquals(90f, rot.y)
        assertEquals(1f, scl.z)
    }

    /**
     * Regression guard for the PhysicsDemo-invisible-spheres bug:
     *
     * `Scale(1f)` MUST produce the uniform scale `(1, 1, 1)`. The named-arg form
     * `Scale(x = 1f)` produces the singular `(1, 0, 0)` — that's kotlin-math's
     * three-arg primary constructor with default `y = 0f, z = 0f`.
     *
     * If a future refactor ever makes `Scale(1f)` resolve to the primary three-arg
     * constructor (e.g. by removing `Float3(v: Float)`), this test fails loudly
     * before the change reaches the node composables.
     */
    @Test
    fun scaleSingleArgIsUniform() {
        val uniform: Scale = Scale(1f)
        assertEquals(1f, uniform.x)
        assertEquals(1f, uniform.y)
        assertEquals(1f, uniform.z)

        // And the named-arg form stays (intentionally) non-uniform — callers
        // who want uniform scale must use the positional single-arg form. This
        // locks in the behaviour so a future kotlin-math upgrade that e.g.
        // changes the primary constructor to fill y/z from x will surface here.
        val partial: Scale = Scale(x = 1f)
        assertEquals(1f, partial.x)
        assertEquals(0f, partial.y)
        assertEquals(0f, partial.z)
    }

    @Test
    fun floatArrayToFloat3() {
        val arr = floatArrayOf(1f, 2f, 3f)
        val v = arr.toFloat3()
        assertEquals(1f, v.x)
        assertEquals(2f, v.y)
        assertEquals(3f, v.z)
    }

    @Test
    fun transformWithDefaultValues() {
        val t = Transform()
        // Identity transform
        assertClose(1f, t.x.x) // scale x
        assertClose(1f, t.y.y) // scale y
        assertClose(1f, t.z.z) // scale z
        assertClose(1f, t.w.w) // homogeneous
    }

    @Test
    fun mat4ToColumnsFloatArray() {
        val identity = Mat4()
        val arr = identity.toColumnsFloatArray()
        assertEquals(16, arr.size)
        assertClose(1f, arr[0])  // m[0][0]
        assertClose(0f, arr[1])  // m[1][0]
        assertClose(1f, arr[5])  // m[1][1]
        assertClose(1f, arr[15]) // m[3][3]
    }

    @Test
    fun lerpHalfway() {
        val start = Float3(0f, 0f, 0f)
        val end = Float3(10f, 20f, 30f)
        val result = lerp(start, end, 0.5f)
        assertClose(5f, result.x)
        assertClose(10f, result.y)
        assertClose(15f, result.z)
    }

    @Test
    fun colorOfBasic() {
        val c = colorOf(0.5f, 0.3f, 0.1f, 1.0f)
        assertClose(0.5f, c.x) // r
        assertClose(0.3f, c.y) // g
        assertClose(0.1f, c.z) // b
        assertClose(1.0f, c.w) // a
    }

    @Test
    fun colorOfGrayscale() {
        val c = colorOf(rgb = 0.5f)
        assertClose(0.5f, c.x)
        assertClose(0.5f, c.y)
        assertClose(0.5f, c.z)
        assertClose(1.0f, c.w)
    }

    @Test
    fun floatAlmostEquals() {
        assertTrue(1.0f almostEquals 1.0f)
        assertTrue(1.0f almostEquals (1.0f + Float.MIN_VALUE))
    }

    @Test
    fun floatEqualsWithDelta() {
        assertTrue(1.0f.equals(1.05f, 0.1f))
        assertTrue(!1.0f.equals(1.2f, 0.1f))
    }

    @Test
    fun toLinearSpace() {
        val linear = floatArrayOf(0.5f).toLinearSpace()
        // 0.5^2.2 ≈ 0.2176
        assertClose(0.2176f, linear[0], 0.01f)
    }
}
