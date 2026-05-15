package io.github.sceneview.loaders

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Regression contract for issue [#933](https://github.com/sceneview/sceneview/issues/933)
 * — "MaterialLoader/EnvironmentLoader CoroutineScope leaks across composition disposal".
 *
 * Each loader holds a private `CoroutineScope(Dispatchers.IO)`. If it is never cancelled,
 * an in-flight `loadMaterial`/`loadHDREnvironment` job keeps running after the owning
 * composition is disposed and the Filament `Engine` is destroyed — touching a destroyed
 * `Engine` from the completion callback then crashes the JNI layer.
 *
 * Instantiating either loader requires a real Filament `Engine` + GPU (the constructors
 * eagerly build an `UbershaderProvider` / `IBLPrefilter`), so the live disposal path is
 * not exercisable in pure JVM. What we pin here instead is the **API contract** that the
 * fix depends on, plus the **cancellation semantics** of the exact mechanism the loaders'
 * `destroy()` relies on.
 */
class LoaderCoroutineScopeContractTest {

    @Test
    fun `MaterialLoader exposes an injectable CoroutineScope and a destroy method`() {
        assertHasInjectableScopeConstructor(MaterialLoader::class.java)
        assertHasCoroutineScopeField(MaterialLoader::class.java)
        assertHasTeardownMethod(MaterialLoader::class.java, "destroy")
    }

    @Test
    fun `EnvironmentLoader exposes an injectable CoroutineScope and a destroy method`() {
        assertHasInjectableScopeConstructor(EnvironmentLoader::class.java)
        assertHasCoroutineScopeField(EnvironmentLoader::class.java)
        assertHasTeardownMethod(EnvironmentLoader::class.java, "destroy")
        // `clear()` must stay a pure environment-release call — it must NOT cancel the
        // scope, otherwise a mid-life `clear()` silently kills the loader (#933).
        assertHasTeardownMethod(EnvironmentLoader::class.java, "clear")
    }

    @Test
    fun `cancelling the scope stops an in-flight job — the mechanism destroy() relies on`() =
        runBlocking {
            // Mirrors `loadMaterialAsync` / `loadHDREnvironment`: a long-running job launched
            // on the loader's own CoroutineScope(Dispatchers.IO).
            val scope = CoroutineScope(Dispatchers.IO)
            var completedNaturally = false
            val job: Job = scope.launch {
                delay(10_000)
                // Stand-in for the post-load callback that would touch a destroyed Engine.
                completedNaturally = true
            }
            assertTrue("Job should be running before cancel", job.isActive)

            // This is exactly what `MaterialLoader.destroy()` / `EnvironmentLoader.destroy()`
            // do on `DisposableEffect.onDispose`.
            scope.cancel()
            job.join()

            assertTrue("Cancelled job must be cancelled", job.isCancelled)
            assertFalse("Scope must be inactive after cancel", scope.isActive)
            assertFalse(
                "In-flight work must NOT complete after the loader is destroyed (#933)",
                completedNaturally
            )
        }

    // ── Helpers ───────────────────────────────────────────────────────────────────────────────

    private fun assertHasInjectableScopeConstructor(loader: Class<*>) {
        val hasScopeCtor = loader.declaredConstructors.any { ctor ->
            ctor.parameterTypes.any { it == CoroutineScope::class.java }
        }
        assertTrue(
            "${loader.simpleName} must accept a CoroutineScope constructor parameter so its " +
                "lifecycle is owned and cancellable (#933)",
            hasScopeCtor
        )
    }

    private fun assertHasCoroutineScopeField(loader: Class<*>) {
        val scopeField = loader.declaredFields.firstOrNull {
            it.type == CoroutineScope::class.java
        }
        assertNotNull(
            "${loader.simpleName} must hold a CoroutineScope field that destroy() cancels (#933)",
            scopeField
        )
    }

    private fun assertHasTeardownMethod(loader: Class<*>, name: String) {
        val method = loader.declaredMethods.firstOrNull {
            it.name == name && it.parameterCount == 0 && Modifier.isPublic(it.modifiers)
        }
        assertNotNull(
            "${loader.simpleName} must expose a public no-arg `$name()` method (#933)",
            method
        )
    }
}
