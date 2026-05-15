package io.github.sceneview.texture

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.annotation.IntRange
import com.google.android.filament.Engine
import com.google.android.filament.Texture
import com.google.android.filament.android.TextureHelper
import com.google.android.filament.utils.TextureType
import io.github.sceneview.utils.readBuffer

/**
 * Helper for creating Filament [Texture]s from Android [Bitmap]s, asset files, or drawable
 * resources, with automatic mipmap generation.
 *
 * Use the nested [Builder] to construct a texture, or the [Texture.setBitmap] extensions to
 * upload image data into an existing texture. All operations touch the Filament engine and
 * must run on the main thread.
 */
class ImageTexture {
    /**
     * Builder for an [ImageTexture]-backed Filament [Texture]. Supply image data via one of
     * the [bitmap] overloads, then call [build].
     *
     * The texture is configured with `SAMPLER_2D`, automatic mip-level count, and the
     * `GEN_MIPMAPPABLE` usage flag so [build] can generate mipmaps.
     */
    class Builder : Texture.Builder() {
        private lateinit var bitmap: Bitmap

        init {
            sampler(Texture.Sampler.SAMPLER_2D)
            // This tells Filament to figure out the number of mip levels
            levels(0xff)
            // Include GEN_MIPMAPPABLE so generateMipmaps() works in build().
            // DEFAULT (UPLOADABLE | SAMPLEABLE) alone causes a fatal assertion
            // in Filament 1.70+ when generateMipmaps is called.
            usage(Texture.Usage.DEFAULT or Texture.Usage.GEN_MIPMAPPABLE)
        }

        /**
         * Sets the texture internal format from a semantic [type]: `COLOR` maps to sRGB
         * (gamma-corrected), `NORMAL` and `DATA` map to linear RGBA8. Returns this builder
         * for chaining.
         */
        fun type(type: TextureType) = apply {
            format(
                when (type) {
                    TextureType.COLOR -> Texture.InternalFormat.SRGB8_A8
                    TextureType.NORMAL -> Texture.InternalFormat.RGBA8
                    TextureType.DATA -> Texture.InternalFormat.RGBA8
                }
            )
        }

        /**
         * Sets the texture dimensions, format and source data from an in-memory [bitmap].
         * Returns this builder for chaining.
         *
         * @param type semantic format of the image; defaults to [DEFAULT_TYPE] ([TextureType.COLOR]).
         */
        fun bitmap(bitmap: Bitmap, type: TextureType = DEFAULT_TYPE) = apply {
            width(bitmap.width)
            height(bitmap.height)
            type(type)
            this.bitmap = bitmap
        }

        /**
         * Decodes an image from the app assets at [fileLocation] and uses it as the source.
         * Returns this builder for chaining.
         *
         * @param type semantic format of the image; defaults to [DEFAULT_TYPE].
         */
        fun bitmap(
            assets: AssetManager,
            fileLocation: String,
            type: TextureType = DEFAULT_TYPE
        ) = bitmap(getBitmap(assets, fileLocation, type), type)

        /**
         * Decodes a drawable resource [drawableResId] and uses it as the source.
         * Returns this builder for chaining.
         *
         * @param type semantic format of the image; defaults to [DEFAULT_TYPE].
         */
        fun bitmap(
            context: Context,
            @DrawableRes drawableResId: Int,
            type: TextureType = DEFAULT_TYPE
        ) = bitmap(getBitmap(context, drawableResId, type), type)

        /**
         * Builds the Filament [Texture] on [engine], uploads the configured bitmap, and
         * generates its mipmap chain. A source bitmap must have been set first via one of the
         * [bitmap] overloads. Must be called on the main thread.
         */
        override fun build(engine: Engine): Texture = super.build(engine).apply {
            // TextureHelper offers a method that skips the copy of the bitmap into a ByteBuffer
            setBitmap(engine, bitmap)
            generateMipmaps(engine)
        }
    }

    companion object {
        /** Default semantic texture type used when none is specified: [TextureType.COLOR]. */
        val DEFAULT_TYPE = TextureType.COLOR

        /**
         * Decodes a [Bitmap] from the app assets at [fileLocation]. Alpha is pre-multiplied
         * only for [TextureType.COLOR] textures, matching Filament's expectation.
         */
        fun getBitmap(
            assets: AssetManager,
            fileLocation: String,
            type: TextureType = DEFAULT_TYPE
        ): Bitmap {
            val buffer = assets.readBuffer(fileLocation)
            return BitmapFactory.decodeByteArray(
                buffer.array(),
                0,
                buffer.capacity(),
                BitmapFactory.Options().apply {
                    // Color is the only type of texture we want to pre-multiply with the alpha
                    // channel. Pre-multiplication is the default behavior, so we need to turn it
                    // off here
                    inPremultiplied = type == TextureType.COLOR
                })
        }

        /**
         * Decodes a [Bitmap] from the drawable resource [drawableResId]. Alpha is
         * pre-multiplied only for [TextureType.COLOR] textures.
         */
        fun getBitmap(
            context: Context,
            @DrawableRes drawableResId: Int,
            type: TextureType = DEFAULT_TYPE
        ) = BitmapFactory.decodeResource(
            context.resources,
            drawableResId,
            BitmapFactory.Options().apply {
                // Color is the only type of texture we want to pre-multiply with the alpha
                // channel.
                // Pre-multiplication is the default behavior, so we need to turn it off here
                inPremultiplied = type == TextureType.COLOR
            })
    }
}

/**
 * Uploads an image decoded from the app assets at [fileLocation] into this [Texture] at the
 * given mip [level]. Must be called on the main thread.
 *
 * @param level destination mip level, `0` for the base level.
 */
fun Texture.setBitmap(
    engine: Engine,
    assets: AssetManager,
    fileLocation: String,
    type: TextureType = ImageTexture.DEFAULT_TYPE,
    @IntRange(from = 0) level: Int = 0
) = setBitmap(engine, ImageTexture.getBitmap(assets, fileLocation, type), level)

/**
 * Uploads an image decoded from the drawable resource [drawableResId] into this [Texture] at
 * the given mip [level]. Must be called on the main thread.
 *
 * @param level destination mip level, `0` for the base level.
 */
fun Texture.setBitmap(
    engine: Engine,
    context: Context,
    @DrawableRes drawableResId: Int,
    type: TextureType = ImageTexture.DEFAULT_TYPE,
    @IntRange(from = 0) level: Int = 0
) = setBitmap(engine, ImageTexture.getBitmap(context, drawableResId, type), level)

/**
 * Uploads [bitmap] into this [Texture] at the given mip [level], without an intermediate
 * `ByteBuffer` copy. The bitmap dimensions must match the texture level. Must be called on
 * the main thread.
 *
 * @param level destination mip level, `0` for the base level.
 */
fun Texture.setBitmap(engine: Engine, bitmap: Bitmap, @IntRange(from = 0) level: Int = 0) =
    TextureHelper.setBitmap(
        engine,
        this,
        // This tells Filament to figure out the number of mip levels
        level,
        bitmap
    )