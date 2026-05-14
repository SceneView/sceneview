package io.github.sceneview.demo.sketchfab

/**
 * A single streamable Sketchfab asset surfaced to a sample/demo.
 *
 * Stage-1 contract for [#1152](https://github.com/sceneview/sceneview/issues/1152) — the demo
 * code never references a Sketchfab UID directly. It looks up a [SketchfabSlug] in
 * [SampleAssets], hands it to [SketchfabAssetResolver.resolve], and renders the returned
 * `File` via the normal `rememberModelInstance(modelLoader, file.absolutePath)` flow.
 *
 * **License contract.** The [license] field MUST equal one of:
 *  - `"CC-BY-4.0"`
 *  - `"CC-BY-3.0"`
 *  - `"CC0-1.0"` (or `"CC-0"` legacy form — both are accepted)
 *
 * Anything else triggers an [IllegalStateException] inside the [SampleAssets] `init`
 * block, failing the build at startup so an un-credited model can never reach
 * the Play Store binary. See [VALID_LICENSES].
 *
 * @property uid                  The Sketchfab model UID (32-char hex). Used to call
 *                                [SketchfabService.downloadModel].
 * @property fallbackBundledPath  Relative path under `samples/android-demo/src/main/assets/`
 *                                (e.g. `"models/khronos_damaged_helmet.glb"`) that the
 *                                resolver copies to a temp file when the network path
 *                                is unavailable (no key, download error, bounds reject).
 * @property scaleToUnits         Target world-space side length (metres) of the longest axis
 *                                after loading. Sketchfab UIDs ship at wildly different
 *                                scales — this is what the demo uses to normalize.
 * @property hasBakedAnimation    `true` if the glTF is expected to include at least one
 *                                animation. The resolver rejects models whose actual
 *                                `animationCount` doesn't agree, falling back to the
 *                                bundled asset so an animated demo never silently renders
 *                                a static T-pose.
 * @property license              SPDX-ish license tag (see [VALID_LICENSES]).
 * @property attribution          Human-readable "Author Name" string surfaced in the
 *                                About → Credits sheet (Stage 3). Mandatory by CC-BY.
 * @property sketchfabPageUrl     The Sketchfab model page URL — surfaced in the Credits
 *                                sheet as the link to the original work. Mandatory by CC-BY.
 */
data class SketchfabSlug(
    val uid: String,
    val fallbackBundledPath: String,
    val scaleToUnits: Float,
    val hasBakedAnimation: Boolean,
    val license: String,
    val attribution: String,
    val sketchfabPageUrl: String,
) {
    init {
        require(uid.isNotBlank()) { "SketchfabSlug.uid must not be blank" }
        require(fallbackBundledPath.isNotBlank()) {
            "SketchfabSlug($uid).fallbackBundledPath must not be blank — every slug needs a " +
                "bundled fallback so cold-cache / no-key launches still render something."
        }
        require(scaleToUnits > 0f) {
            "SketchfabSlug($uid).scaleToUnits must be positive (got $scaleToUnits)"
        }
        check(license in VALID_LICENSES) {
            "SketchfabSlug($uid) ships license=\"$license\" — only CC-BY / CC-0 are accepted " +
                "for SceneView demo bundling. Allowed: $VALID_LICENSES"
        }
        require(attribution.isNotBlank()) {
            "SketchfabSlug($uid).attribution must not be blank — CC-BY requires author credit."
        }
        require(sketchfabPageUrl.startsWith("https://sketchfab.com/")) {
            "SketchfabSlug($uid).sketchfabPageUrl must point at sketchfab.com (got $sketchfabPageUrl)"
        }
    }

    companion object {
        /**
         * The only license strings accepted by the build.
         *
         * - `CC-BY-4.0` / `CC-BY-3.0` require visible author + license + link credit
         *   in the About → Credits sheet.
         * - `CC0-1.0` / `CC-0` are public-domain dedications and may be shown without
         *   credit, but the credit row is still surfaced as a courtesy.
         */
        val VALID_LICENSES: Set<String> = setOf(
            "CC-BY-4.0",
            "CC-BY-3.0",
            "CC0-1.0",
            "CC-0",
        )
    }
}

