package io.github.sceneview.ar.light

import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Regression pin for PR [#1095](https://github.com/sceneview/sceneview/pull/1095)
 * (#1094) — `@Volatile` on [LightEstimator]'s 7 cross-thread toggles.
 *
 * The Filament `PixelBufferDescriptor` upload callback fires on Filament's
 * render thread, while the public `environmentalHdr*` toggles + `isEnabled`
 * are flipped from the AR / UI thread. Without `@Volatile` the render thread
 * could observe a stale toggle value indefinitely (Java Memory Model: no
 * happens-before edge between an ordinary write and a read on another thread).
 *
 * A Kotlin `@Volatile` property compiles to the JVM `volatile` modifier on the
 * backing field, so this concurrency invariant is observable by pure-JVM
 * reflection — no Filament `Engine` instance required. A future contributor who
 * "cleans up" one of these annotations away gets caught here (#1120).
 *
 * Scope note: #1120 explicitly flags the reflection-based `@Volatile` check as
 * acceptable. Keeping it pins the invariant cheaply.
 */
class LightEstimatorVolatileTest {

    /**
     * The 7 fields PR #1095 annotated `@Volatile`. Names are the Kotlin
     * property names; the backing field carries the same name.
     */
    private val volatileFields = listOf(
        "isEnabled",
        "environmentalHdrReflections",
        "environmentalHdrSphericalHarmonics",
        "environmentalHdrSpecularFilter",
        "environmentalHdrMainLightDirection",
        "environmentalHdrMainLightIntensity",
        "uploadInFlight",
    )

    @Test
    fun `LightEstimator cross-thread toggles carry @Volatile (#1095)`() {
        volatileFields.forEach { name ->
            val field = LightEstimator::class.java.getDeclaredField(name)
            assertTrue(
                "LightEstimator.$name MUST be `@Volatile` — it is written and read " +
                    "from different threads (AR/UI thread vs Filament render thread). " +
                    "Dropping `@Volatile` re-introduces the #1094 stale-read race (#1095).",
                Modifier.isVolatile(field.modifiers),
            )
        }
    }
}
