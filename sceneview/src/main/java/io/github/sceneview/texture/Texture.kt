package io.github.sceneview.texture

import com.google.android.filament.Engine
import com.google.android.filament.Texture

/**
 * Runs [block] with this [Texture], then destroys it on [engine] before returning — analogous
 * to Kotlin's standard `use` for closeables. Use this for short-lived textures whose lifetime
 * is scoped to a single operation so the native Filament resource is always released.
 *
 * Must be called on the main thread. The texture is destroyed even if [block] returns early;
 * do not retain the texture reference after this call.
 *
 * @return the value produced by [block].
 */
fun <R> Texture.use(engine: Engine, block: (Texture) -> R): R = block(this).also {
    engine.destroyTexture(this)
}