/**
 * Curated, build-time-validated registry of Sketchfab assets used by SceneView demos.
 *
 * **Stage 1 scope.** This file only registers slugs and groups them by category.
 * No demo migrates onto this registry yet — that's [Stage 2 of #1152](https://github.com/sceneview/sceneview/issues/1152).
 *
 * **Sources of truth.** Every UID in this file is cross-referenced with
 * `docs/docs/free-3d-models.md`, which lists publicly known CC-BY models on
 * api.sketchfab.com. Never add a UID here without a row in that doc.
 *
 * **Categories.** Mirrors the planned Stage-2 demo migrations: `solarSystem`, `gallery`,
 * `animation`, `arPlacement`, `physics`, `materials`. iOS ships the SAME categories
 * with the SAME UIDs in the SAME order — see `SampleAssets.swift`.
 */
object SampleAssets {

    // ── solarSystem ───────────────────────────────────────────────────────
    // OrbitalARDemo target — animated planets / creatures that orbit a star.
    // CURATED: 2 confirmed UIDs from docs/docs/free-3d-models.md.
    // TODO: expand to 5-7 items in Stage 2 — needs verified animated planet UIDs.
    val solarSystem: List<SketchfabSlug> = listOf(
        SketchfabSlug(
            uid = "27db6f519be14199b22688fad09a4b43",
            fallbackBundledPath = "models/khronos_lantern.glb",
            scaleToUnits = 1.0f,
            hasBakedAnimation = true,
            license = "CC-BY-4.0",
            attribution = "BlackProject",
            sketchfabPageUrl = "https://sketchfab.com/3d-models/planet-earth-27db6f519be14199b22688fad09a4b43",
        ),
        SketchfabSlug(
            uid = "23223f2e898945dbbb6e10b838a70c04",
            fallbackBundledPath = "models/khronos_lantern.glb",
            scaleToUnits = 1.0f,
            hasBakedAnimation = false,
            license = "CC-BY-4.0",
            attribution = "Jeremy Faivre",
            sketchfabPageUrl = "https://sketchfab.com/3d-models/earth-3d-globe-23223f2e898945dbbb6e10b838a70c04",
        ),
        // TODO: curate before Stage 2 — need 4-6 more animated themed planets
        // (butterfly, hummingbird, bee, fish, dolphin) from sketchfab `animated=true`
        // search with verified CC-BY licenses. See plan_sketchfab_streaming_samples.md.
    )

