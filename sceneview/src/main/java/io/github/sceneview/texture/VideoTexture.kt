package io.github.sceneview.texture

import android.graphics.Bitmap
import com.google.android.filament.Engine
import com.google.android.filament.Stream
import com.google.android.filament.Texture
import com.google.android.filament.android.TextureHelper

/**
 * Helper for creating a Filament [Texture] backed by an external video [Stream] — typically
 * a `SurfaceTexture` fed by `MediaPlayer`, `ExoPlayer`, or the camera.
 *
 * Use the nested [Builder] to construct the texture. All operations touch the Filament engine
 * and must run on the main thread.
 */
class VideoTexture {
    /**
     * Builder for a [VideoTexture]-backed Filament [Texture]. Set the source [stream], then
     * call [build].
     *
     * The texture is configured as an external sampler (`SAMPLER_EXTERNAL`) with `RGBA8`
     * format, as required for streamed video content.
     */
    class Builder : Texture.Builder() {
        private lateinit var stream: Stream

        init {
            sampler(Texture.Sampler.SAMPLER_EXTERNAL)
            format(Texture.InternalFormat.RGBA8)
        }

        /** Sets the external video [stream] that feeds this texture. Returns this builder for chaining. */
        fun stream(stream: Stream) = apply {
            this.stream = stream
        }

        /**
         * Builds the Filament [Texture] on [engine] and binds the configured external stream.
         * A stream must have been set first via [stream]. Must be called on the main thread.
         */
        override fun build(engine: Engine): Texture = super.build(engine).apply {
            setExternalStream(engine, stream)
        }
    }
}

/**
 * Uploads [bitmap] into this [Texture]'s base mip level. Provided as a convenience for
 * pushing a single still frame into a video-style texture. Must be called on the main thread.
 */
fun Texture.setStream(engine: Engine, bitmap: Bitmap) =
    TextureHelper.setBitmap(
        engine,
        this,
        0,
        bitmap
    )