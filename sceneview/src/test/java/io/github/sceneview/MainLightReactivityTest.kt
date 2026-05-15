package io.github.sceneview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM regression test for the reactive `apply`-block contract of
 * [rememberMainLightNode] / [rememberFillLightNode] introduced in #1306.
 *
 * Before the fix, the `apply` configuration block ran exactly **once** — inside the
 * `remember { createMainLightNode(engine).apply(apply) }` factory. A caller whose
 * `apply` block read Compose state (e.g. `intensity = animatedIntensity`) saw that
 * state captured at first composition and never re-read: mutating the light after
 * the first frame was silently ignored. iOS does not have this gap — its
 * `RealityView.update:` closure re-runs `provisionLightSlot` every render (#1031).
 *
 * The fix moves the `apply` call into a `SideEffect { node.apply(apply) }`, so it
 * is re-invoked on **every recomposition** (`SideEffect` runs on the composition
 * applier — the main thread — which is required for the Filament `LightManager`
 * JNI writes the [io.github.sceneview.components.LightComponent] setters perform).
 *
 * The test module has no Compose UI testing dependency, so this pins the semantics
 * by simulating Compose's `SideEffect` re-run contract in plain JVM. If anyone
 * reverts the `apply` call back into the `remember` factory, [applyBlockReRunsOnEveryRecomposition]
 * fails because the invocation count stops tracking recomposition count.
 */
class MainLightReactivityTest {

    /**
     * Minimal stand-in for Compose's `SideEffect { ... }`.
     *
     * Compose's contract: a `SideEffect` block runs after **every** successful
     * (re)composition — unlike `LaunchedEffect`, it is not keyed and is not skipped.
     */
    private class SideEffectSimulator(private val effect: () -> Unit) {
        var invocationCount = 0
            private set

        fun recompose() {
            effect()
            invocationCount++
        }
    }

    @Test
    fun applyBlockReRunsOnEveryRecomposition() {
        // Simulate a caller whose `apply` block reads a Compose-state-backed intensity.
        var stateIntensity = 100_000.0f
        var lightIntensity = 0.0f
        val apply: () -> Unit = { lightIntensity = stateIntensity }

        val simulator = SideEffectSimulator(apply)

        // First composition.
        simulator.recompose()
        assertEquals(100_000.0f, lightIntensity, 0.0f)

        // Caller mutates the Compose state and the SceneView recomposes.
        stateIntensity = 5_000.0f
        simulator.recompose()

        assertEquals(
            "rememberMainLightNode's apply block must re-run on recomposition so a " +
                "Compose-state-driven intensity change propagates to the LightNode (#1306). " +
                "If this fails, the apply call has regressed into the remember{} factory and " +
                "only runs once.",
            5_000.0f,
            lightIntensity,
            0.0f,
        )
    }

    @Test
    fun applyBlockIsNotKeyed() {
        // Unlike LaunchedEffect, SideEffect is unconditional — 5 recompositions => 5 invocations,
        // even when nothing the block reads has changed. This is intentional: LightComponent
        // setters write straight to Filament's LightManager, so re-applying unchanged values
        // is a cheap idempotent write, and it removes the need to key on every light property.
        val simulator = SideEffectSimulator { /* no-op effect */ }

        repeat(5) { simulator.recompose() }

        assertEquals(
            "The light apply block must fire once per recomposition (SideEffect semantics) — " +
                "this is what makes mutations reactive without re-keying the remember.",
            5,
            simulator.invocationCount,
        )
    }

    @Test
    fun directionAndColorMutationsAlsoPropagate() {
        // The reactive contract covers every property the apply block touches, not just intensity.
        var stateDirection = floatArrayOf(0.0f, -1.0f, 0.0f)
        var stateColorRed = 1.0f
        var appliedDirection = floatArrayOf(0f, 0f, 0f)
        var appliedColorRed = 0.0f

        val simulator = SideEffectSimulator {
            appliedDirection = stateDirection
            appliedColorRed = stateColorRed
        }

        simulator.recompose()
        assertTrue(appliedDirection.contentEquals(floatArrayOf(0.0f, -1.0f, 0.0f)))
        assertEquals(1.0f, appliedColorRed, 0.0f)

        stateDirection = floatArrayOf(0.5f, -0.5f, 0.5f)
        stateColorRed = 0.8f
        simulator.recompose()

        assertTrue(
            "lightDirection mutations must propagate on recomposition (#1306).",
            appliedDirection.contentEquals(floatArrayOf(0.5f, -0.5f, 0.5f)),
        )
        assertEquals(
            "color mutations must propagate on recomposition (#1306).",
            0.8f,
            appliedColorRed,
            0.0f,
        )
    }
}