    // ── gallery ───────────────────────────────────────────────────────────
    // SceneGalleryDemo target — themed bundles ("Vehicles", "Furniture", "Tech").
    // CURATED: confirmed from docs/docs/free-3d-models.md.
    val gallery: List<SketchfabSlug> = listOf(
        SketchfabSlug(
            uid = "5ef9b845aaf44203b6d04e2c677e444f",
            fallbackBundledPath = "models/khronos_damaged_helmet.glb",
            scaleToUnits = 2.0f,
            hasBakedAnimation = false,
            license = "CC-BY-4.0",
            attribution = "Ameer Studio",
            sketchfabPageUrl = "https://sketchfab.com/3d-models/tesla-2018-model-3-5ef9b845aaf44203b6d04e2c677e444f",
        ),
        SketchfabSlug(
            uid = "1fbf29e297bd4a17ac39a00a378441d8",
            fallbackBundledPath = "models/khronos_damaged_helmet.glb",
            scaleToUnits = 2.0f,
            hasBakedAnimation = false,
            license = "CC-BY-4.0",
            attribution = "metarex.4d",
            sketchfabPageUrl = "https://sketchfab.com/3d-models/tesla-roadster-2020-1fbf29e297bd4a17ac39a00a378441d8",
        ),
        SketchfabSlug(
            uid = "f12e67159f75486bb21213e573520612",
            fallbackBundledPath = "models/khronos_damaged_helmet.glb",
            scaleToUnits = 2.0f,
            hasBakedAnimation = false,
            license = "CC-BY-4.0",
            attribution = "Lexyc16",
            sketchfabPageUrl = "https://sketchfab.com/3d-models/tesla-cybertruck-f12e67159f75486bb21213e573520612",
        ),
        SketchfabSlug(
            uid = "41a071ae12794b668502f58d1e0fd1a3",
            fallbackBundledPath = "models/khronos_damaged_helmet.glb",
            scaleToUnits = 0.16f,
            hasBakedAnimation = false,
            license = "CC-BY-4.0",
            attribution = "MajdyModels",
            sketchfabPageUrl = "https://sketchfab.com/3d-models/iphone-16-pro-max-41a071ae12794b668502f58d1e0fd1a3",
        ),
        SketchfabSlug(
            uid = "9e045e469d514fea9dda2ccd161f5fa3",
            fallbackBundledPath = "models/khronos_damaged_helmet.glb",
            scaleToUnits = 0.16f,
            hasBakedAnimation = false,
            license = "CC-BY-4.0",
            attribution = "Sketcher",
            sketchfabPageUrl = "https://sketchfab.com/3d-models/iphone-15-pro-9e045e469d514fea9dda2ccd161f5fa3",
        ),
    )

    // ── animation ─────────────────────────────────────────────────────────
    // AnimationDemo target — characters with baked skinned animations.
    // CURATED: 1 confirmed UID from docs/docs/free-3d-models.md.
    // TODO: expand to 5 items in Stage 2 — needs verified animated character UIDs.
    val animation: List<SketchfabSlug> = listOf(
        SketchfabSlug(
            uid = "245a8bcfddf44122b1f2e8dfa883c544",
            fallbackBundledPath = "models/threejs_soldier.glb",
            scaleToUnits = 1.8f,
            hasBakedAnimation = true,
            license = "CC-BY-4.0",
            attribution = "loganbro",
            sketchfabPageUrl = "https://sketchfab.com/3d-models/walking-character-245a8bcfddf44122b1f2e8dfa883c544",
        ),
        // TODO: curate before Stage 2 — need 4 more verified animated CC-BY characters
        // (idle, run, dance loops). See plan_sketchfab_streaming_samples.md.
    )

    // ── arPlacement ───────────────────────────────────────────────────────
    // ARPlacementDemo / ARInstantPlacementDemo target — furniture + props.
    // CURATED: confirmed from docs/docs/free-3d-models.md (furniture row).
    val arPlacement: List<SketchfabSlug> = listOf(
        SketchfabSlug(
            uid = "5b7d9dae3db24fd499cd2d28283daa3f",
            fallbackBundledPath = "models/khronos_toy_car.glb",
            scaleToUnits = 1.2f,
            hasBakedAnimation = false,
            license = "CC-BY-4.0",
            attribution = "harshaog07",
            sketchfabPageUrl = "https://sketchfab.com/3d-models/leather-furniture-set-sofa-5b7d9dae3db24fd499cd2d28283daa3f",
        ),
        SketchfabSlug(
            uid = "f3622d9edcc94f1aa4b0bd95f1d5cda2",
            fallbackBundledPath = "models/khronos_toy_car.glb",
            scaleToUnits = 1.5f,
            hasBakedAnimation = false,
            license = "CC-BY-4.0",
            attribution = "Blaz Mraz",
            sketchfabPageUrl = "https://sketchfab.com/3d-models/couchsofa-set-f3622d9edcc94f1aa4b0bd95f1d5cda2",
        ),
        SketchfabSlug(
            uid = "3220ea6654e7483d8aa101a024cad81a",
            fallbackBundledPath = "models/khronos_toy_car.glb",
            scaleToUnits = 0.9f,
            hasBakedAnimation = false,
            license = "CC-BY-4.0",
            attribution = "Urielgc",
            sketchfabPageUrl = "https://sketchfab.com/3d-models/modern-black-chair-3220ea6654e7483d8aa101a024cad81a",
        ),
    )

