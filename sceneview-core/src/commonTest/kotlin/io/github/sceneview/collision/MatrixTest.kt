package io.github.sceneview.collision

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatrixTest {

    private fun assertClose(expected: Float, actual: Float, epsilon: Float = 0.001f) {
        assertTrue(abs(expected - actual) < epsilon, "Expected $expected but got $actual")
    }

    // --- Identity ---

    @Test
    fun defaultConstructorCreatesIdentity() {
        val m = Matrix()
        for (i in 0..15) {
            val expected = if (i % 5 == 0) 1f else 0f
            assertClose(expected, m.data[i])
        }
    }

    @Test
    fun identityTransformPointPreservesPoint() {
        val m = Matrix()
        val v = Vector3(3f, 7f, -2f)
        val result = m.transformPoint(v)
        assertClose(3f, result.x)
        assertClose(7f, result.y)
        assertClose(-2f, result.z)
    }

    @Test
    fun identityTransformDirectionPreservesDirection() {
        val m = Matrix()
        val v = Vector3(1f, 0f, 0f)
        val result = m.transformDirection(v)
        assertClose(1f, result.x)
        assertClose(0f, result.y)
        assertClose(0f, result.z)
    }

    // --- Translation ---

    @Test
    fun makeTranslationSetsCorrectValues() {
        val m = Matrix()
        m.makeTranslation(Vector3(5f, 10f, 15f))
        assertClose(5f, m.data[12])
        assertClose(10f, m.data[13])
        assertClose(15f, m.data[14])
    }

    @Test
    fun translationTransformsPoint() {
        val m = Matrix()
        m.makeTranslation(Vector3(1f, 2f, 3f))
        val result = m.transformPoint(Vector3(0f, 0f, 0f))
        assertClose(1f, result.x)
        assertClose(2f, result.y)
        assertClose(3f, result.z)
    }

    @Test
    fun translationDoesNotAffectDirection() {
        val m = Matrix()
        m.makeTranslation(Vector3(100f, 200f, 300f))
        val dir = m.transformDirection(Vector3(1f, 0f, 0f))
        assertClose(1f, dir.x)
        assertClose(0f, dir.y)
        assertClose(0f, dir.z)
    }

    // --- Scale ---

    @Test
    fun makeUniformScale() {
        val m = Matrix()
        m.makeScale(3f)
        assertClose(3f, m.data[0])
        assertClose(3f, m.data[5])
        assertClose(3f, m.data[10])
    }

    @Test
    fun makeNonUniformScale() {
        val m = Matrix()
        m.makeScale(Vector3(2f, 3f, 4f))
        assertClose(2f, m.data[0])
        assertClose(3f, m.data[5])
        assertClose(4f, m.data[10])
    }

    @Test
    fun scaleTransformsPoint() {
        val m = Matrix()
        m.makeScale(Vector3(2f, 3f, 4f))
        val result = m.transformPoint(Vector3(1f, 1f, 1f))
        assertClose(2f, result.x)
        assertClose(3f, result.y)
        assertClose(4f, result.z)
    }

    // --- Rotation ---

    @Test
    fun makeRotation90AroundY() {
        val m = Matrix()
        val q = Quaternion(Vector3.up(), 90f)
        m.makeRotation(q)
        // Rotating (1,0,0) by 90 degrees around Y should give approximately (0,0,-1)
        val result = m.transformPoint(Vector3(1f, 0f, 0f))
        assertClose(0f, result.x, 0.01f)
        assertClose(0f, result.y, 0.01f)
        assertClose(-1f, result.z, 0.01f)
    }

    // --- Decomposition ---

    @Test
    fun decomposeTranslation() {
        val m = Matrix()
        m.makeTranslation(Vector3(4f, 5f, 6f))
        val t = Vector3()
        m.decomposeTranslation(t)
        assertClose(4f, t.x)
        assertClose(5f, t.y)
        assertClose(6f, t.z)
    }

    @Test
    fun decomposeScale() {
        val m = Matrix()
        m.makeScale(Vector3(2f, 3f, 4f))
        val s = Vector3()
        m.decomposeScale(s)
        assertClose(2f, s.x)
        assertClose(3f, s.y)
        assertClose(4f, s.z)
    }

    // --- Multiply ---

    @Test
    fun multiplyIdentityByIdentity() {
        val a = Matrix()
        val b = Matrix()
        val result = Matrix()
        Matrix.multiply(a, b, result)
        assertTrue(Matrix.equals(result, Matrix()))
    }

    @Test
    fun multiplyTranslations() {
        val a = Matrix()
        a.makeTranslation(Vector3(1f, 0f, 0f))
        val b = Matrix()
        b.makeTranslation(Vector3(0f, 2f, 0f))
        val result = Matrix()
        Matrix.multiply(a, b, result)
        val point = result.transformPoint(Vector3(0f, 0f, 0f))
        assertClose(1f, point.x)
        assertClose(2f, point.y)
        assertClose(0f, point.z)
    }

    // --- Invert ---

    @Test
    fun invertIdentity() {
        val m = Matrix()
        val inv = Matrix()
        val success = Matrix.invert(m, inv)
        assertTrue(success)
        assertTrue(Matrix.equals(m, inv))
    }

    @Test
    fun invertTranslation() {
        val m = Matrix()
        m.makeTranslation(Vector3(3f, 4f, 5f))
        val inv = Matrix()
        val success = Matrix.invert(m, inv)
        assertTrue(success)
        // M * M^-1 should be identity
        val product = Matrix()
        Matrix.multiply(m, inv, product)
        assertTrue(Matrix.equals(product, Matrix()))
    }

    @Test
    fun invertScale() {
        val m = Matrix()
        m.makeScale(Vector3(2f, 4f, 8f))
        val inv = Matrix()
        val success = Matrix.invert(m, inv)
        assertTrue(success)
        val product = Matrix()
        Matrix.multiply(m, inv, product)
        assertTrue(Matrix.equals(product, Matrix()))
    }

    // --- TRS ---

    @Test
    fun makeTrsIdentity() {
        val m = Matrix()
        m.makeTrs(Vector3(0f, 0f, 0f), Quaternion.identity(), Vector3(1f, 1f, 1f))
        assertTrue(Matrix.equals(m, Matrix()))
    }

    @Test
    fun makeTrsDecomposesCorrectly() {
        val m = Matrix()
        val translation = Vector3(5f, 10f, 15f)
        m.makeTrs(translation, Quaternion.identity(), Vector3(1f, 1f, 1f))
        val t = Vector3()
        m.decomposeTranslation(t)
        assertClose(5f, t.x)
        assertClose(10f, t.y)
        assertClose(15f, t.z)
    }

    // --- Equals ---

    @Test
    fun equalMatricesAreEqual() {
        val a = Matrix()
        val b = Matrix()
        assertTrue(Matrix.equals(a, b))
    }

    @Test
    fun differentMatricesAreNotEqual() {
        val a = Matrix()
        val b = Matrix()
        b.data[0] = 99f
        assertFalse(Matrix.equals(a, b))
    }

    // --- Rotation round-trip through extractQuaternion ---

    @Test
    fun rotationMatrixToQuaternionAndBack() {
        val q = Quaternion.axisAngle(Vector3(1f, 1f, 1f).normalized(), 60f)
        val m = Matrix()
        m.makeRotation(q)
        val extracted = Quaternion()
        m.extractQuaternion(extracted)
        // Quaternions should match (up to sign)
        val dot = kotlin.math.abs(q.x * extracted.x + q.y * extracted.y + q.z * extracted.z + q.w * extracted.w)
        assertTrue(dot > 0.999f, "Quaternion round-trip via matrix failed: dot=$dot")
    }

    @Test
    fun extractQuaternion90AroundX() {
        val q = Quaternion.axisAngle(Vector3.right(), 90f)
        val m = Matrix()
        m.makeRotation(q)
        val extracted = Quaternion()
        m.extractQuaternion(extracted)
        val dot = kotlin.math.abs(q.x * extracted.x + q.y * extracted.y + q.z * extracted.z + q.w * extracted.w)
        assertTrue(dot > 0.999f, "Quaternion extraction 90 X failed: dot=$dot")
    }

    @Test
    fun extractQuaternion90AroundZ() {
        val q = Quaternion.axisAngle(Vector3(0f, 0f, 1f), 90f)
        val m = Matrix()
        m.makeRotation(q)
        val extracted = Quaternion()
        m.extractQuaternion(extracted)
        val dot = kotlin.math.abs(q.x * extracted.x + q.y * extracted.y + q.z * extracted.z + q.w * extracted.w)
        assertTrue(dot > 0.999f, "Quaternion extraction 90 Z failed: dot=$dot")
    }

    // --- TRS with rotation and scale ---

    @Test
    fun makeTrsWithRotationAndScale() {
        val m = Matrix()
        val t = Vector3(1f, 2f, 3f)
        val r = Quaternion.axisAngle(Vector3.up(), 90f)
        val s = Vector3(2f, 2f, 2f)
        m.makeTrs(t, r, s)

        // Transform a point and verify translation + rotation + scale applied
        val point = m.transformPoint(Vector3(1f, 0f, 0f))
        // Scaled by 2, rotated 90 around Y: (1,0,0) -> (0,0,-2), then translated: (1,2,1)
        assertClose(1f, point.x, 0.05f)
        assertClose(2f, point.y, 0.05f)
        assertClose(1f, point.z, 0.05f)
    }

    @Test
    fun makeTrsDecomposeScaleCorrectly() {
        val m = Matrix()
        m.makeTrs(Vector3(0f, 0f, 0f), Quaternion.identity(), Vector3(3f, 4f, 5f))
        val s = Vector3()
        m.decomposeScale(s)
        assertClose(3f, s.x)
        assertClose(4f, s.y)
        assertClose(5f, s.z)
    }

    // --- Multiply with scale ---

    @Test
    fun multiplyScaleAndTranslation() {
        val scale = Matrix()
        scale.makeScale(2f)
        val trans = Matrix()
        trans.makeTranslation(Vector3(3f, 0f, 0f))
        val result = Matrix()
        // Scale * Translation: first translate, then scale
        Matrix.multiply(scale, trans, result)
        val point = result.transformPoint(Vector3(0f, 0f, 0f))
        assertClose(6f, point.x)
        assertClose(0f, point.y)
        assertClose(0f, point.z)
    }

    @Test
    fun multiplyTranslationAndScale() {
        val trans = Matrix()
        trans.makeTranslation(Vector3(3f, 0f, 0f))
        val scale = Matrix()
        scale.makeScale(2f)
        val result = Matrix()
        // Translation * Scale: first scale, then translate
        Matrix.multiply(trans, scale, result)
        val point = result.transformPoint(Vector3(1f, 0f, 0f))
        // scale(1,0,0) = (2,0,0), then translate = (5,0,0)
        assertClose(5f, point.x)
    }

    // --- Invert rotation ---

    @Test
    fun invertRotation() {
        val m = Matrix()
        m.makeRotation(Quaternion.axisAngle(Vector3.up(), 45f))
        val inv = Matrix()
        assertTrue(Matrix.invert(m, inv))
        val product = Matrix()
        Matrix.multiply(m, inv, product)
        assertTrue(Matrix.equals(product, Matrix()))
    }

    // --- Invert singular matrix ---

    @Test
    fun invertSingularMatrixFails() {
        val m = Matrix()
        // Zero out the matrix to make it singular
        for (i in 0..15) m.data[i] = 0f
        val inv = Matrix()
        assertFalse(Matrix.invert(m, inv))
    }

    // --- Transform direction with scale ---

    @Test
    fun transformDirectionWithScale() {
        val m = Matrix()
        m.makeScale(Vector3(2f, 3f, 4f))
        val dir = m.transformDirection(Vector3(1f, 1f, 1f))
        assertClose(2f, dir.x)
        assertClose(3f, dir.y)
        assertClose(4f, dir.z)
    }

    // --- Copy constructor ---

    @Test
    fun matrixFromFloatArray() {
        val data = floatArrayOf(
            2f, 0f, 0f, 0f,
            0f, 3f, 0f, 0f,
            0f, 0f, 4f, 0f,
            0f, 0f, 0f, 1f
        )
        val m = Matrix(data)
        assertClose(2f, m.data[0])
        assertClose(3f, m.data[5])
        assertClose(4f, m.data[10])
        assertClose(1f, m.data[15])
    }

    @Test
    fun matrixSetFromAnother() {
        val a = Matrix()
        a.makeTranslation(Vector3(7f, 8f, 9f))
        val b = Matrix()
        b.set(a)
        assertTrue(Matrix.equals(a, b))
    }

    // --- decomposeRotation ---

    @Test
    fun decomposeRotationFromTRS() {
        val m = Matrix()
        val q = Quaternion.axisAngle(Vector3.up(), 45f)
        m.makeTrs(Vector3(1f, 2f, 3f), q, Vector3(2f, 2f, 2f))
        val scale = Vector3()
        m.decomposeScale(scale)
        val extractedQ = Quaternion()
        m.decomposeRotation(scale, extractedQ)
        val dot = kotlin.math.abs(q.x * extractedQ.x + q.y * extractedQ.y + q.z * extractedQ.z + q.w * extractedQ.w)
        assertTrue(dot > 0.99f, "decomposeRotation failed: dot=$dot")
    }

    @Test
    fun decomposeRotationLeavesDataUntouched() {
        // Pre-#1126: decomposeRotation(scale, Quaternion) used `this` as scratch space,
        // transiently writing the normalized rotation matrix into `this.data` before
        // restoring the original. Any concurrent reader of `data` during those ~30
        // instructions saw a normalized matrix instead of the full TRS — silently
        // wrong matrices on hot frames where Compose and the render thread both touch
        // the same node transform.
        //
        // Post-fix: `data` is now read-only within decomposeRotation(scale, Quaternion).
        // This test pins that the method never mutates `data`, eliminating the
        // visibility race.
        val m = Matrix()
        val q = Quaternion.axisAngle(Vector3.up(), 45f)
        m.makeTrs(Vector3(1f, 2f, 3f), q, Vector3(2f, 2f, 2f))
        val snapshotBefore = m.data.copyOf()

        val scale = Vector3()
        m.decomposeScale(scale)
        val extractedQ = Quaternion()
        m.decomposeRotation(scale, extractedQ)

        for (i in snapshotBefore.indices) {
            assertTrue(
                kotlin.math.abs(snapshotBefore[i] - m.data[i]) < 1e-6f,
                "decomposeRotation must not mutate this.data — index $i changed " +
                        "from ${snapshotBefore[i]} to ${m.data[i]}"
            )
        }
    }
}
