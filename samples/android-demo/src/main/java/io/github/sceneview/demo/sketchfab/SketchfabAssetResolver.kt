package io.github.sceneview.demo.sketchfab

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Single-entry resolver that hands a sample/demo a ready-to-use `File` for a
 * [SketchfabSlug] — regardless of whether the model is cached, must be streamed,
 * or has to fall back to the bundled APK asset.
 *
 * Stage 1 deliverable for [#1152](https://github.com/sceneview/sceneview/issues/1152).
 *
 * **Behavior (matches iOS `SketchfabAssetResolver.swift` 1-for-1)**:
 *  1. **Cache hit** → touch `lastModified` (LRU bump) and return the cached file.
 *  2. **Cache miss + API key present** → [SketchfabService.downloadModel],
 *     optionally validate via [boundsValidator], evict LRU if cache exceeds the
 *     budget, return the freshly-downloaded file.
 *  3. **Cache miss + no key** → copy [SketchfabSlug.fallbackBundledPath] from
 *     `assets/` into the cache directory under a `fallback-<uid>.glb` name and
 *     return that file. The caller still gets a real `File` so the demo can
 *     render — Sketchfab is just an invisible CDN.
 *  4. **Download fails OR validation rejects** → log the failure (telemetry
 *     hook later) and fall back to the bundled path as in case 3.
 *
 * **Bounds validation.** The class doesn't depend on Filament — the
 * [boundsValidator] parameter is a function of `(file, slug) → Boolean` and
 * defaults to "accept-everything". The production callers wrap the resolver
 * with a validator backed by `ModelLoader` (which must be on the main thread,
 * see [io.github.sceneview.loaders.ModelLoader]). This lets unit tests inject
 * a fake validator and exercise the cache/fallback paths without a Filament
 * `Engine`.
 *
 * **Threading.** All work is dispatched to [Dispatchers.IO]. The resolver is
 * stateless past its constructor — multiple instances share the same on-disk
 * cache because [SketchfabService] is a process-wide singleton.
 *
 * @property context             The application context; only used for
 *                               `cacheDir` and `assets` access.
 * @property downloader          Suspending download function `(uid) -> File`.
 *                               Defaults to [SketchfabService.downloadModel]
 *                               via the process singleton. Tests substitute a
 *                               stub that writes a synthetic file into the
 *                               cache directory without hitting the network.
 * @property boundsValidator     Optional gate run after a successful download.
 *                               Return `false` to discard the file and fall
 *                               back. Default: accept-everything.
 * @property cacheBudgetBytes    LRU cap enforced after each successful
 *                               download. Defaults to 250 MB per the Stage-1
 *                               spec — lower than [SketchfabConfig.CACHE_MAX_BYTES]
 *                               so the demo APK never fills the user's cache
 *                               partition with model data.
 */
class SketchfabAssetResolver(
    private val context: Context,
    /**
     * Coroutine that fetches a model by uid and returns the cached `File`.
     *
     * Default delegates to the singleton [SketchfabService.downloadModel].
     * Tests substitute a stub that writes a synthetic file into the cache
     * directory without hitting the network.
     */
    private val downloader: suspend (String) -> File = { uid ->
        SketchfabService.getInstance(context).downloadModel(uid)
    },
    private val boundsValidator: suspend (File, SketchfabSlug) -> Boolean = ACCEPT_ALL,
    private val cacheBudgetBytes: Long = DEFAULT_CACHE_BUDGET_BYTES,
) {
    /**
     * Resolve [slug] to a local `File` suitable for `rememberModelInstance(modelLoader, file.absolutePath)`.
     *
     * Never throws. Errors are logged + the bundled fallback is returned so
     * the rendering pipeline always has something to consume.
     */
    suspend fun resolve(slug: SketchfabSlug): File = withContext(Dispatchers.IO) {
        // 1. Cache hit short-circuit. SketchfabService.downloadModel() already
        //    bumps lastModified, but we check here first to avoid even building
        //    the request when the key is absent.
        val cached = cacheFile(slug.uid)
        if (cached.exists() && cached.length() > 0L) {
            cached.setLastModified(System.currentTimeMillis())
            return@withContext cached
        }

        // 2. No key → straight to bundled fallback. No telemetry — this is
        //    the documented offline / first-launch path.
        val apiKey = SketchfabConfig.apiKey
        if (apiKey.isNullOrBlank()) {
            return@withContext extractBundledFallback(slug)
        }

        // 3. Try the network path. Any failure logs + falls back. We never let
        //    a network error bubble into the demo composable.
        val downloaded = try {
            downloader(slug.uid)
        } catch (e: SketchfabService.SketchfabError) {
            Log.w(TAG, "Sketchfab download failed for ${slug.uid} (${e.message}); using bundled fallback")
            return@withContext extractBundledFallback(slug)
        } catch (e: IOException) {
            Log.w(TAG, "IO error downloading ${slug.uid}; using bundled fallback", e)
            return@withContext extractBundledFallback(slug)
        }

        // 4. Bounds-check + animation-flag check (callers may have wired up the
        //    Filament-backed validator). If the model fails, delete the bad
        //    download so a retry doesn't re-use the corrupt blob, then fall back.
        val passes = try {
            boundsValidator(downloaded, slug)
        } catch (t: Throwable) {
            Log.w(TAG, "Bounds validator threw for ${slug.uid}; treating as reject", t)
            false
        }
        if (!passes) {
            Log.w(TAG, "Bounds validation rejected ${slug.uid}; falling back to bundled asset")
            runCatching { downloaded.delete() }
            return@withContext extractBundledFallback(slug)
        }

        // 5. Successful path: enforce the LRU budget and return.
        evictLruIfOverBudget(cacheBudgetBytes)
        downloaded
    }

    /**
     * Walk the Sketchfab cache directory, sort by `lastModified` ascending, and
     * delete oldest entries until total size ≤ [maxBytes]. Used both internally
     * after every successful download and exposed as a test/maintenance hook.
     *
     * No-op when the directory doesn't exist yet or is already under budget.
     */
    fun evictLruIfOverBudget(maxBytes: Long = cacheBudgetBytes) {
        val root = File(context.cacheDir, SketchfabConfig.CACHE_DIR_NAME)
        if (!root.exists()) return
        val files = root.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        val sorted = files.sortedBy { it.lastModified() }
        for (file in sorted) {
            if (total <= maxBytes) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────

    /**
     * Where [SketchfabService] caches downloaded GLBs. Kept identical to the
     * service's own layout (`<cacheDir>/sketchfab/<uid>.glb`) so a download done
     * by another code path is immediately visible here as a "cache hit".
     */
    internal fun cacheFile(uid: String): File =
        File(File(context.cacheDir, SketchfabConfig.CACHE_DIR_NAME), "$uid.glb")

    /**
     * Copy [SketchfabSlug.fallbackBundledPath] from `assets/` into the cache
     * directory and return the resulting file. The destination is named
     * `fallback-<uid>.glb` so it can't accidentally pose as a real Sketchfab
     * download (LRU bumps target real downloads only).
     *
     * Throws [IllegalStateException] if the bundled asset is missing — that's
     * a build-time bug, not a runtime degradation we can absorb.
     */
    @Throws(IllegalStateException::class)
    internal fun extractBundledFallback(slug: SketchfabSlug): File {
        val root = File(context.cacheDir, SketchfabConfig.CACHE_DIR_NAME).apply { mkdirs() }
        val dest = File(root, "fallback-${slug.uid}.glb")
        if (dest.exists() && dest.length() > 0L) return dest
        try {
            context.assets.open(slug.fallbackBundledPath).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: IOException) {
            throw IllegalStateException(
                "Missing bundled fallback at assets/${slug.fallbackBundledPath} for slug ${slug.uid}",
                e,
            )
        }
        return dest
    }

    companion object {
        private const val TAG = "SketchfabResolver"

        /** Stage-1 LRU cap — see Stage-1 spec on #1152. */
        const val DEFAULT_CACHE_BUDGET_BYTES: Long = 250L * 1024 * 1024

        /** Sentinel validator that accepts every download. Used in tests and as
         *  the default when no Filament validator is supplied. */
        val ACCEPT_ALL: suspend (File, SketchfabSlug) -> Boolean = { _, _ -> true }

        /**
         * Reference implementation of a bounds validator for production callers
         * that already hold a `ModelLoader`. Lives in a separate file so this
         * one stays free of Filament dependencies — see
         * `SketchfabBoundsValidator.kt` (Stage 2).
         */
    }
}
