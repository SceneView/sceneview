package io.github.sceneview.demo.sketchfab

import io.github.sceneview.demo.BuildConfig

/**
 * Configuration for the Sketchfab Data API v3.
 *
 * The API key is injected at build time via [BuildConfig.SKETCHFAB_API_KEY]
 * (populated from the `SKETCHFAB_API_KEY` environment variable or the
 * `sketchfab.api.key` property in `local.properties` — see this module's
 * `build.gradle`). A `SKETCHFAB_API_KEY` env variable at runtime is also
 * checked as a last-resort fallback (useful when running unit tests from a
 * shell that exports the variable but doesn't propagate it to Gradle).
 *
 * TODO V1.1: move to backend proxy via mcp-gateway to avoid bundling the key
 * directly in the Android app binary. End-users should authenticate against
 * the proxy (which holds the master key server-side) so we don't ship a
 * long-lived token that can be extracted from `.apk` files.
 */
object SketchfabConfig {

    /** Base URL of the Sketchfab Data API v3. Always ends with a trailing slash. */
    const val BASE_URL: String = "https://api.sketchfab.com/v3/"

    /**
     * API key injected at build time, or `null` when missing.
     *
     * Callers should surface [SketchfabService.SketchfabError.MissingApiKey]
     * in that case rather than firing unauthenticated requests.
     */
    val apiKey: String?
        get() {
            val fromBuildConfig = BuildConfig.SKETCHFAB_API_KEY
            if (fromBuildConfig.isNotBlank()) return fromBuildConfig
            val fromEnv = System.getenv("SKETCHFAB_API_KEY")
            return fromEnv?.takeIf { it.isNotBlank() }
        }

    /** Subdirectory under `Context.cacheDir` where downloaded GLB files live. */
    const val CACHE_DIR_NAME: String = "sketchfab"

    /** Maximum cache size on disk, in bytes (500 MB). */
    const val CACHE_MAX_BYTES: Long = 500L * 1024 * 1024
}
