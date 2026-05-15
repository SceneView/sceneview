package io.github.sceneview.material

import com.google.android.filament.MaterialInstance
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Mat3
import io.github.sceneview.math.Color
import io.github.sceneview.texture.TextureSampler2D

//////////////
// UberShader
//////////////

// Type-safe setters for the parameters exposed by Filament's gltfio "ubershader" material —
// the material used to render loaded glTF/GLB models. Each function forwards to
// `setParameter(name, value)` with the correct parameter name and type. All must be called
// on the main thread.

/** Sets the `specularFactor` parameter (specular-glossiness workflow). */
fun MaterialInstance.setSpecularFactor(value: Float3) = setParameter("specularFactor", value)

/** Sets the `glossinessFactor` parameter (specular-glossiness workflow), in `0..1`. */
fun MaterialInstance.setGlossinessFactor(value: Float) = setParameter("glossinessFactor", value)

// Base Color

/** Sets the `baseColorIndex` parameter — the UV set index used to sample the base color map. */
fun MaterialInstance.setBaseColorIndex(value: Int) = setParameter("baseColorIndex", value)

/** Sets the `baseColorFactor` parameter — the RGBA tint multiplied with the base color map. */
fun MaterialInstance.setBaseColorFactor(value: Color) = setParameter("baseColorFactor", value)

/** Sets the `baseColorMap` texture, sampled with [sampler] (defaults to a repeating 2D sampler). */
fun MaterialInstance.setBaseColorMap(
    texture: Texture,
    sampler: TextureSampler = TextureSampler2D()
) = setParameter("baseColorMap", texture, sampler)

/** Sets the `baseColorUvMatrix` parameter — the 3×3 UV transform applied to the base color map. */
fun MaterialInstance.setBaseColorUvMatrix(value: Mat3) = setParameter("baseColorUvMatrix", value)

// Metallic-Roughness Map

/** Sets the `metallicRoughnessIndex` parameter — the UV set index for the metallic-roughness map. */
fun MaterialInstance.setMetallicRoughnessIndex(value: Int) =
    setParameter("metallicRoughnessIndex", value)

/** Sets the `metallicFactor` parameter, in `0..1` (`0` = dielectric, `1` = metal). */
fun MaterialInstance.setMetallicFactor(value: Float) = setParameter("metallicFactor", value)

/** Sets the `roughnessFactor` parameter, in `0..1` (`0` = mirror-smooth, `1` = fully rough). */
fun MaterialInstance.setRoughnessFactor(value: Float) = setParameter("roughnessFactor", value)

/** Sets the `metallicRoughnessMap` texture, sampled with [sampler]. */
fun MaterialInstance.setMetallicRoughnessMap(
    texture: Texture,
    sampler: TextureSampler = TextureSampler2D()
) = setParameter("metallicRoughnessMap", texture, sampler)

/** Sets the `metallicRoughnessUvMatrix` parameter — the 3×3 UV transform for the metallic-roughness map. */
fun MaterialInstance.setMetallicRoughnessUvMatrix(value: Mat3) =
    setParameter("metallicRoughnessUvMatrix", value)

// Normal Map

/** Sets the `normalIndex` parameter — the UV set index for the normal map. */
fun MaterialInstance.setNormalIndex(value: Int) = setParameter("normalIndex", value)

/** Sets the `normalScale` parameter — the strength multiplier applied to the normal map. */
fun MaterialInstance.setNormalScale(value: Float) = setParameter("normalScale", value)

/** Sets the `normalMap` texture, sampled with [sampler]. */
fun MaterialInstance.setNormalMap(texture: Texture, sampler: TextureSampler = TextureSampler2D()) =
    setParameter("normalMap", texture, sampler)

/** Sets the `normalUvMatrix` parameter — the 3×3 UV transform for the normal map. */
fun MaterialInstance.setNormalUvMatrix(value: Mat3) = setParameter("normalUvMatrix", value)

// Ambient Occlusion

/** Sets the `aoIndex` parameter — the UV set index for the ambient occlusion map. */
fun MaterialInstance.setAoIndex(value: Int) = setParameter("aoIndex", value)

/** Sets the `aoStrength` parameter — the ambient occlusion intensity, in `0..1`. */
fun MaterialInstance.setAoStrength(value: Float) = setParameter("aoStrength", value)

/** Sets the `occlusionMap` texture, sampled with [sampler]. */
fun MaterialInstance.setOcclusionMap(
    texture: Texture,
    sampler: TextureSampler = TextureSampler2D()
) = setParameter("occlusionMap", texture, sampler)

