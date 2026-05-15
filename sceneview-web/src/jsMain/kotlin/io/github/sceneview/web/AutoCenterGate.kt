package io.github.sceneview.web

/**
 * One-shot gate for the library-level `autoCenterContent` pass.
 *
 * The auto-centre pass ([SceneView.refreshContentCentering]) must run exactly
 * once per "content generation": after a model finishes loading, the first
 * render frame with non-degenerate bounds translates the content to the
 * origin, and every later frame is a cheap no-op — until *new* content is
 * loaded, which starts a fresh generation.
 *
 * This class isolates that state machine from any Filament.js binding so the
 * #1357 regression (`didCenterContent` never reset on a 2nd `loadModel`, so
 * the second model stayed off-centre) is directly unit-testable without a
 * WebGL context. Mirrors iOS's `didCenterContent` flag and Android's
 * `SceneAutoCenterState`.
 */
internal class AutoCenterGate {

    /** `true` once the current content generation has been centred. */
    var didCenter: Boolean = false
        private set

    /**
     * Whether the centring pass should run this frame.
     *
     * @param enabled the consumer-facing `autoCenterContent` toggle
     * @param hasContent whether any model is currently loaded
     */
    fun shouldCenter(enabled: Boolean, hasContent: Boolean): Boolean =
        enabled && hasContent && !didCenter

    /** Mark the current content generation as centred — subsequent frames no-op. */
    fun markCentered() {
        didCenter = true
    }

    /**
     * Start a new content generation. Called whenever a model is added
     * (`loadModel` / `addGeometry`): the union bounds changed, so the one-shot
     * pass must run again. This is the exact reset that fixes #1357.
     */
    fun reset() {
        didCenter = false
    }
}
