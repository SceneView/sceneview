package io.github.sceneview.demo.sketchfab

/**
 * Curated metadata for a single Sketchfab asset that is safe to stream from
 * the demo apps.
 *
 * Every entry must satisfy three invariants before it is allowed into
 * [SampleAssets]:
 *
 *  1. **License is CC-BY** — the only Sketchfab license SceneView's demo apps
 *     redistribute. Other Creative Commons variants (NC, ND, SA) and the
 *     bespoke "Sketchfab Standard" license are intentionally excluded.
 *     Enforced at runtime by [SampleAssets.requireValid].
 *  2. **Fallback bundled asset exists** — the resolver falls back to
 *     [fallbackBundledPath] when no Sketchfab API key is configured (App Store
 *     builds, offline cold cache, network failure). This guarantees the
 *     `feedback_demo_quality` rule "NEVER ship a build that needs the network
 *     to render something useful".
 *  3. **Scale hint + animation hint are honest** — both surface in the bounds
 *     sanity check in [SketchfabAssetResolver.resolve] so we can blacklist
 *     drifting slugs (authors edit their models over time).
 *
 * @property uid The Sketchfab model uid (matches the path segment in
 *   `sketchfab.com/3d-models/<slug>-<uid>`). Always the unique identifier —
 *   never trust the `<slug>` portion which authors can edit.
 * @property displayName Human-readable label shown in carousels, credits
 *   sheets and registry test reports.
 * @property author The Sketchfab username of the original author (required by
 *   CC-BY attribution). Printed in the Stage 3 credits sheet.
 * @property licenseUrl Direct link to the CC-BY license text. Pinned to the
 *   exact license version (`/licenses/by/4.0/` not `/licenses/by/`) so legal
 *   review can audit the registry without resolving redirects.
 * @property fallbackBundledPath Asset-relative path inside the demo app
 *   (`assets/models/...` on Android, `Models/...` on iOS) to ship when the
 *   network or API key is unavailable. Used by
 *   [SketchfabAssetResolver.fallbackBundle].
 * @property scaleToUnits Expected post-load bounding-sphere radius in metres.
 *   Used by [SketchfabAssetResolver.boundsAreSane] to detect models that
 *   silently changed scale.
 * @property hasBakedAnimation `true` when the model carries one or more
 *   skeletal animations — checked against the GLB's actual animation count by
 *   the bounds sanity step.
 * @property category Free-form grouping (`solar`, `gallery`, `animation`,
 *   `ar_placement`, `physics`, `materials`). Used by
 *   [SketchfabAssetResolver.prefetchAll] to warm a category of demos at once.
 * @property tags Optional free-form tags surfaced in the credits sheet (e.g.
 *   `"low-poly"`, `"hard-surface"`).
 */
data class SketchfabSlug(
    val uid: String,
    val displayName: String,
    val author: String,
    val licenseUrl: String,
    val fallbackBundledPath: String,
    val scaleToUnits: Float,
    val hasBakedAnimation: Boolean,
    val category: String,
    val tags: List<String> = emptyList(),
) {
    init {
        require(uid.isNotBlank()) { "SketchfabSlug.uid must not be blank" }
        require(displayName.isNotBlank()) {
            "SketchfabSlug.displayName must not be blank (uid=$uid)"
        }
        require(author.isNotBlank()) {
            "SketchfabSlug.author must not be blank for CC-BY attribution (uid=$uid)"
        }
        require(licenseUrl.startsWith("https://creativecommons.org/licenses/by/")) {
            "SketchfabSlug.licenseUrl must be a CC-BY creativecommons.org URL (uid=$uid). " +
                "Other licenses (NC, ND, SA, Sketchfab Standard) are not allowed in the demo " +
                "registry — see SampleAssets KDoc."
        }
        require(fallbackBundledPath.isNotBlank()) {
            "SketchfabSlug.fallbackBundledPath must not be blank (uid=$uid). Every entry " +
                "needs an offline fallback so demos render without a key/network."
        }
        require(scaleToUnits > 0f) {
            "SketchfabSlug.scaleToUnits must be > 0 (uid=$uid)"
        }
        require(category.isNotBlank()) {
            "SketchfabSlug.category must not be blank (uid=$uid)"
        }
    }
}
