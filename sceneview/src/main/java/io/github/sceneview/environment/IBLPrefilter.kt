package io.github.sceneview.environment

import com.google.android.filament.Engine
import com.google.android.filament.Texture
import com.google.android.filament.utils.IBLPrefilterContext

/**
 * IBLPrefilter creates and initializes GPU state common to all environment map filters.
 * Typically, only one instance per filament Engine of this object needs to exist.
 *
 * @see [IBLPrefilterContext]
 */
class IBLPrefilter(engine: Engine) {

    /**
     * Created IBLPrefilterContext, keeping it around if several cubemap will be processed.
     */
    val context by lazy { IBLPrefilterContext(engine) }

    /**
     * EquirectangularToCubemap is use to convert an equirectangluar image to a cubemap.
     *
     * Creates a EquirectangularToCubemap processor.
     */
    private val equirectangularToCubemap by lazy {
        IBLPrefilterContext.EquirectangularToCubemap(
            context
        )
    }

    /**
     * Converts an equirectangular image to a cubemap.
     *
     * @param equirect Texture to convert to a cubemap.
     * - Can't be null.
     * - Must be a 2d texture
     * - Must have equirectangular geometry, that is width == 2*height.
     * - Must be allocated with all mip levels.
     * - Must be SAMPLEABLE
     *
     * @return the cubemap texture
     *
     * @see [EquirectangularToCubemap]
     */
    fun equirectangularToCubemap(equirect: Texture): Texture =
        equirectangularToCubemap.run(equirect)

    /**
     * Created specular (reflections) filter. This operation generates the kernel, so it's
     * important to keep it around if it will be reused for several cubemaps.
     * An instance of SpecularFilter is needed per filter configuration. A filter configuration
     * contains the filter's kernel and sample count.
     */
    private val specularFilter by lazy { IBLPrefilterContext.SpecularFilter(context) }

    /**
     * Generates a prefiltered cubemap.
     *
     * SpecularFilter is a GPU based implementation of the specular probe pre-integration filter.
     *
     * **Cost scales with cubemap face count + resolution**:
     * - **First build of a 1024×1024×6 HDR skybox**: ~100–200 ms on the GPU (initial
     *   mip-chain construction at full resolution — what this KDoc historically referenced).
     * - **Incremental update of a 16×16×6 ARCore cubemap**: ~5–15 ms on a Pixel 9
     *   (used per [io.github.sceneview.ar.light.LightEstimator.environmentalHdrSpecularFilter]
     *   each time `acquireEnvironmentalHdrCubeMap()` ticks, ~once per second).
     *
     * Reuse the [IBLPrefilter] instance across calls — the GPU kernel + sample count
     * are cached in [specularFilter], so subsequent invocations only pay the per-cubemap
     * filter pass and not the kernel-init cost.
     *
     * @param skybox Environment cubemap.
     * This cubemap is SAMPLED and have all its levels allocated.
     *
     * @return the reflections texture
     */
    fun specularFilter(skybox: Texture) = specularFilter.run(skybox)

    fun destroy() {
        runCatching { specularFilter.destroy() }
        runCatching { equirectangularToCubemap.destroy() }
        runCatching { context.destroy() }
    }
}