package io.github.sceneview.ar.light

import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.Texture
import com.google.ar.core.Config.LightEstimationMode
import com.google.ar.core.Frame
import com.google.ar.core.LightEstimate
import com.google.ar.core.Session
import dev.romainguy.kotlin.math.max
import io.github.sceneview.environment.IBLPrefilter
import io.github.sceneview.math.Color
import io.github.sceneview.math.Direction
import io.github.sceneview.math.toLinearSpace
import io.github.sceneview.utils.exposureFactor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Per frame AR light estimation
 *
 * ARCore will estimate lighting to provide directional light, ambient spherical harmonics,
 * and reflection cubemap estimation
 *
 * A key part for creating realistic AR experiences is getting the lighting right. When a virtual
 * object is missing a shadow or has a shiny material that doesn't reflect the surrounding space,
 * users can sense that the object doesn't quite fit, even if they can't explain why.
 * This is because humans subconsciously perceive cues regarding how objects are lit in their
 * environment. The Lighting Estimation API analyzes given images for such cues, providing detailed
 * information about the lighting in a scene. You can then use this information when rendering
 * virtual objects to light them under the same conditions as the scene they're placed in,
 * keeping users grounded and engaged.
 *
 * ## Thread safety
 *
 * [update] is invoked from the AR render path each frame; [destroy] from the Compose
 * main thread via [androidx.compose.runtime.DisposableEffect]'s `onDispose`. The
 * Filament `PixelBufferDescriptor` upload callback fires on Filament's render
 * thread. Cross-thread state transitions are gated by two `@Volatile` flags
 * ([isDestroyed], [uploadInFlight]) — see the field docs. The public
 * `environmentalHdr*` toggles are also `@Volatile` and safe to flip from any
 * thread. See CORR-B audit (acceptance #2 of umbrella #1094) for the
 * destroy-race / texture-leak / buffer-race specifics.
 */
class LightEstimator(
    val engine: Engine,
    val iblPrefilter: IBLPrefilter
) {

    data class Estimation(
        var mainLightColor: Color? = null,
        var mainLightIntensity: Float? = null,
        var mainLightDirection: Direction? = null,
        var reflections: Texture? = null,
        var irradiance: FloatArray? = null
    )

    @Volatile
    var isEnabled = true

    /**
     * Enable reflection cubemap
     *
     * - true if the AR Core reflection cubemap should be used
     * - false for using the default/static/fake environment reflections
     *
     * Use the HDR cubemap to render realistic reflections on virtual objects with medium to high
     * glossiness, such as shiny metallic surfaces. The cubemap also affects the shading and
     * appearance of objects. For example, the material of a specular object surrounded by a blue
     * environment will reflect blue hues. Calculating the HDR cubemap requires a small amount of
     * additional CPU computation.
     */
    @Volatile
    var environmentalHdrReflections = true

    /**
     * Ambient spherical harmonics
     *
     * In addition to the light energy in the main directional light, ARCore provides spherical
     * harmonics, representing the overall ambient light coming in from all directions in the scene.
     * Add subtle cues that bring out the definition of virtual objects.
     */
    @Volatile
    var environmentalHdrSphericalHarmonics = true

    /**
     * Build a roughness-prefiltered cubemap before handing it to Filament's
     * `IndirectLight.reflections()`.
     *
     * **Default `true`** (#1064). Filament's `IndirectLight.reflections()` expects a
     * prefiltered roughness mip chain — feeding a raw cubemap means every roughness
     * lookup samples mip 0, so reflections are mirror-like at any roughness slider
     * value, highlights oversaturate, and metallic/glossy materials look wrong.
     *
     * Cost: a one-time prefilter pass per cubemap update (~5–15 ms on a Pixel 9).
     * ARCore updates the HDR cubemap roughly once per second, so the cost is
     * amortised.
     *
     * Set `false` only on perf-budget-constrained devices where the materials are
     * known not to use roughness > 0 (e.g. unlit-only scenes).
     */
    @Volatile
    var environmentalHdrSpecularFilter = true

    /**
     * Move the directional light
     *
     * When the main light source or a lit object is in motion, the specular highlight on the
     * object adjusts its position in real time relative to the light source.
     *
     * Directional shadows also adjust their length and direction relative to the position of the
     * main light source, just as they do in the real world.
     */
    @Volatile
    var environmentalHdrMainLightDirection = true

    /**
     * Modulate the main directional light (sun) intensity
     */
    @Volatile
    var environmentalHdrMainLightIntensity = true

    private var timestamp: Long? = null
    private var cubeMapBuffer: ByteBuffer? = null
    private var cubeMapTexture: Texture? = null
        set(value) {
            runCatching { field?.let { engine.destroyTexture(it) } }
            field = value
        }
    private var cubeMapTextureSpecular: Texture? = null
        set(value) {
            runCatching { field?.let { engine.destroyTexture(it) } }
            field = value
        }

    /**
     * Set to `true` by [destroy]; checked at the top of [update] so the render
     * loop early-returns instead of touching native resources after teardown.
     *
     * Why `@Volatile` (CORR-B audit, acceptance #2 of umbrella #1094): [destroy] is invoked from
     * `DisposableEffect.onDispose` (Compose main thread, [ARScene.kt:373]),
     * while a stray late frame could still drive [update] (Filament render
     * thread, same engine but different lifecycle). Marking the flag volatile
     * gives the render thread a happens-before guarantee on the teardown
     * write without taking a lock on the hot path. Reads are a single
     * `getfield_volatile`, the cheapest synchronisation primitive that
     * works here.
     *
     * Also gated: the public toggles (`environmentalHdrXxx`) are already
     * `@Volatile`, so toggling after `destroy()` is a no-op via the same
     * early-return.
     */
    @Volatile
    private var isDestroyed = false

    /**
     * Acts as a 1-bit semaphore between the AR thread (which fills
     * [cubeMapBuffer] frame N and hands it to Filament) and the Filament
     * render thread (which drains the buffer to the GPU asynchronously).
     *
     * Set to `true` immediately before [Texture.setImage]; reset to `false`
     * by the [Texture.PixelBufferDescriptor] callback, which Filament fires
     * on its render thread once the upload has completed. While true, the
     * AR thread skips the cubemap update — see the long-form comment on
     * the callback site for the rationale.
     *
     * @Volatile is mandatory: the writer (AR thread) and reader (AR thread
     * again, on the next frame) ARE on the same thread today, but the
     * resetter (Filament render thread) is NOT — without volatile, the AR
     * thread could observe a stale `true` indefinitely.
     */
    @Volatile
    private var uploadInFlight = false

    fun update(session: Session, frame: Frame, camera: Camera): Estimation? {
        // Fix 1 (CORR-B, #1094 acceptance #2): a late render frame raced with `destroy()` from
        // `DisposableEffect.onDispose` could NPE on `engine.destroyTexture`
        // (engine torn down) or use-after-free a freed cubemap. Early return
        // is the smallest fix that survives the race.
        if (isDestroyed) return null

        val mode = session.config.lightEstimationMode
        val enabled = isEnabled && mode != LightEstimationMode.DISABLED

        // Fix 2 (CORR-B, #1094 acceptance #2): free reflection resources as soon as
        // their feature flag flips false, so toggles don't leak.
        //
        // Before this guard, toggling `environmentalHdrReflections false →
        // true → false` left `cubeMapTextureSpecular` (and the parent
        // `cubeMapTexture`) alive in native heap forever — neither was
        // reassigned on the second `false` transition, so the setter's
        // built-in destroy-on-reassign never fired.
        //
        // The setters are null-safe (`field?.let { destroy }`) and idempotent,
        // so repeated null-assignments after teardown cost nothing. Free the
        // direct ByteBuffer too — DirectByteBuffer relies on a cleaner that
        // may not run for a long time under low GC pressure.
        if (!enabled || mode != LightEstimationMode.ENVIRONMENTAL_HDR ||
            !environmentalHdrReflections
        ) {
            cubeMapTexture = null
            cubeMapTextureSpecular = null
            // Drop the staging buffer too once reflections are off; a future
            // re-enable will allocate a fresh one inside the HDR branch.
            cubeMapBuffer = null
        } else if (!environmentalHdrSpecularFilter) {
            // Reflections on but prefilter off: only the specular texture
            // is dead weight.
            cubeMapTextureSpecular = null
        }

        if (!enabled) {
            return null
        }

        val lightEstimate = frame.lightEstimate.takeIf {
            it.state == LightEstimate.State.VALID && it.timestamp != timestamp
        } ?: return null

        timestamp = lightEstimate.timestamp

        return when (mode) {
            LightEstimationMode.AMBIENT_INTENSITY -> Estimation().apply {
                val colorCorrections = FloatArray(4).apply {
                    // The float array the 4 component color correction values are written to.
                    // The four values are:
                    // - `colorCorrections[0]`: Color correction value for the red channel. This
                    // value is larger or equal to zero.
                    // - `colorCorrections[1]`: Color correction value for the green channel. This
                    // value is always 1.0 as the green channel is the reference baseline.
                    // - `colorCorrections[2]`: Color correction value for the blue channel. This
                    // value is larger or equal to zero.
                    // `colorCorrections[3]`: This value is identical to the average pixel intensity
                    // from [LightEstimate.getPixelIntensity] in the range `[0.0, 1.0]`.
                    // A value of a white colorCorrection (r=1.0, g=1.0, b=1.0) and pixelIntensity
                    // of 1.0 mean that no changes are made to the light settings.
                    // The color correction method uses the green channel as reference baseline and
                    // scales the red and blue channels accordingly. In this way the overall
                    // intensity will not be significantly changed
                    lightEstimate.getColorCorrection(this, 0)
                }

                val colorIntensitiesFactors = colorCorrections
                    .slice(0..2).let { (r, g, b) -> Color(r, g, b) }
                val maxIntensity = max(colorIntensitiesFactors)
                // Normalize color to fit into [0..1]
                // Rendering in linear space
                mainLightColor = (colorIntensitiesFactors / maxIntensity).toLinearSpace()

                // Normalize the pixel intensity by multiplying it by 1.8
                mainLightIntensity = colorCorrections[3] * 1.8f
            }

            LightEstimationMode.ENVIRONMENTAL_HDR -> Estimation().apply {
                // Returns the intensity of the main directional light based on the inferred
                // Environmental HDR Lighting Estimation. All return values are larger or equal to
                // zero.
                // The color correction method uses the green channel as reference baseline and
                // scales the red and blue channels accordingly. In this way the overall intensity
                // will not be significantly changed
                if (environmentalHdrMainLightIntensity) {
                    val colorIntensitiesFactors = lightEstimate.environmentalHdrMainLightIntensity
                        .let { (r, g, b) -> Color(r, g, b) }
                    val maxIntensity = max(colorIntensitiesFactors)
                    // ARCore's light estimation uses unit-less (relative) values while Filament
                    // uses a physically based camera model with lux or lumen values.
                    // In order to keep the "standard" Filament behavior we scale ARCore values.
                    // More info: [https://github.com/ThomasGorisse/SceneformMaintained/pull/156#issuecomment-911873565]
                    val exposureFactor = camera.exposureFactor
                    // Apply the camera exposure factor
                    mainLightColor = colorIntensitiesFactors * exposureFactor
                    // Average intensity
                    mainLightIntensity = mainLightColor?.toFloatArray()?.average()?.toFloat()
                }

                if (environmentalHdrMainLightDirection) {
                    lightEstimate.environmentalHdrMainLightDirection.let { (x, y, z) ->
                        mainLightDirection = Direction(-x, -y, -z)
                    }
                }

                // Fix 3 (CORR-B, #1094 acceptance #2): skip the whole cubemap pipeline while the
                // previous upload is still in flight. Without this guard, the
                // next-frame `cubeMapBuffer.clear() + put(rgbBytes)` path
                // (see below) could partially overwrite a ByteBuffer that
                // Filament's render thread is still draining to the GPU,
                // producing a smeared cubemap or a single-frame flash of
                // garbage HDR colors. ARCore updates the HDR cubemap at
                // roughly 1 Hz; skipping 1–2 frames here is invisible. We
                // also bail BEFORE `acquireEnvironmentalHdrCubeMap()` so we
                // don't pay the acquire cost and don't leak Images we can't
                // process.
                if (environmentalHdrReflections && !uploadInFlight) {
                    lightEstimate.acquireEnvironmentalHdrCubeMap()?.let { arImages ->
                        // CORR-B (#1094 acceptance #2) wraps the acquire→process block in a
                        // single try/finally so every ARCore [Image] is released exactly
                        // once even if `arImages[0].width` throws, ByteBuffer.allocateDirect
                        // OOMs, or one of the iterations throws mid-loop. Pre-fix path used
                        // per-image `image.use {}` which only covered images we managed to
                        // reach — a partial iteration leaked the remaining 1–5 images.
                        //
                        // `runCatching { it.close() }` defends against ARCore's
                        // [Image.close] not being strictly idempotent on every device
                        // (it can throw IllegalStateException on a double-close). Today
                        // the loop body does NOT close images, so single-close in the
                        // finally is the expected path; the runCatching is belt-and-
                        // suspenders for forward compatibility with future loop edits.
                        try {
                            val (width, height) = arImages[0].width to arImages[0].height
                            val faceOffsets = IntArray(arImages.size)
                            // RGB Bytes per pixel : 6 * 2
                            val bufferSize =
                                width * height * arImages.size * 6 * 2
                            val buffer = cubeMapBuffer?.takeIf {
                                it.capacity() == bufferSize
                            }?.apply {
                                clear()
                            } ?: ByteBuffer.allocateDirect(bufferSize).apply {
                                // Use the device hardware's native byte order
                                order(ByteOrder.nativeOrder())
                                cubeMapBuffer = this
                            }
                            val rgbBytes = ByteArray(6) // RGB Bytes per pixel
                            arImages.forEachIndexed { index, image ->
                                faceOffsets[index] = buffer.position()
                                val imageBuffer = image.planes[0].buffer
                                while (imageBuffer.hasRemaining()) {
                                    // Only take the RGB channels
                                    imageBuffer.get(rgbBytes)
                                    buffer.put(rgbBytes)
                                    // Skip the Alpha channel
                                    imageBuffer.position(imageBuffer.position() + 2)
                                }
                                imageBuffer.clear()
                            }
                            buffer.flip()

                            // Reuse the previous texture instead of creating a new one for
                            // performance and memory reasons
                            val cubeMapTexture = cubeMapTexture?.takeIf {
                                it.getWidth(0) == width &&
                                        it.getHeight(0) == height
                            } ?: Texture.Builder()
                                .width(width)
                                .height(height)
                                .levels(0xff)
                                .sampler(Texture.Sampler.SAMPLER_CUBEMAP)
                                .format(Texture.InternalFormat.R11F_G11F_B10F)
                                // GEN_MIPMAPPABLE is REQUIRED — `iblPrefilter.specularFilter()`
                                // below internally calls `Texture.generateMipmaps()`, which
                                // Filament 1.70+ asserts on if the texture wasn't built with
                                // this usage flag. Pre-PR #1086 the specular prefilter was off
                                // by default so the missing flag was latent; flipping the
                                // default to `true` then made every AR demo SIGABRT on the
                                // first cubemap update (caught by CORR-D visual QA on
                                // Pixel 9). Cross-reference: ImageTexture.kt:23-26 already
                                // applies the same flag for the same reason.
                                .usage(Texture.Usage.DEFAULT or Texture.Usage.GEN_MIPMAPPABLE)
                                .build(engine)
                                .also {
                                    cubeMapTexture = it
                                }
                            // Latch the in-flight flag BEFORE handing the buffer
                            // to Filament — the callback below will reset it once
                            // the GPU upload has consumed the bytes. The set →
                            // setImage → callback sequence MUST be in this order
                            // so an extra-fast Filament backend cannot fire the
                            // callback before we've observed the latched `true`.
                            uploadInFlight = true
                            @Suppress("DEPRECATION")
                            cubeMapTexture.setImage(
                                engine,
                                0,
                                // The callback IS load-bearing — DO NOT no-op it.
                                //
                                // Filament `setImage()` uploads the buffer
                                // asynchronously: it returns immediately, then the
                                // render thread reads from `buffer` on a later
                                // tick. Until the GPU has finished reading, the
                                // AR thread MUST NOT clear/overwrite the buffer
                                // or we corrupt the in-flight upload (smeared
                                // cubemap, 1-frame flash of garbage HDR colors).
                                //
                                // `uploadInFlight = true` is latched immediately
                                // above. Filament invokes this Runnable from its
                                // render thread once the upload is done; the
                                // `@Volatile` write publishes the reset to the
                                // AR thread, which gates the next cubemap update
                                // via `if (!uploadInFlight)`.
                                //
                                // History — DO NOT no-op this again:
                                //   #1090 / PR #1091 made this callback a no-op to
                                //   remove a bug where the previous closure
                                //   double-closed `arImages` and re-`clear()`ed
                                //   the buffer. That fix was correct for the
                                //   resource-cleanup path, BUT the *synchronisation
                                //   semantics* of the callback were collateral
                                //   damage — there was no longer any signal back
                                //   from Filament telling the AR thread when the
                                //   buffer was safe to mutate.
                                //
                                //   CORR-B (#1094 acceptance #2) restores the callback as a
                                //   pure synchronisation hook. The body does NOT
                                //   touch `arImages` (they are closed exactly once
                                //   by the surrounding `try { } finally { ... }`
                                //   block) and does NOT clear `buffer` (that happens
                                //   at the top of the next frame inside
                                //   `cubeMapBuffer?.takeIf {...}?.apply { clear() }`).
                                //   Only the volatile flag flips.
                                //
                                // If a future refactor is tempted to remove this
                                // callback again: ADD an instrumented test
                                // before doing so, and verify the cubemap stays
                                // pixel-stable across 60 frames of rapid
                                // `acquireEnvironmentalHdrCubeMap` cycles.
                                Texture.PixelBufferDescriptor(
                                    buffer,
                                    Texture.Format.RGB,
                                    Texture.Type.HALF,
                                    1, 0, 0, 0, null,
                                    Runnable { uploadInFlight = false }
                                ),
                                faceOffsets
                            )
                            reflections = if (environmentalHdrSpecularFilter) {
                                iblPrefilter.specularFilter(cubeMapTexture).also {
                                    cubeMapTextureSpecular = it
                                }
                            } else {
                                cubeMapTexture
                            }
                        } finally {
                            // Single-pass close of every acquired ARCore Image.
                            // See try{} comment above for the why; runCatching
                            // defends against device-specific non-idempotent
                            // close() implementations.
                            arImages.forEach { runCatching { it.close() } }
                        }
                    }
                }
                if (environmentalHdrSphericalHarmonics) {
                    irradiance = lightEstimate.environmentalHdrAmbientSphericalHarmonics
                        .mapIndexed { index, sphericalHarmonic ->
                            // Convert Environmental HDR's spherical harmonics to Filament
                            // irradiance spherical harmonics.
                            sphericalHarmonic * SPHERICAL_HARMONICS_IRRADIANCE_FACTORS[index / 3]
                        }.toFloatArray()
                }
            }

            else -> null
        }
    }

    /**
     * Releases all Filament-owned resources and flips the [isDestroyed] gate
     * so any in-flight [update] call from the render thread early-returns
     * instead of touching freed natives.
     *
     * Safe to call multiple times — the [Texture] setters are null-safe
     * (`field?.let { destroy }`) and idempotent; the volatile flag write
     * provides the happens-before to a concurrent reader.
     *
     * Idempotency is required by [ARScene.kt:373]'s `DisposableEffect`
     * semantics: the lambda can re-run if the keys change before the
     * previous teardown finishes.
     */
    fun destroy() {
        // Latch the gate BEFORE freeing the textures so a render-thread
        // late frame observes the destroyed state and skips its use of
        // `cubeMapTexture` / `cubeMapTextureSpecular` instead of racing
        // with `engine.destroyTexture` below.
        isDestroyed = true
        // Unstick the buffer-race gate so a dropped Filament callback during
        // engine teardown doesn't leave the flag latched. After destroy(),
        // `isDestroyed` is the dominant gate anyway (L169 early-return takes
        // precedence) — this is belt-and-suspenders for the case where a
        // future caller resurrects the estimator or copies the pattern into
        // a sibling.
        uploadInFlight = false
        // Routed through the setters so the `field?.let { engine.destroyTexture }`
        // teardown runs and `field` is nulled afterwards — keeping the two in
        // sync and tolerating repeated `destroy()` calls.
        //
        // Filament defers actual GPU teardown until the command-buffer drains,
        // so calling `engine.destroyTexture(t)` while `t` is still being
        // uploaded by an in-flight `setImage()` is documented as safe (the
        // resource is reference-counted by the pending command and freed
        // when both refs drop). The setter's `runCatching` covers the
        // theoretical case where the engine itself was destroyed first
        // (Compose dispose order is LIFO so this is rare in practice, but
        // documented here so future maintainers don't tighten the wrapper).
        cubeMapTexture = null
        cubeMapTextureSpecular = null
        // Filament's PixelBufferDescriptor holds a strong ref to the buffer
        // until the upload callback fires; nulling our `cubeMapBuffer` field
        // only drops OUR reference. The DirectByteBuffer's Cleaner won't run
        // until Filament also releases its ref, so an in-flight upload is
        // not impacted by this null.
        cubeMapBuffer = null
    }

    companion object {

        /**
         * Filament SH normalization factors for the 9 first-order spherical harmonic
         * bands, in the order Filament's `IndirectLight.Builder.irradiance(3, factors)`
         * expects (matches `filament/libs/ibl/src/CubemapSH.cpp` band layout):
         *
         *     index | band | symbol | K
         *     ------|------|--------|----
         *     0     | 0    | y00    |  0.282095   = sqrt(1/(4π))
         *     1     | 1    | y1m1   | -0.325735   = sqrt(3/(4π)) signed
         *     2     | 1    | y10    |  0.325735
         *     3     | 1    | y11    | -0.325735
         *     4     | 2    | y2m2   |  0.273137
         *     5     | 2    | y2m1   | -0.273137
         *     6     | 2    | y20    |  0.078848
         *     7     | 2    | y21    | -0.273137
         *     8     | 2    | y22    |  0.136569
         *
         * Per the ARCore docs the 27 floats from
         * `getEnvironmentalHdrAmbientSphericalHarmonics()` are returned in the SAME
         * order: 9 coefficients × 3 RGB channels in y00, y1m1, y10, y11, y2m2,
         * y2m1, y20, y21, y22 sequence. The factor[i] slot at index `i/3` therefore
         * scales the matching ARCore band directly.
         *
         * The pre-#1093 code applied a `mapIndexed { 6 -> [7]; 7 -> [6] }` swap
         * after constructing this array. That swap was based on a v1.x assumption
         * (Sceneform-era ThomasGorisse/SceneformMaintained PR #156) that the two
         * APIs used different basis orderings. After cross-checking Filament's
         * CubemapSH.cpp + ARCore's SH ordering docs, both use the SAME band layout.
         * The swap inverted y20 (`0.078848`) and y21 (`-0.273137`) factors → matte
         * surfaces in AR were lit with wrong direction-dependent shifts (y20 is
         * the up-down axis, y21 the up-front cross term). Removed in #1093.
         *
         * Pinning regression test: `LightEstimatorSphericalHarmonicsTest`.
         */
        internal val SPHERICAL_HARMONICS_IRRADIANCE_FACTORS = floatArrayOf(
            0.282095f, -0.325735f, 0.325735f,
            -0.325735f, 0.273137f, -0.273137f,
            0.078848f, -0.273137f, 0.136569f
        )
    }
}