import Foundation

/// ⚠️ **Stage 1 status (2026-05-14)** — the uid + author values below are
/// placeholders curated at registry-design time. They are NOT yet validated
/// against `GET /v3/models/<uid>`. Stage 1 ships foundations only; no demo
/// migrates to streamed assets in this PR. Stage 2 will replace each `uid`
/// with one verified live and add a weekly CI cron to surface slug drift.
///
/// Curated registry of Sketchfab models streamed by the iOS demo app.
///
/// Mirrors the Android registry `SampleAssets.kt` 1:1 — same uids, same
/// categories, same scale hints. The iOS fallback paths point to bundled
/// USDZ assets under `samples/ios-demo/SceneViewDemo/Models/` whereas Android
/// points to GLBs under `samples/android-demo/src/main/assets/models/`; the
/// uid is the cross-platform key.
///
/// Every entry is a `SketchfabSlug` whose license is **CC-BY** (Creative
/// Commons Attribution 4.0 International) — the only license SceneView's
/// demo apps redistribute. Non-CC-BY models (NC, ND, SA, Sketchfab Standard)
/// are deliberately rejected by `SketchfabSlug`'s `init(...)` and surfaced
/// by `validate()` so the registry can't silently regress.
///
/// **Adding a new entry — checklist.**
///
///  1. Confirm the Sketchfab page says **CC-BY 4.0** (a generic "Creative
///     Commons" badge is not enough).
///  2. Confirm the model is downloadable in `usdz` format (iOS prefers usdz
///     for RealityKit compatibility; the iOS resolver does not transcode
///     glTF).
///  3. Eyeball a realistic `scaleToUnits` — the bounds sanity check in
///     `SketchfabAssetResolver.boundsAreSane(_:slug:)` rejects values outside
///     `[0.05 m, 5 m]`.
///  4. Pick an existing bundled fallback that visually resembles the streamed
///     model.
enum SampleAssets {

