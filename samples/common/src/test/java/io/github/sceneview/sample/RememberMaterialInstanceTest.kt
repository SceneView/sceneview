package io.github.sceneview.sample

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression tests for [rememberMaterialInstance] / [rememberUnlitMaterialInstance].
 *
 * Why these are source-anchored (not full Compose-UI tests)
 * =========================================================
 * The bug the #937 review caught — `metallic` / `roughness` / `reflectance`
 * leaking into the `remember(...)` key, so every 60 Hz slider tick destroys
 * and re-allocates the underlying Filament `MaterialInstance` — lives entirely
 * in *which keys* the helper passes to `remember`. There is no behaviour to
 * exercise: `MaterialLoader` is a `final` class wired to a native Filament
 * `Engine`, so it cannot be faked, and `:samples:common` ships no
 * `compose-ui-test` runtime to drive a real recomposition.
 *
 * So the regression is pinned the only way that is both genuine and minimal:
 * by asserting the *exact key tuple* in the helper's source. The contract is
 * a static property of that one `remember(...)` call — a source assertion
 * pins it precisely, with zero production change and no flaky JNI dependency.
 * `ARCompletenessDefaultsTest` in `:arsceneview` uses the same source-anchored
 * approach.
 *
 * Acceptance (#972): "the PBR-key regression-pin test must fail if a future
 * maintainer adds `metallic` back to the `remember(...)` key."
 */
class RememberMaterialInstanceTest {

    // Gradle runs unit tests with the working dir set to the module root
    // (`samples/common`), so this relative path resolves. Same convention as
    // `arsceneview`'s `ARCompletenessDefaultsTest`.
    private val source: String by lazy {
        val f = File("src/main/java/io/github/sceneview/sample/RememberMaterialInstance.kt")
        assertTrue(
            "RememberMaterialInstance.kt not found at ${f.absolutePath} — " +
                "did the helper move? Update this test's path.",
            f.exists(),
        )
        f.readText()
    }

    /**
     * The body of the single `remember(...)` call inside [rememberMaterialInstance].
     * Captures everything between `remember(` and the matching `)`.
     */
    private fun litRememberKeys(): String {
        val marker = "val instance = remember("
        val start = source.indexOf(marker)
        assertTrue(
            "could not locate the `val instance = remember(` call in " +
                "rememberMaterialInstance — was it refactored?",
            start >= 0,
        )
        val keysStart = start + marker.length
        val keysEnd = source.indexOf(')', keysStart)
        assertTrue("malformed remember(...) call", keysEnd > keysStart)
        return source.substring(keysStart, keysEnd).trim()
    }

    @Test
    fun `lit instance is remembered on materialLoader and color only`() {
        val keys = litRememberKeys()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        assertEquals(
            "rememberMaterialInstance must key its remember(...) on exactly " +
                "[materialLoader, color] — found $keys",
            listOf("materialLoader", "color"),
            keys,
        )
    }

    @Test
    fun `metallic roughness reflectance must NOT be remember keys`() {
        // This is the #937 PBR-key regression pin. If a future maintainer
        // writes `remember(materialLoader, color, metallic)` the slider-driven
        // demos churn the MaterialInstance at 60 Hz again — fail loudly here.
        val keys = litRememberKeys()
        for (forbidden in listOf("metallic", "roughness", "reflectance")) {
            assertFalse(
                "PBR-key regression (#937): `$forbidden` is back in the " +
                    "remember(...) key of rememberMaterialInstance — slider " +
                    "ticks will now destroy + recreate the MaterialInstance " +
                    "every frame. Keep PBR params out of the key; push them " +
                    "via setMetallic/setRoughness/setReflectance instead.",
                Regex("\\b$forbidden\\b").containsMatchIn(keys),
            )
        }
    }

    @Test
    fun `PBR params are still applied via a DisposableEffect`() {
        // The flip side of keeping PBR params out of the remember key: dynamic
        // updates must still reach the instance. Pin that the setters run in an
        // effect keyed on the params, so a slider tick re-applies them.
        assertTrue(
            "expected a DisposableEffect keyed on (instance, metallic, " +
                "roughness, reflectance) to push live PBR updates",
            Regex(
                "DisposableEffect\\(\\s*instance\\s*,\\s*metallic\\s*,\\s*" +
                    "roughness\\s*,\\s*reflectance\\s*\\)",
            ).containsMatchIn(source),
        )
        for (setter in listOf("setMetallic", "setRoughness", "setReflectance")) {
            assertTrue(
                "rememberMaterialInstance must call instance.$setter(...) so " +
                    "PBR updates reach the instance without churning it",
                source.contains("$setter("),
            )
        }
    }

    @Test
    fun `both helpers destroy their instance on dispose`() {
        // Pin the leak fix (#937): every allocated MaterialInstance must be
        // reclaimed in an onDispose. Two allocations (lit + unlit) → two
        // destroyMaterialInstance calls.
        val destroyCalls =
            Regex("materialLoader\\.destroyMaterialInstance\\(instance\\)")
                .findAll(source)
                .count()
        assertEquals(
            "expected rememberMaterialInstance and rememberUnlitMaterialInstance " +
                "to each destroy their instance in onDispose (the #937 leak fix)",
            2,
            destroyCalls,
        )
        assertTrue(
            "the destroy call must sit inside an onDispose { } block",
            Regex("onDispose\\s*\\{[^}]*destroyMaterialInstance").containsMatchIn(source),
        )
    }

    @Test
    fun `unlit instance is remembered on materialLoader and color only`() {
        // The unlit helper takes no PBR params, but pin its key too so a future
        // edit that adds e.g. an alpha-mode param does not silently churn it.
        val marker = "val instance = remember("
        // Second occurrence = the one inside rememberUnlitMaterialInstance.
        val first = source.indexOf(marker)
        val second = source.indexOf(marker, first + marker.length)
        assertTrue("could not locate the unlit remember(...) call", second >= 0)
        val keysStart = second + marker.length
        val keysEnd = source.indexOf(')', keysStart)
        val keys = source.substring(keysStart, keysEnd)
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        assertEquals(
            "rememberUnlitMaterialInstance must key remember(...) on exactly " +
                "[materialLoader, color] — found $keys",
            listOf("materialLoader", "color"),
            keys,
        )
    }
}
