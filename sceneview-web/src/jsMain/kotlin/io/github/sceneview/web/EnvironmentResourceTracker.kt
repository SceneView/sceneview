package io.github.sceneview.web

/**
 * Tracks a single GPU resource handle and guarantees leak-free replacement
 * and teardown.
 *
 * The web `SceneView` creates a Filament `IndirectLight` and `Skybox` in
 * [SceneView.loadEnvironment]. Before issue #1496 those handles were never
 * stored, so:
 *
 * 1. [SceneView.destroy] tore down the renderer/view/scene but never called
 *    `engine.destroyIndirectLight` / `engine.destroySkybox` — every web
 *    `SceneView` leaked its IBL + skybox GPU memory.
 * 2. Calling `loadEnvironment` twice (or `loadDefaultEnvironment` after a
 *    manual one) overwrote `scene.setIndirectLight` / `setSkybox` with a fresh
 *    handle while the previous one was silently orphaned — a second leak.
 *
 * This class isolates the "destroy the old handle before adopting a new one,
 * destroy whatever is held on teardown" state machine from the Filament.js
 * bindings so it is directly unit-testable without a WebGL context — the same
 * approach [AutoCenterGate] uses for the auto-centre pass.
 *
 * @param T the handle type (`IndirectLight` or `Skybox`)
 * @param destroyer invoked with a handle that must be released on the GPU
 */
internal class EnvironmentResourceTracker<T>(
    private val destroyer: (T) -> Unit,
) {

    /** The handle currently held, or `null` if none has been adopted. */
    var current: T? = null
        private set

    /**
     * Adopt [handle] as the current resource, destroying the previously held
     * handle (if any) first. This is the leak-free replacement path for a 2nd
     * `loadEnvironment` call.
     */
    fun replaceWith(handle: T) {
        current?.let(destroyer)
        current = handle
    }

    /**
     * Destroy the currently held handle (if any) and clear the slot. Idempotent
     * — a second call is a no-op. Called from [SceneView.destroy].
     */
    fun release() {
        current?.let(destroyer)
        current = null
    }
}
