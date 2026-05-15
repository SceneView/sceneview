package io.github.sceneview.texture

import com.google.android.filament.TextureSampler

/**
 * A Filament [TextureSampler] preconfigured for standard 2D color/data textures: trilinear
 * mipmap minification, linear magnification, and `REPEAT` wrapping on both axes.
 *
 * This is the default sampler used by the ubershader material parameter helpers (base color,
 * normal, metallic-roughness, etc.).
 */
class TextureSampler2D : TextureSampler(
    MinFilter.LINEAR_MIPMAP_LINEAR,
    MagFilter.LINEAR,
    WrapMode.REPEAT
)

/**
 * A Filament [TextureSampler] preconfigured for external textures (e.g. camera feed or video
 * streams): linear min/mag filtering with no mipmaps and `CLAMP_TO_EDGE` wrapping.
 *
 * External textures cannot be mipmapped, hence the plain `LINEAR` minification filter.
 */
class TextureSamplerExternal : TextureSampler(
    MinFilter.LINEAR,
    MagFilter.LINEAR,
    WrapMode.CLAMP_TO_EDGE
)