    /// All curated entries flattened into a single list.
    static let all: [SketchfabSlug] = [
        // ── Solar / Orbital scene (OrbitalARDemo) ──────────────────────────
        SketchfabSlug(
            uid: "78d8345fffe54a55ae62fadcf9eaece6",
            displayName: "Animated Butterfly",
            author: "AzazelTheUnbeliever",
            licenseURL: URL(string: "https://creativecommons.org/licenses/by/4.0/")!,
            fallbackBundledPath: "Models/animated_butterfly.usdz",
            scaleToUnits: 0.25,
            hasBakedAnimation: true,
            category: "solar",
            tags: ["insect", "low-poly"]
        ),
        SketchfabSlug(
            uid: "9c54b62d3c2f4f0db8e7a3a8a78a4d92",
            displayName: "Hummingbird",
            author: "DigitalLife3D",
            licenseURL: URL(string: "https://creativecommons.org/licenses/by/4.0/")!,
            fallbackBundledPath: "Models/animated_butterfly.usdz",
            scaleToUnits: 0.18,
            hasBakedAnimation: true,
            category: "solar",
            tags: ["bird", "wings"]
        ),
        SketchfabSlug(
            uid: "6cb9f9a4c6e94f9da5b7c8a85e8a5c2d",
            displayName: "Honey Bee",
            author: "alban",
            licenseURL: URL(string: "https://creativecommons.org/licenses/by/4.0/")!,
            fallbackBundledPath: "Models/animated_butterfly.usdz",
            scaleToUnits: 0.12,
            hasBakedAnimation: true,
            category: "solar",
            tags: ["insect"]
        ),
        SketchfabSlug(
            uid: "d1ca3a3ddf3845abb98f4e5d62ae34c6",
            displayName: "Koi Fish",
            author: "willpatrick",
            licenseURL: URL(string: "https://creativecommons.org/licenses/by/4.0/")!,
            fallbackBundledPath: "Models/animated_butterfly.usdz",
            scaleToUnits: 0.35,
            hasBakedAnimation: true,
            category: "solar",
            tags: ["fish", "aquatic"]
        ),

        // ── Gallery (SceneGalleryDemo) ─────────────────────────────────────
        SketchfabSlug(
            uid: "92f1d1eea16d422d8593f1e8c3e0ee37",
            displayName: "Vintage Cassette",
            author: "Stefano-Tax",
            licenseURL: URL(string: "https://creativecommons.org/licenses/by/4.0/")!,
            fallbackBundledPath: "Models/retro_piano.usdz",
            scaleToUnits: 0.12,
            hasBakedAnimation: false,
            category: "gallery",
            tags: ["retro", "audio"]
        ),
        SketchfabSlug(
            uid: "5cf2d5dd1a40451595dcc0fef5dcb6a8",
            displayName: "Polly the Parrot",
            author: "lambertcommercial",
            licenseURL: URL(string: "https://creativecommons.org/licenses/by/4.0/")!,
            fallbackBundledPath: "Models/phoenix_bird.usdz",
            scaleToUnits: 0.40,
            hasBakedAnimation: false,
            category: "gallery",
            tags: ["animal", "bird"]
        ),
        SketchfabSlug(
            uid: "61bd9ee5e30946fab26d3f8e7ef9da4f",
            displayName: "Reading Lamp",
            author: "ArtIntellect",
            licenseURL: URL(string: "https://creativecommons.org/licenses/by/4.0/")!,
            fallbackBundledPath: "Models/fantasy_book.usdz",
            scaleToUnits: 0.45,
            hasBakedAnimation: false,
            category: "gallery",
            tags: ["furniture", "lighting"]
        ),
        SketchfabSlug(
            uid: "ac4e6b6f6e7a4f9da4e62fadcf9eaece",
            displayName: "Wooden Chair",
            author: "EvgenyRodygin",
            licenseURL: URL(string: "https://creativecommons.org/licenses/by/4.0/")!,
            fallbackBundledPath: "Models/fantasy_book.usdz",
            scaleToUnits: 0.85,
            hasBakedAnimation: false,
            category: "gallery",
            tags: ["furniture"]
        ),

        // ── Animation (AnimationDemo) ──────────────────────────────────────
        SketchfabSlug(
            uid: "1eaa978c12d147d9a4e62fadcf9eaece",
            displayName: "Walking Robot",
            author: "Daniel_Mejia",
            licenseURL: URL(string: "https://creativecommons.org/licenses/by/4.0/")!,
            fallbackBundledPath: "Models/cyberpunk_character.usdz",
            scaleToUnits: 1.30,
            hasBakedAnimation: true,
            category: "animation",
            tags: ["character", "loop"]
        ),
        SketchfabSlug(
            uid: "f1d75e7a4f9d4f0db8e7a3a8a78a4d92",
            displayName: "Dancing Knight",
            author: "DJMaesen",
            licenseURL: URL(string: "https://creativecommons.org/licenses/by/4.0/")!,
            fallbackBundledPath: "Models/cyberpunk_character.usdz",
            scaleToUnits: 1.45,
            hasBakedAnimation: true,
            category: "animation",
            tags: ["character", "dance"]
        ),
        SketchfabSlug(
            uid: "ad32c1ca3a3d4f0db8e7a3a8a78a4d92",
            displayName: "Idle Cat",
            author: "fritter",
            licenseURL: URL(string: "https://creativecommons.org/licenses/by/4.0/")!,
            fallbackBundledPath: "Models/animated_butterfly.usdz",
            scaleToUnits: 0.40,
            hasBakedAnimation: true,
            category: "animation",
            tags: ["animal", "loop"]
        ),
        SketchfabSlug(
            uid: "f0e7a4f9d4f0db8e7a3a8a78a4d92ad3",
            displayName: "Sleeping Fox",
            author: "JKaragiozov",
            licenseURL: URL(string: "https://creativecommons.org/licenses/by/4.0/")!,
            fallbackBundledPath: "Models/animated_butterfly.usdz",
            scaleToUnits: 0.55,
            hasBakedAnimation: true,
            category: "animation",
            tags: ["animal"]
        ),

        // ── AR placement (ARPlacementDemo) ─────────────────────────────────
        SketchfabSlug(
            uid: "7d7c4b3e1ca42d9da4e62fadcf9eaece",
            displayName: "Coffee Mug",
            author: "AndrosV",
            licenseURL: URL(string: "https://creativecommons.org/licenses/by/4.0/")!,
            fallbackBundledPath: "Models/retro_piano.usdz",
            scaleToUnits: 0.10,
            hasBakedAnimation: false,
            category: "ar_placement",
            tags: ["kitchen"]
        ),
        SketchfabSlug(
            uid: "92a4c3ad32c1ca3a3d4f0db8e7a3a8a7",
            displayName: "Houseplant",
            author: "abramsdesign",
            licenseURL: URL(string: "https://creativecommons.org/licenses/by/4.0/")!,
            fallbackBundledPath: "Models/tree_scene.usdz",
            scaleToUnits: 0.45,
            hasBakedAnimation: false,
            category: "ar_placement",
            tags: ["plant", "decor"]
        ),
        SketchfabSlug(
            uid: "62fadcf9eaece1ca3a3d4f0db8e7a3a8",
            displayName: "Wooden Crate",
            author: "Quaternius",
            licenseURL: URL(string: "https://creativecommons.org/licenses/by/4.0/")!,
            fallbackBundledPath: "Models/game_boy_classic.usdz",
            scaleToUnits: 0.60,
            hasBakedAnimation: false,
            category: "ar_placement",
            tags: ["prop", "low-poly"]
        ),

        // ── Physics (PhysicsDemo) ──────────────────────────────────────────
        SketchfabSlug(
            uid: "8e7a3a8a78a4d9292a4c3ad32c1ca3a3",
            displayName: "Ceramic Vase",
            author: "EvgenyRodygin",
            licenseURL: URL(string: "https://creativecommons.org/licenses/by/4.0/")!,
            fallbackBundledPath: "Models/fantasy_book.usdz",
            scaleToUnits: 0.30,
            hasBakedAnimation: false,
            category: "physics",
            tags: ["pottery"]
        ),
        SketchfabSlug(
            uid: "d4f0db8e7a3a8a78a4d9292a4c3ad32c",
            displayName: "Wooden Stool",
            author: "ScansFromOldGames",
            licenseURL: URL(string: "https://creativecommons.org/licenses/by/4.0/")!,
            fallbackBundledPath: "Models/fantasy_book.usdz",
            scaleToUnits: 0.50,
            hasBakedAnimation: false,
            category: "physics",
            tags: ["furniture"]
        ),

        // ── Materials (MaterialsDemo) ──────────────────────────────────────
        SketchfabSlug(
            uid: "62fadcf9eaecead32c1ca3a3d4f0db8e",
            displayName: "Iridescent Beetle",
            author: "KhronosGroup",
            licenseURL: URL(string: "https://creativecommons.org/licenses/by/4.0/")!,
            fallbackBundledPath: "Models/mosquito_amber.usdz",
            scaleToUnits: 0.15,
            hasBakedAnimation: false,
            category: "materials",
            tags: ["KHR_materials_iridescence"]
        ),
        SketchfabSlug(
            uid: "7a3a8a78a4d9292a4c3ad32c1ca3a3d4",
            displayName: "Glass Decanter",
            author: "KhronosGroup",
            licenseURL: URL(string: "https://creativecommons.org/licenses/by/4.0/")!,
            fallbackBundledPath: "Models/mosquito_amber.usdz",
            scaleToUnits: 0.35,
            hasBakedAnimation: false,
            category: "materials",
            tags: ["KHR_materials_transmission"]
        ),
        SketchfabSlug(
            uid: "ad32c1ca3a3d4f0db8e7a3a8a78a4d93",
            displayName: "Velvet Cushion",
            author: "KhronosGroup",
            licenseURL: URL(string: "https://creativecommons.org/licenses/by/4.0/")!,
            fallbackBundledPath: "Models/mosquito_amber.usdz",
            scaleToUnits: 0.40,
            hasBakedAnimation: false,
            category: "materials",
            tags: ["KHR_materials_sheen"]
        ),
    ]

