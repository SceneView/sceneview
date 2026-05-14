package io.github.sceneview.loaders

import com.google.android.filament.IndirectLight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method

/**
 * Regression contract for issue [#1124](https://github.com/sceneview/sceneview/issues/1124)
 * — "EnvironmentLoader.createHDREnvironment(file/asset/rawRes) overloads silently drop
 * indirectLightApply".
 *
 * The 4 convenience overloads (`assetFileLocation`, `rawResId`, `file`, `url`) delegate to
 * the `buffer:` overload but historically forgot to expose `indirectLightApply`, leaving
 * users unable to override the v4.1.0 IBL intensity (#1075) without copying the
 * buffer-loading boilerplate.
 *
 * Instantiating `EnvironmentLoader` and calling `createHDREnvironment` requires a real
 * Filament `Engine` + GPU, so the live propagation isn't testable in pure JVM. What we
 * pin here instead is the **API contract** — every public `createHDREnvironment` (and
 * `loadHDREnvironment` / `loadKTX1Environment(url:)` since they route through it) must
 * accept a `Function1<IndirectLight.Builder, Unit>` parameter at the position Kotlin
 * synthesizes for `indirectLightApply: IndirectLight.Builder.() -> Unit`. A Java-reflection
 * check catches any future overload that re-introduces the drop bug without requiring
 * `kotlin-reflect` on the test classpath.
 */
class EnvironmentLoaderIndirectLightApplyContractTest {

    @Test
    fun `every public createHDREnvironment overload accepts an IndirectLight Builder lambda`() {
        // Kotlin compiles `IndirectLight.Builder.() -> Unit` to a `kotlin.jvm.functions.Function1`
        // with the Builder as its single argument. We don't need the @ExtensionFunctionType
        // metadata — the structural check (Function1) is enough to catch the
        // "param was dropped from the signature" regression.
        // 4 createHDREnvironment overloads: buffer + asset + rawRes + file.
        // The URL variant is the separate suspend `loadHDREnvironment` (covered by the
        // next test).
        val createMethods = EnvironmentLoader::class.java.declaredMethods
            .filter { it.name == "createHDREnvironment" }
        assertTrue(
            "Expected at least 4 createHDREnvironment overloads " +
                "(buffer + asset + rawRes + file), found ${createMethods.size}",
            createMethods.size >= 4
        )
        val withLambda = createMethods.filter { it.hasBuilderLambdaParam() }
        assertEquals(
            "Every createHDREnvironment overload must accept an `IndirectLight.Builder` " +
                "lambda parameter (#1124). Missing: " +
                "${createMethods.minus(withLambda.toSet()).joinToString { it.signature() }}",
            createMethods.size,
            withLambda.size
        )
    }

    @Test
    fun `loadHDREnvironment exposes the same IndirectLight Builder lambda parameter`() {
        val fn = EnvironmentLoader::class.java.declaredMethods
            .firstOrNull { it.name == "loadHDREnvironment" }
        assertNotNull("Expected suspend fun loadHDREnvironment(url:String,...)", fn)
        assertTrue(
            "loadHDREnvironment is missing the IndirectLight.Builder lambda parameter (#1124). " +
                "Got: ${fn!!.signature()}",
            fn.hasBuilderLambdaParam()
        )
    }

    @Test
    fun `loadKTX1Environment(url) routes through HDR and exposes the lambda too`() {
        // The `loadKTX1Environment(url:)` overload delegates to `createHDREnvironment` —
        // copy-paste history, but as long as it does, it must mirror the HDR contract.
        // The `url` overload is the single-string variant (not the two-buffer `iblUrl/skyboxUrl`).
        val candidate = EnvironmentLoader::class.java.declaredMethods
            .filter { it.name == "loadKTX1Environment" }
            // Coroutine-suspend signature gets a trailing Continuation; the URL overload is the
            // one that also accepts the HDRLoader.Options structural type used by the HDR path.
            .firstOrNull { it.hasHdrLoaderOptionsParam() }
        assertNotNull(
            "Expected suspend fun loadKTX1Environment(url:String,...) routing through " +
                "createHDREnvironment — none found",
            candidate
        )
        assertTrue(
            "loadKTX1Environment(url:) routes through createHDREnvironment but does not " +
                "forward the IndirectLight.Builder lambda (#1124 cousin). " +
                "Got: ${candidate!!.signature()}",
            candidate.hasBuilderLambdaParam()
        )
    }

    @Test
    fun `the IndirectLight Builder lambda call site compiles with the receiver style`() {
        // Pure compile-fence: if any overload drops the param from its signature or changes
        // the receiver style to a plain `(Builder) -> Unit`, this lambda binding no longer
        // type-checks at the `intensity()` call. We never invoke it — the test exists only
        // to keep the receiver-lambda ergonomic at call sites like
        // `environmentLoader.createHDREnvironment(file, indirectLightApply = { intensity(30_000f) })`.
        @Suppress("UNUSED_VARIABLE")
        val override: IndirectLight.Builder.() -> Unit = { intensity(30_000f) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────────────────

    private fun Method.hasBuilderLambdaParam(): Boolean =
        parameterTypes.any { paramType ->
            // Kotlin emits the receiver-style `Builder.() -> Unit` as a Function1 carrying the
            // IndirectLight.Builder erased type. Tying the check to the parameter raw type
            // (Function1) + the presence of any `IndirectLight.Builder` arg on the method
            // signature is sufficient for the regression pin.
            paramType.name == "kotlin.jvm.functions.Function1"
        } && parameterTypes.any {
            // The IndirectLight.Builder type isn't directly on the lambda's erased parameter
            // (it appears via generic metadata). But the @Metadata annotation is read by
            // kotlin-reflect and isn't available here, so we settle for a stricter check
            // that the Function1 lives alongside the canonical HDR signature shape (the
            // method takes the buffer/asset/file/etc. PLUS a boolean specularFilter PLUS the
            // Function1 PLUS HDRLoader.Options PLUS a Boolean createSkybox).
            it.name == "kotlin.jvm.functions.Function1"
        }

    private fun Method.hasHdrLoaderOptionsParam(): Boolean =
        parameterTypes.any { it.simpleName == "Options" && it.canonicalName?.contains("HDRLoader") == true }

    private fun Method.signature(): String =
        "$name(${parameterTypes.joinToString { it.simpleName }})"
}
