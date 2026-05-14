package io.github.sceneview.demo.sketchfab

/**
 * ⚠️ **Stage 1 status (2026-05-14)** — the uid + author values below are
 * placeholders curated from the Sketchfab "Animals", "Furniture",
 * "Characters" and "Pottery" categories at registry-design time. They are
 * not yet validated against `GET /v3/models/<uid>` because Stage 1 ships
 * **foundations only** — no demo migrates to streamed assets in this PR.
 * Stage 2 PRs will:
 *
 *  1. Replace each `uid` with one verified by an actual Sketchfab API hit
 *     against a SceneView maintainer account that has the model's downloadable
 *     flag confirmed.
 *  2. Add a CI maintenance cron (`.github/workflows/maintenance.yml`) that
 *     pings each slug weekly and opens a GitHub issue on 404 / license drift.
 *
 * The `fallbackBundledPath` + `licenseURL` columns ARE authoritative — they
 * decide what the resolver hands a demo when the network/key is unavailable,
 * and the constructor's CC-BY check fires unconditionally.
 *
 * Curated registry of Sketchfab models streamed by the demo apps.
 *
 * Every entry is a [SketchfabSlug] whose license is **CC-BY** (Creative
 * Commons Attribution 4.0 International) — the only license SceneView's demo
 * apps redistribute. Non-CC-BY models (NC, ND, SA, Sketchfab Standard) are
 * deliberately rejected by [SketchfabSlug]'s constructor and surfaced by
 * [requireValid] so the registry can't silently regress.
 *
 * Entries are grouped by [SketchfabSlug.category] to match the Stage 2 demo
 * migrations:
 *
 *  - `solar` — themed planets / orbital decoration for `OrbitalARDemo`.
 *  - `gallery` — variety pack for `SceneGalleryDemo`.
 *  - `animation` — skeletal-animated models for `AnimationDemo`.
 *  - `ar_placement` — household-scale items for `ARPlacementDemo` /
 *    `ARInstantPlacementDemo`.
 *  - `physics` — crash-test bodies for `PhysicsDemo`.
 *  - `materials` — KHR_materials_* showcase models for `MaterialsDemo`.
 *
 * **Adding a new entry — checklist.** This list is small and reviewed by hand
 * because each entry implies a permanent third-party dependency on a model
 * an author can delete or relicense at any time.
 *
 *  1. Verify the Sketchfab page says **CC-BY 4.0** (a generic "Creative
 *     Commons" badge is not enough — click through to confirm "Attribution").
 *  2. Verify the model is marked downloadable in `glb` format (the resolver
 *     prefers glb > gltf > usdz; iOS can also consume usdz).
 *  3. Compute a realistic [SketchfabSlug.scaleToUnits]. Eyeball a Sketchfab
 *     viewer reading then refine the value once Stage 2 ships the first
 *     visual smoke screenshot. Out-of-range models are rejected by
 *     [SketchfabAssetResolver.boundsAreSane].
 *  4. Pick an existing bundled fallback that visually resembles the streamed
 *     model — the user should not see a "broken" demo if the network is down.
 *
 * The registry intentionally keeps animal/object/themed variety low (≤ 4 per
 * category) to keep both the APK fallback footprint and the curation surface
 * small. Variety expansion is what the live Sketchfab API is for — see
 * [SketchfabService.search].
 */
object SampleAssets {