/** Sets the `occlusionUvMatrix` parameter — the 3×3 UV transform for the occlusion map. */
fun MaterialInstance.setOcclusionUvMatrix(value: Mat3) = setParameter("occlusionUvMatrix", value)

// Emissive Map

/** Sets the `emissiveIndex` parameter — the UV set index for the emissive map. */
fun MaterialInstance.setEmissiveIndex(value: Int) = setParameter("emissiveIndex", value)

/** Sets the `emissiveFactor` parameter — the RGB emissive color multiplied with the emissive map. */
fun MaterialInstance.setEmissiveFactor(value: Color) = setParameter("emissiveFactor", value)

/** Sets the `emissiveStrength` parameter — a multiplier scaling the emissive output (KHR_materials_emissive_strength). */
fun MaterialInstance.setEmissiveStrength(value: Float) = setParameter("emissiveStrength", value)

/** Sets the `emissiveMap` texture, sampled with [sampler]. */
fun MaterialInstance.setEmissiveMap(
    texture: Texture,
    sampler: TextureSampler = TextureSampler2D()
) = setParameter("emissiveMap", texture, sampler)

/** Sets the `emissiveUvMatrix` parameter — the 3×3 UV transform for the emissive map. */
fun MaterialInstance.setemissiveUvMatrix(value: Mat3) = setParameter("emissiveUvMatrix", value)


// Clear coat

/** Sets the `clearCoatFactor` parameter — the clear-coat layer strength, in `0..1`. */
fun MaterialInstance.setClearCoatFactor(value: Float) = setParameter("clearCoatFactor", value)

/** Sets the `clearCoatRoughnessFactor` parameter — the clear-coat layer roughness, in `0..1`. */
fun MaterialInstance.setClearCoatRoughnessFactor(value: Float) =
    setParameter("clearCoatRoughnessFactor", value)

/** Sets the `clearCoatIndex` parameter — the UV set index for the clear-coat map. */
fun MaterialInstance.setClearCoatIndex(value: Int) = setParameter("clearCoatIndex", value)

/** Sets the `clearCoatMap` texture, sampled with [sampler]. */
fun MaterialInstance.setClearCoatMap(
    texture: Texture,
    sampler: TextureSampler = TextureSampler2D()
) = setParameter("clearCoatMap", texture, sampler)

/** Sets the `clearCoatUvMatrix` parameter — the 3×3 UV transform for the clear-coat map. */
fun MaterialInstance.setClearCoatUvMatrix(value: Mat3) = setParameter("clearCoatUvMatrix", value)

/** Sets the `clearCoatRoughnessIndex` parameter — the UV set index for the clear-coat roughness map. */
fun MaterialInstance.setClearCoatRoughnessIndex(value: Int) =
    setParameter("clearCoatRoughnessIndex", value)

/** Sets the `clearCoatRoughnessMap` texture, sampled with [sampler]. */
fun MaterialInstance.setClearCoatRoughnessMap(
    texture: Texture,
    sampler: TextureSampler = TextureSampler2D()
) = setParameter("clearCoatRoughnessMap", texture, sampler)

/** Sets the `clearCoatRoughnessUvMatrix` parameter — the 3×3 UV transform for the clear-coat roughness map. */
fun MaterialInstance.setClearCoatRoughnessUvMatrix(value: Mat3) =
    setParameter("clearCoatRoughnessUvMatrix", value)

/** Sets the `clearCoatNormalIndex` parameter — the UV set index for the clear-coat normal map. */
fun MaterialInstance.setClearCoatNormalIndex(value: Int) =
    setParameter("clearCoatNormalIndex", value)

/** Sets the `clearCoatNormalMap` texture, sampled with [sampler]. */
fun MaterialInstance.setClearCoatNormalMap(
    texture: Texture,
    sampler: TextureSampler = TextureSampler2D()
) = setParameter("clearCoatNormalMap", texture, sampler)

/** Sets the `clearCoatNormalUvMatrix` parameter — the 3×3 UV transform for the clear-coat normal map. */
fun MaterialInstance.setClearCoatNormalUvMatrix(value: Mat3) =
    setParameter("clearCoatNormalUvMatrix", value)

/** Sets the `clearCoatNormalScale` parameter — the strength multiplier for the clear-coat normal map. */
fun MaterialInstance.setClearCoatNormalScale(value: Float) =
    setParameter("clearCoatNormalScale", value)

// Reflectance

//fun MaterialInstance.setReflectance(value: Float) = setParameter("reflectance", value)
