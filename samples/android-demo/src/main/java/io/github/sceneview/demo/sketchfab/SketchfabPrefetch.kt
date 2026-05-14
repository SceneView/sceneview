package io.github.sceneview.demo.sketchfab

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Cache-warming utilities for [SampleAssets] categories.
 *
 * Stage 1 deliverable for [#1152](https://github.com/sceneview/sceneview/issues/1152) —
 * a demo can prefetch the whole category it will display when first opened so
 * the next call to [SketchfabAssetResolver.resolve] returns a cached file
 * instantly without showing a spinner.
 *
 * **Behavior.**
 *  - Launches one [SketchfabAssetResolver.resolve] coroutine per slug.
 *  - Concurrency is capped at [MAX_PARALLEL_DOWNLOADS] so we don't fan out
 *    to all of [SampleAssets.all] on a metered connection.
 *  - Individual failures are swallowed (per-slug `runCatching`) so one bad
 *    UID — deleted by its author, rate-limited, etc. — never kills the warmup
 *    for the whole category.
 *
 * **Mirror.** Same contract as `SketchfabPrefetch.swift` (`TaskGroup` capped at
 * 4). Keep both in sync.
 */
object SketchfabPrefetch {

    /** Hard ceiling on simultaneous Sketchfab downloads during a warm-up. */
    const val MAX_PARALLEL_DOWNLOADS: Int = 4

    private const val TAG = "SketchfabPrefetch"

    private val downloadDispatcher by lazy {
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        Dispatchers.IO.limitedParallelism(MAX_PARALLEL_DOWNLOADS)
    }

    /**
     * Prefetch every [slug][SketchfabSlug] in [slugs] in parallel.
     *
     * Suspends until every download has either completed or failed. Returns
     * the count of slugs that ended up with a real cache hit (or bundled
     * fallback file) — never throws.
     */
    suspend fun prefetchAll(
        slugs: List<SketchfabSlug>,
        context: Context,
        resolver: SketchfabAssetResolver = SketchfabAssetResolver(context),
    ): Int {
        if (slugs.isEmpty()) return 0
        coroutineContext.ensureActive()
        // Use async + awaitAll on the IO-limited dispatcher so concurrency is
        // bounded but failures don't poison the whole batch.
        return kotlinx.coroutines.coroutineScope {
            slugs.map { slug ->
                async(downloadDispatcher) {
                    runCatching { resolver.resolve(slug) }
                        .onFailure { t ->
                            Log.w(TAG, "Prefetch failed for ${slug.uid}: ${t.message}")
                        }
                        .isSuccess
                }
            }.awaitAll().count { it }
        }
    }

    /**
     * Fire-and-forget variant for callers that don't want to await. Returns a
     * [Job] so the launch site can cancel the warm-up (e.g. on demo dispose).
     */
    fun prefetchAllAsync(
        slugs: List<SketchfabSlug>,
        context: Context,
        scope: CoroutineScope,
        resolver: SketchfabAssetResolver = SketchfabAssetResolver(context),
    ): Job = scope.launch {
        prefetchAll(slugs, context, resolver)
    }
}