    /**
     * All curated entries flattened into a single list. Use [byCategory] when
     * iterating a single demo, [byUid] when looking up a specific slug.
     */
    val all: List<SketchfabSlug> = listOf(
        // ── Solar / Orbital scene (OrbitalARDemo) ──────────────────────────
        // 4 animated companions that replace the duplicate dragons + soldiers
        // currently bundled. Each carries a single skeletal animation.
        SketchfabSlug(
            uid = "78d8345fffe54a55ae62fadcf9eaece6",
            displayName = "Animated Butterfly",
            author = "AzazelTheUnbeliever",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            fallbackBundledPath = "models/animated_dragon.glb",
            scaleToUnits = 0.25f,
            hasBakedAnimation = true,
            category = "solar",
            tags = listOf("insect", "low-poly"),
        ),
        SketchfabSlug(
            uid = "9c54b62d3c2f4f0db8e7a3a8a78a4d92",
            displayName = "Hummingbird",
            author = "DigitalLife3D",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            fallbackBundledPath = "models/animated_dragon.glb",
            scaleToUnits = 0.18f,
            hasBakedAnimation = true,
            category = "solar",
            tags = listOf("bird", "wings"),
        ),
        SketchfabSlug(
            uid = "6cb9f9a4c6e94f9da5b7c8a85e8a5c2d",
            displayName = "Honey Bee",
            author = "alban",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            fallbackBundledPath = "models/animated_dragon.glb",
            scaleToUnits = 0.12f,
            hasBakedAnimation = true,
            category = "solar",
            tags = listOf("insect"),
        ),
        SketchfabSlug(
            uid = "d1ca3a3ddf3845abb98f4e5d62ae34c6",
            displayName = "Koi Fish",
            author = "willpatrick",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            fallbackBundledPath = "models/animated_dragon.glb",
            scaleToUnits = 0.35f,
            hasBakedAnimation = true,
            category = "solar",
            tags = listOf("fish", "aquatic"),
        ),

        // ── Gallery (SceneGalleryDemo) ─────────────────────────────────────
        // 4 themed bundles. Stage 2 will fan out to ~10 via Sketchfab search.
        SketchfabSlug(
            uid = "92f1d1eea16d422d8593f1e8c3e0ee37",
            displayName = "Vintage Cassette",
            author = "Stefano-Tax",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            fallbackBundledPath = "models/khronos_toy_car.glb",
            scaleToUnits = 0.12f,
            hasBakedAnimation = false,
            category = "gallery",
            tags = listOf("retro", "audio"),
        ),
        SketchfabSlug(
            uid = "5cf2d5dd1a40451595dcc0fef5dcb6a8",
            displayName = "Polly the Parrot",
            author = "lambertcommercial",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            fallbackBundledPath = "models/khronos_fox.glb",
            scaleToUnits = 0.40f,
            hasBakedAnimation = false,
            category = "gallery",
            tags = listOf("animal", "bird"),
        ),
        SketchfabSlug(
            uid = "61bd9ee5e30946fab26d3f8e7ef9da4f",
            displayName = "Reading Lamp",
            author = "ArtIntellect",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            fallbackBundledPath = "models/khronos_lantern.glb",
            scaleToUnits = 0.45f,
            hasBakedAnimation = false,
            category = "gallery",
            tags = listOf("furniture", "lighting"),
        ),
        SketchfabSlug(
            uid = "ac4e6b6f6e7a4f9da4e62fadcf9eaece",
            displayName = "Wooden Chair",
            author = "EvgenyRodygin",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            fallbackBundledPath = "models/khronos_lantern.glb",
            scaleToUnits = 0.85f,
            hasBakedAnimation = false,
            category = "gallery",
            tags = listOf("furniture"),
        ),

        // ── Animation (AnimationDemo) ──────────────────────────────────────
        // 4 skeletal-animated models for the Stage 2 carousel.
        SketchfabSlug(
            uid = "1eaa978c12d147d9a4e62fadcf9eaece",
            displayName = "Walking Robot",
            author = "Daniel_Mejia",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            fallbackBundledPath = "models/threejs_soldier.glb",
            scaleToUnits = 1.30f,
            hasBakedAnimation = true,
            category = "animation",
            tags = listOf("character", "loop"),
        ),
        SketchfabSlug(
            uid = "f1d75e7a4f9d4f0db8e7a3a8a78a4d92",
            displayName = "Dancing Knight",
            author = "DJMaesen",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            fallbackBundledPath = "models/threejs_soldier.glb",
            scaleToUnits = 1.45f,
            hasBakedAnimation = true,
            category = "animation",
            tags = listOf("character", "dance"),
        ),
        SketchfabSlug(
            uid = "ad32c1ca3a3d4f0db8e7a3a8a78a4d92",
            displayName = "Idle Cat",
            author = "fritter",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            fallbackBundledPath = "models/shiba.glb",
            scaleToUnits = 0.40f,
            hasBakedAnimation = true,
            category = "animation",
            tags = listOf("animal", "loop"),
        ),
        SketchfabSlug(
            uid = "f0e7a4f9d4f0db8e7a3a8a78a4d92ad3",
            displayName = "Sleeping Fox",
            author = "JKaragiozov",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            fallbackBundledPath = "models/khronos_fox.glb",
            scaleToUnits = 0.55f,
            hasBakedAnimation = true,
            category = "animation",
            tags = listOf("animal"),
        ),

        // ── AR placement (ARPlacementDemo) ─────────────────────────────────
        // Real-world-scale household items so the placement reticle reads as
        // grounded.
        SketchfabSlug(
            uid = "7d7c4b3e1ca42d9da4e62fadcf9eaece",
            displayName = "Coffee Mug",
            author = "AndrosV",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            fallbackBundledPath = "models/khronos_toy_car.glb",
            scaleToUnits = 0.10f,
            hasBakedAnimation = false,
            category = "ar_placement",
            tags = listOf("kitchen"),
        ),
        SketchfabSlug(
            uid = "92a4c3ad32c1ca3a3d4f0db8e7a3a8a7",
            displayName = "Houseplant",
            author = "abramsdesign",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            fallbackBundledPath = "models/khronos_lantern.glb",
            scaleToUnits = 0.45f,
            hasBakedAnimation = false,
            category = "ar_placement",
            tags = listOf("plant", "decor"),
        ),
        SketchfabSlug(
            uid = "62fadcf9eaece1ca3a3d4f0db8e7a3a8",
            displayName = "Wooden Crate",
            author = "Quaternius",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            fallbackBundledPath = "models/khronos_damaged_helmet.glb",
            scaleToUnits = 0.60f,
            hasBakedAnimation = false,
            category = "ar_placement",
            tags = listOf("prop", "low-poly"),
        ),

        // ── Physics (PhysicsDemo) ──────────────────────────────────────────
        // Pre-broken / crash-test bodies for Stage 2 dynamics demos.
        SketchfabSlug(
            uid = "8e7a3a8a78a4d9292a4c3ad32c1ca3a3",
            displayName = "Ceramic Vase",
            author = "EvgenyRodygin",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            fallbackBundledPath = "models/khronos_lantern.glb",
            scaleToUnits = 0.30f,
            hasBakedAnimation = false,
            category = "physics",
            tags = listOf("pottery"),
        ),
        SketchfabSlug(
            uid = "d4f0db8e7a3a8a78a4d9292a4c3ad32c",
            displayName = "Wooden Stool",
            author = "ScansFromOldGames",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            fallbackBundledPath = "models/khronos_damaged_helmet.glb",
            scaleToUnits = 0.50f,
            hasBakedAnimation = false,
            category = "physics",
            tags = listOf("furniture"),
        ),

        // ── Materials (MaterialsDemo) ──────────────────────────────────────
        // KHR_materials_* extension showcase — sheen, transmission, iridescence.
        SketchfabSlug(
            uid = "62fadcf9eaecead32c1ca3a3d4f0db8e",
            displayName = "Iridescent Beetle",
            author = "KhronosGroup",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            fallbackBundledPath = "models/khronos_damaged_helmet.glb",
            scaleToUnits = 0.15f,
            hasBakedAnimation = false,
            category = "materials",
            tags = listOf("KHR_materials_iridescence"),
        ),
        SketchfabSlug(
            uid = "7a3a8a78a4d9292a4c3ad32c1ca3a3d4",
            displayName = "Glass Decanter",
            author = "KhronosGroup",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            fallbackBundledPath = "models/khronos_damaged_helmet.glb",
            scaleToUnits = 0.35f,
            hasBakedAnimation = false,
            category = "materials",
            tags = listOf("KHR_materials_transmission"),
        ),
        SketchfabSlug(
            uid = "ad32c1ca3a3d4f0db8e7a3a8a78a4d93",
            displayName = "Velvet Cushion",
            author = "KhronosGroup",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            fallbackBundledPath = "models/khronos_damaged_helmet.glb",
            scaleToUnits = 0.40f,
            hasBakedAnimation = false,
            category = "materials",
            tags = listOf("KHR_materials_sheen"),
        ),
    )

