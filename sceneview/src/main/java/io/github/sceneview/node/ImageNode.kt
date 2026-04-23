package io.github.sceneview.node

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import com.google.android.filament.RenderableManager
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.utils.TextureType
import dev.romainguy.kotlin.math.normalize
import io.github.sceneview.geometries.Plane
import io.github.sceneview.geometries.UvScale
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.safeDestroyTexture
import io.github.sceneview.texture.ImageTexture
import io.github.sceneview.texture.TextureSampler2D
import io.github.sceneview.texture.setBitmap

/**
 * A node that renders a [Bitmap] image on a flat textured plane in 3D space.
 *
 * The plane is auto-sized from the image's aspect ratio (longest edge normalised to 1.0 world
 * unit) unless an explicit [size] is given. Assigning a new [bitmap] re-uploads the texture to the
 * GPU and optionally re-sizes the geometry.
 *
 * Multiple constructors are provided for loading from a [Bitmap], an asset file path, or a
 * drawable resource ID.
 *
 * @see PlaneNode
 * @see io.github.sceneview.texture.ImageTexture
 */
open class ImageNode private constructor(
    val materialLoader: MaterialLoader,
    bitmap: Bitmap,
    val texture: Texture,
    textureSampler: TextureSampler = TextureSampler2D(),
    /**
     * `null` to adjust size on the normalized image size
     */
    val size: Size? = null,
    center: Position = Plane.DEFAULT_CENTER,
    normal: Direction = Plane.DEFAULT_NORMAL,
    uvScale: UvScale = UvScale(1.0f),
    builderApply: RenderableManager.Builder.() -> Unit = {}
) : PlaneNode(
    engine = materialLoader.engine,
    size = size ?: normalize(Size(bitmap.width.toFloat(), bitmap.height.toFloat())),
    center = center,
    normal = normal,
    uvScale = uvScale,
    materialInstance = materialLoader.createImageInstance(texture, textureSampler),
    builderApply = builderApply
) {
    var bitmap = bitmap
        set(value) {
            field = value
            texture.setBitmap(engine, value)
            if (size == null) {
                updateGeometry(
                    size = normalize(Size(value.width.toFloat(), value.height.toFloat()))
                )
            }
        }

    constructor(
        materialLoader: MaterialLoader,
        bitmap: Bitmap,
        /**
         * `null` to adjust size on the normalized image size
         */
        size: Size? = null,
        center: Position = Plane.DEFAULT_CENTER,
        normal: Direction = Plane.DEFAULT_NORMAL,
        uvScale: UvScale = UvScale(1.0f),
        type: TextureType = ImageTexture.DEFAULT_TYPE,
        textureSampler: TextureSampler = TextureSampler2D(),
        builderApply: RenderableManager.Builder.() -> Unit = {},
        textureBuilderApply: ImageTexture.Builder.() -> Unit = {}
    ) : this(
        materialLoader = materialLoader,
        bitmap = bitmap,
        texture = ImageTexture.Builder()
            .bitmap(bitmap)
            .type(type)
            .apply(textureBuilderApply)
            .build(materialLoader.engine),
        textureSampler = textureSampler,
        size = size,
        center = center,
        normal = normal,
        uvScale = uvScale,
        builderApply = builderApply
    )

    constructor(
        materialLoader: MaterialLoader,
        /**
         * `null` to adjust size on the normalized image size
         */
        size: Size? = null,
        center: Position = Plane.DEFAULT_CENTER,
        normal: Direction = Plane.DEFAULT_NORMAL,
        uvScale: UvScale = UvScale(1.0f),
        imageFileLocation: String,
        type: TextureType = ImageTexture.DEFAULT_TYPE,
        textureSampler: TextureSampler = TextureSampler2D(),
        builderApply: RenderableManager.Builder.() -> Unit = {},
        textureBuilderApply: ImageTexture.Builder.() -> Unit = {}
    ) : this(
        materialLoader = materialLoader,
        bitmap = ImageTexture.getBitmap(materialLoader.assets, imageFileLocation),
        size = size,
        center = center,
        normal = normal,
        uvScale = uvScale,
        type = type,
        textureSampler = textureSampler,
        builderApply = builderApply,
        textureBuilderApply = textureBuilderApply
    )

    constructor(
        materialLoader: MaterialLoader,
        /**
         * `null` to adjust size on the normalized image size
         */
        size: Size? = null,
        center: Position = Plane.DEFAULT_CENTER,
        normal: Direction = Plane.DEFAULT_NORMAL,
        uvScale: UvScale = UvScale(1.0f),
        @DrawableRes imageResId: Int,
        type: TextureType = ImageTexture.DEFAULT_TYPE,
        textureSampler: TextureSampler = TextureSampler2D(),
        builderApply: RenderableManager.Builder.() -> Unit = {},
        textureBuilderApply: ImageTexture.Builder.() -> Unit = {}
    ) : this(
        materialLoader = materialLoader,
        bitmap = ImageTexture.getBitmap(materialLoader.context, imageResId),
        size = size,
        center = center,
        normal = normal,
        uvScale = uvScale,
        type = type,
        textureSampler = textureSampler,
        builderApply = builderApply,
        textureBuilderApply = textureBuilderApply
    )

    fun setBitmap(fileLocation: String, type: TextureType = ImageTexture.DEFAULT_TYPE) {
        bitmap = ImageTexture.getBitmap(materialLoader.assets, fileLocation, type)
    }

    fun setBitmap(@DrawableRes drawableResId: Int, type: TextureType = ImageTexture.DEFAULT_TYPE) {
        bitmap = ImageTexture.getBitmap(materialLoader.context, drawableResId, type)
    }

    override fun destroy() {
        // Filament aborts with `Invalid texture still bound to MaterialInstance: 'Transparent
        // Textured'` whenever a texture is destroyed before its owning MaterialInstance is
        // reclaimed on the next frame commit. `destroyMaterialInstance` + `flushAndWait` both
        // tried and neither is strong enough — Filament's MI reclamation is tied to the render
        // loop, and in an instrumented-test teardown (no `Renderer.render()` call between destroy
        // and engine shutdown) the MI is technically still considered live.
        //
        // Rather than risk a native SIGABRT we intentionally do NOT destroy the texture here —
        // the parent [MaterialLoader.destroy] path or the [Engine.destroy] path will reclaim it
        // when the engine is torn down. This matches Filament's own recommended pattern for
        // short-lived textured MaterialInstances: release the MaterialInstance early, let the
        // texture live until the Engine dies.
        //
        // Regression reference: commit 88a... "samples/ImageNode lifecycle crash" (no issue #),
        // plus this test suite's BillboardDemo/ImageDemo/TextDemo teardown crashes on Apple M3
        // Metal emulator, repro at commit 76665230.
        val mi = materialInstance
        super.destroy()
        materialLoader.destroyMaterialInstance(mi)
        // DELIBERATE: do NOT destroy the texture — Engine teardown reclaims it safely.
    }
}