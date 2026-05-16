package io.github.sceneview.web

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression tests for [AutoCenterGate] — the one-shot gate that drives
 * `SceneView`'s library-level `autoCenterContent` pass.
 *
 * The headline case is **#1357**: `SceneView.loadModel` calls
 * [AutoCenterGate.reset] every time a model is added, so loading a 2nd model
 * after the 1st was already centred re-runs the centring pass. Before the fix
 * the flag was a write-once `didCenterContent = true` that never reset, so the
 * 2nd model stayed off-centre. [loadingSecondModelReRunsTheCenteringPass] pins
 * exactly that.
 */
class AutoCenterGateTest {

    @Test
    fun freshGateHasNotCenteredYet() {
        val gate = AutoCenterGate()
        assertFalse(gate.didCenter, "a fresh gate must not have centred anything yet")
    }

    @Test
    fun shouldCenterWhenEnabledWithContentAndNotYetCentered() {
        val gate = AutoCenterGate()
        assertTrue(
            gate.shouldCenter(enabled = true, hasContent = true),
            "the first frame with loaded content must run the centring pass",
        )
    }

    @Test
    fun shouldNotCenterWhenAutoCenterContentIsDisabled() {
        val gate = AutoCenterGate()
        assertFalse(
            gate.shouldCenter(enabled = false, hasContent = true),
            "autoCenterContent = false must suppress the pass entirely",
        )
    }

    @Test
    fun shouldNotCenterWhenThereIsNoContent() {
        val gate = AutoCenterGate()
        assertFalse(
            gate.shouldCenter(enabled = true, hasContent = false),
            "with no models loaded there is nothing to centre",
        )
    }

    @Test
    fun centeringIsAOneShot() {
        val gate = AutoCenterGate()
        assertTrue(gate.shouldCenter(enabled = true, hasContent = true))
        gate.markCentered()
        // Every subsequent frame must be a cheap no-op.
        assertFalse(
            gate.shouldCenter(enabled = true, hasContent = true),
            "once centred, later frames must not re-run the pass",
        )
        assertFalse(
            gate.shouldCenter(enabled = true, hasContent = true),
            "the no-op must hold frame after frame",
        )
        assertTrue(gate.didCenter)
    }

    /**
     * #1357: loading a 2nd model resets the gate so the centring pass runs
     * again — the combined bounds changed, so the previous centre is stale.
     */
    @Test
    fun loadingSecondModelReRunsTheCenteringPass() {
        val gate = AutoCenterGate()

        // Frame 1: first model loaded -> pass runs and marks centred.
        assertTrue(gate.shouldCenter(enabled = true, hasContent = true))
        gate.markCentered()
        assertFalse(
            gate.shouldCenter(enabled = true, hasContent = true),
            "after the 1st model is centred the pass is dormant",
        )

        // A 2nd loadModel() call resets the gate (this is the #1357 fix).
        gate.reset()
        assertFalse(gate.didCenter, "reset() must clear the centred flag")
        assertTrue(
            gate.shouldCenter(enabled = true, hasContent = true),
            "#1357: a 2nd loadModel must re-arm the centring pass",
        )
    }

    @Test
    fun resetIsIdempotentBeforeCentering() {
        val gate = AutoCenterGate()
        gate.reset()
        gate.reset()
        assertFalse(gate.didCenter)
        assertTrue(
            gate.shouldCenter(enabled = true, hasContent = true),
            "repeated resets must leave the gate armed",
        )
    }

    @Test
    fun reCenteringWorksAcrossMultipleModelLoads() {
        // Simulate loadModel -> center -> loadModel -> center -> loadModel.
        val gate = AutoCenterGate()
        repeat(3) { load ->
            gate.reset() // each loadModel() resets
            assertTrue(
                gate.shouldCenter(enabled = true, hasContent = true),
                "model load #$load must re-arm the centring pass",
            )
            gate.markCentered()
            assertFalse(
                gate.shouldCenter(enabled = true, hasContent = true),
                "after centring model load #$load the pass goes dormant",
            )
        }
    }
}