    // ── physics ───────────────────────────────────────────────────────────
    // PhysicsDemo target — small dense props for falling-object simulations.
    // CURATED: 2 confirmed UIDs from docs/docs/free-3d-models.md (watch / sneaker).
    // TODO: expand to 5-7 items in Stage 2 — needs verified physics-friendly UIDs
    // (vases, chairs, balls).
    val physics: List<SketchfabSlug> = listOf(
        SketchfabSlug(
            uid = "693e3e5fc41b42c5af35bc965ab70014",
            fallbackBundledPath = "models/khronos_toy_car.glb",
            scaleToUnits = 0.05f,
            hasBakedAnimation = false,
            license = "CC-BY-4.0",
            attribution = "DevPoly3D",
            sketchfabPageUrl = "https://sketchfab.com/3d-models/watch-luxury-wristwatch-3d-model-693e3e5fc41b42c5af35bc965ab70014",
        ),
        SketchfabSlug(
            uid = "9a0bbbb955384ac68486c6cb3768ccb3",
            fallbackBundledPath = "models/khronos_toy_car.glb",
            scaleToUnits = 0.3f,
            hasBakedAnimation = false,
            license = "CC-BY-4.0",
            attribution = "Taohid Animation",
            sketchfabPageUrl = "https://sketchfab.com/3d-models/shoe-model-realistic-3d-sneaker-9a0bbbb955384ac68486c6cb3768ccb3",
        ),
    )

    // ── materials ─────────────────────────────────────────────────────────
    // MaterialsDemo target — KHR_materials_* showcase (sheen, transmission, …).
    // CURATED: 1 confirmed UID from docs/docs/free-3d-models.md (Golden Watch, PBR-rich).
    // TODO: expand to 5 items in Stage 2 — needs verified KHR_materials_* showcase UIDs.
    val materials: List<SketchfabSlug> = listOf(
        SketchfabSlug(
            uid = "2e53321ad51c41c2b16055437e544d10",
            fallbackBundledPath = "models/khronos_damaged_helmet.glb",
            scaleToUnits = 0.05f,
            hasBakedAnimation = false,
            license = "CC-BY-4.0",
            attribution = "Mehdi Belhous",
            sketchfabPageUrl = "https://sketchfab.com/3d-models/golden-watch-2e53321ad51c41c2b16055437e544d10",
        ),
        // TODO: curate before Stage 2 — need 4 more verified PBR-rich CC-BY models
        // exercising KHR_materials_sheen / _transmission / _iridescence.
    )

    /**
     * Flat list of every registered slug, for whole-app prefetch + Credits-sheet rendering.
     */
    val all: List<SketchfabSlug> =
        solarSystem + gallery + animation + arPlacement + physics + materials

    /**
     * Categories surfaced to UI / docs.  Same shape on iOS — kept identical so
     * a Stage-2 PR can migrate the demo once and the resolver returns equivalent
     * assets on both stores.
     */
    val byCategory: Map<String, List<SketchfabSlug>> = linkedMapOf(
        "solarSystem" to solarSystem,
        "gallery" to gallery,
        "animation" to animation,
        "arPlacement" to arPlacement,
        "physics" to physics,
        "materials" to materials,
    )

    init {
        // Sanity contract: every slug must have already passed its own `init` (license
        // tag, fallback path, attribution). Re-validating here catches the case where
        // a developer hand-edits a single field without bumping the slug constructor,
        // because object-init runs lazily once and surfaces at app startup — before
        // any demo can try to resolve a malformed entry.
        val seen = mutableSetOf<String>()
        for (slug in all) {
            check(slug.uid !in seen) {
                "SampleAssets: duplicate SketchfabSlug uid=${slug.uid} in registry."
            }
            seen += slug.uid
        }
    }
}