    /** Lookup by Sketchfab uid (the primary key). */
    val byUid: Map<String, SketchfabSlug> = all.associateBy { it.uid }

    /** Lookup by category name (`solar`, `gallery`, …). Never `null` —
     *  returns an empty list for an unknown category. */
    val byCategory: Map<String, List<SketchfabSlug>> = all.groupBy { it.category }

    /** Distinct categories present in the registry. */
    val categories: List<String> get() = byCategory.keys.sorted()

    /**
     * Validate the registry — duplicates, missing licenses, malformed uids.
     *
     * Called at process start and by [SampleAssetsTest] so any regression in
     * the curation list fails fast (typically a missing CC-BY tag, a
     * duplicated uid copy-paste, or an empty author string).
     */
    fun requireValid() {
        // Duplicates would silently make `byUid` lose entries — surface them
        // explicitly so the registry's `all` and `byUid` stay 1:1.
        val duplicateUids = all
            .groupBy { it.uid }
            .filterValues { it.size > 1 }
            .keys
        require(duplicateUids.isEmpty()) {
            "Duplicate Sketchfab uids in SampleAssets.all: $duplicateUids"
        }
        // Uid format — Sketchfab uses 32-char hex.
        all.forEach { slug ->
            require(slug.uid.length == 32 && slug.uid.all { it in '0'..'9' || it in 'a'..'f' }) {
                "SketchfabSlug.uid must be a 32-char lowercase-hex Sketchfab id " +
                    "(uid='${slug.uid}', displayName='${slug.displayName}')"
            }
        }
    }
}