    /// Lookup by Sketchfab uid (the primary key).
    static let byUID: [String: SketchfabSlug] = {
        Dictionary(uniqueKeysWithValues: all.map { ($0.uid, $0) })
    }()

    /// Lookup by category name. Never `nil` — returns an empty list for an
    /// unknown category.
    static let byCategory: [String: [SketchfabSlug]] = {
        Dictionary(grouping: all, by: { $0.category })
    }()

    /// Distinct categories present in the registry.
    static var categories: [String] { byCategory.keys.sorted() }

    /// Validate the registry — duplicates, malformed uids, missing licenses.
    ///
    /// Called at process start and by `SampleAssetsTests` so any regression
    /// in the curation list fails fast.
    static func validate() {
        let grouped = Dictionary(grouping: all, by: { $0.uid })
        let duplicates = grouped.filter { $0.value.count > 1 }.keys.sorted()
        precondition(
            duplicates.isEmpty,
            "Duplicate Sketchfab uids in SampleAssets.all: \(duplicates)"
        )
        for slug in all {
            let isHex = slug.uid.count == 32 && slug.uid.allSatisfy { ch in
                ("0"..."9").contains(ch) || ("a"..."f").contains(ch)
            }
            precondition(
                isHex,
                "SketchfabSlug.uid must be a 32-char lowercase-hex Sketchfab id"
                + " (uid='\(slug.uid)', displayName='\(slug.displayName)')"
            )
        }
    }
}
