import Foundation

/// A single streamable Sketchfab asset surfaced to a sample/demo.
///
/// Stage-1 contract for [#1152](https://github.com/sceneview/sceneview/issues/1152) — the demo
/// code never references a Sketchfab UID directly. It looks up a `SketchfabSlug` in
/// `SampleAssets`, hands it to `SketchfabAssetResolver.resolve`, and renders the returned
/// file URL via the normal `Entity(contentsOf:)` flow.
///
/// **License contract.** The `license` field MUST equal one of:
///  - `"CC-BY-4.0"`
///  - `"CC-BY-3.0"`
///  - `"CC0-1.0"` (or `"CC-0"` legacy form — both are accepted)
///
/// Anything else triggers a `fatalError` inside `SampleAssets`, failing the
/// build at startup so an un-credited model can never reach the App Store
/// binary. See `validLicenses`.
struct SketchfabSlug: Equatable, Hashable {
    /// The Sketchfab model UID (32-char hex). Used to call `SketchfabService.downloadModel`.
    let uid: String
    /// Name of the bundled fallback asset under `SceneViewDemo/Models/` (e.g. `"animated_dragon.usdz"`).
    /// The resolver copies this file to the cache directory when the network path is unavailable
    /// (no key, download error, bounds reject).
    let fallbackBundledPath: String
    /// Target world-space side length (metres) of the longest axis after loading. Sketchfab UIDs
    /// ship at wildly different scales — this is what the demo uses to normalize.
    let scaleToUnits: Float
    /// `true` if the source is expected to ship at least one baked animation. The resolver
    /// rejects models whose actual animation count doesn't agree, falling back to the bundled
    /// asset so an animated demo never silently renders a static T-pose.
    let hasBakedAnimation: Bool
    /// SPDX-ish license tag (see `validLicenses`).
    let license: String
    /// Human-readable "Author Name" string surfaced in the About → Credits sheet (Stage 3).
    /// Mandatory by CC-BY.
    let attribution: String
    /// The Sketchfab model page URL — surfaced in the Credits sheet as the link to the
    /// original work. Mandatory by CC-BY.
    let sketchfabPageURL: String

    /// The only license strings accepted by the build.
    ///
    /// - `CC-BY-4.0` / `CC-BY-3.0` require visible author + license + link credit
    ///   in the About → Credits sheet.
    /// - `CC0-1.0` / `CC-0` are public-domain dedications and may be shown without
    ///   credit, but the credit row is still surfaced as a courtesy.
    static let validLicenses: Set<String> = [
        "CC-BY-4.0",
        "CC-BY-3.0",
        "CC0-1.0",
        "CC-0",
    ]

    init(
        uid: String,
        fallbackBundledPath: String,
        scaleToUnits: Float,
        hasBakedAnimation: Bool,
        license: String,
        attribution: String,
        sketchfabPageURL: String
    ) {
        precondition(!uid.isEmpty, "SketchfabSlug.uid must not be blank")
        precondition(
            !fallbackBundledPath.isEmpty,
            "SketchfabSlug(\(uid)).fallbackBundledPath must not be blank — every slug needs a "
                + "bundled fallback so cold-cache / no-key launches still render something."
        )
        precondition(scaleToUnits > 0, "SketchfabSlug(\(uid)).scaleToUnits must be positive")
        precondition(
            Self.validLicenses.contains(license),
            "SketchfabSlug(\(uid)) ships license=\"\(license)\" — only CC-BY / CC-0 are accepted "
                + "for SceneView demo bundling. Allowed: \(Self.validLicenses)"
        )
        precondition(
            !attribution.isEmpty,
            "SketchfabSlug(\(uid)).attribution must not be blank — CC-BY requires author credit."
        )
        precondition(
            sketchfabPageURL.hasPrefix("https://sketchfab.com/"),
            "SketchfabSlug(\(uid)).sketchfabPageURL must point at sketchfab.com (got \(sketchfabPageURL))"
        )
        self.uid = uid
        self.fallbackBundledPath = fallbackBundledPath
        self.scaleToUnits = scaleToUnits
        self.hasBakedAnimation = hasBakedAnimation
        self.license = license
        self.attribution = attribution
        self.sketchfabPageURL = sketchfabPageURL
    }
}

/// Curated, build-time-validated registry of Sketchfab assets used by SceneView demos.
///
/// **Stage 1 scope.** This file only registers slugs and groups them by category.
/// No demo migrates onto this registry yet — that's [Stage 2 of #1152](https://github.com/sceneview/sceneview/issues/1152).
///
/// **Sources of truth.** Every UID in this file is cross-referenced with
/// `docs/docs/free-3d-models.md`, which lists publicly known CC-BY models on
/// api.sketchfab.com. Never add a UID here without a row in that doc.
///
/// **Categories.** Mirrors the planned Stage-2 demo migrations: `solarSystem`, `gallery`,
/// `animation`, `arPlacement`, `physics`, `materials`. Android ships the SAME categories
/// with the SAME UIDs in the SAME order — see `SampleAssets.kt`.
enum SampleAssets {

    // MARK: - solarSystem
    // OrbitalARDemo target — animated planets / creatures that orbit a star.
    // CURATED: 2 confirmed UIDs from docs/docs/free-3d-models.md.
    // TODO: expand to 5-7 items in Stage 2 — needs verified animated planet UIDs.
    static let solarSystem: [SketchfabSlug] = [
        SketchfabSlug(
            uid: "27db6f519be14199b22688fad09a4b43",
            fallbackBundledPath: "animated_dragon.usdz",
            scaleToUnits: 1.0,
            hasBakedAnimation: true,
            license: "CC-BY-4.0",
            attribution: "BlackProject",
            sketchfabPageURL: "https://sketchfab.com/3d-models/planet-earth-27db6f519be14199b22688fad09a4b43"
        ),
        SketchfabSlug(
            uid: "23223f2e898945dbbb6e10b838a70c04",
            fallbackBundledPath: "ship_in_clouds.usdz",
            scaleToUnits: 1.0,
            hasBakedAnimation: false,
            license: "CC-BY-4.0",
            attribution: "Jeremy Faivre",
            sketchfabPageURL: "https://sketchfab.com/3d-models/earth-3d-globe-23223f2e898945dbbb6e10b838a70c04"
        ),
        // TODO: curate before Stage 2 — need 4-6 more animated themed planets
        // (butterfly, hummingbird, bee, fish, dolphin) from sketchfab `animated=true`
        // search with verified CC-BY licenses. See plan_sketchfab_streaming_samples.md.
    ]

    // MARK: - gallery
    // SceneGalleryDemo target — themed bundles ("Vehicles", "Furniture", "Tech").
    // CURATED: confirmed from docs/docs/free-3d-models.md.
    static let gallery: [SketchfabSlug] = [
        SketchfabSlug(
            uid: "5ef9b845aaf44203b6d04e2c677e444f",
            fallbackBundledPath: "tesla_cybertruck.usdz",
            scaleToUnits: 2.0,
            hasBakedAnimation: false,
            license: "CC-BY-4.0",
            attribution: "Ameer Studio",
            sketchfabPageURL: "https://sketchfab.com/3d-models/tesla-2018-model-3-5ef9b845aaf44203b6d04e2c677e444f"
        ),
        SketchfabSlug(
            uid: "1fbf29e297bd4a17ac39a00a378441d8",
            fallbackBundledPath: "tesla_cybertruck.usdz",
            scaleToUnits: 2.0,
            hasBakedAnimation: false,
            license: "CC-BY-4.0",
            attribution: "metarex.4d",
            sketchfabPageURL: "https://sketchfab.com/3d-models/tesla-roadster-2020-1fbf29e297bd4a17ac39a00a378441d8"
        ),
        SketchfabSlug(
            uid: "f12e67159f75486bb21213e573520612",
            fallbackBundledPath: "tesla_cybertruck.usdz",
            scaleToUnits: 2.0,
            hasBakedAnimation: false,
            license: "CC-BY-4.0",
            attribution: "Lexyc16",
            sketchfabPageURL: "https://sketchfab.com/3d-models/tesla-cybertruck-f12e67159f75486bb21213e573520612"
        ),
        SketchfabSlug(
            uid: "41a071ae12794b668502f58d1e0fd1a3",
            fallbackBundledPath: "nintendo_switch.usdz",
            scaleToUnits: 0.16,
            hasBakedAnimation: false,
            license: "CC-BY-4.0",
            attribution: "MajdyModels",
            sketchfabPageURL: "https://sketchfab.com/3d-models/iphone-16-pro-max-41a071ae12794b668502f58d1e0fd1a3"
        ),
        SketchfabSlug(
            uid: "9e045e469d514fea9dda2ccd161f5fa3",
            fallbackBundledPath: "nintendo_switch.usdz",
            scaleToUnits: 0.16,
            hasBakedAnimation: false,
            license: "CC-BY-4.0",
            attribution: "Sketcher",
            sketchfabPageURL: "https://sketchfab.com/3d-models/iphone-15-pro-9e045e469d514fea9dda2ccd161f5fa3"
        ),
    ]

    // MARK: - animation
    // AnimationDemo target — characters with baked skinned animations.
    // CURATED: 1 confirmed UID from docs/docs/free-3d-models.md.
    // TODO: expand to 5 items in Stage 2 — needs verified animated character UIDs.
    static let animation: [SketchfabSlug] = [
        SketchfabSlug(
            uid: "245a8bcfddf44122b1f2e8dfa883c544",
            fallbackBundledPath: "animated_butterfly.usdz",
            scaleToUnits: 1.8,
            hasBakedAnimation: true,
            license: "CC-BY-4.0",
            attribution: "loganbro",
            sketchfabPageURL: "https://sketchfab.com/3d-models/walking-character-245a8bcfddf44122b1f2e8dfa883c544"
        ),
        // TODO: curate before Stage 2 — need 4 more verified animated CC-BY characters.
    ]

    // MARK: - arPlacement
    // ARPlacementDemo / ARInstantPlacementDemo target — furniture + props.
    // CURATED: confirmed from docs/docs/free-3d-models.md (furniture row).
    static let arPlacement: [SketchfabSlug] = [
        SketchfabSlug(
            uid: "5b7d9dae3db24fd499cd2d28283daa3f",
            fallbackBundledPath: "retro_piano.usdz",
            scaleToUnits: 1.2,
            hasBakedAnimation: false,
            license: "CC-BY-4.0",
            attribution: "harshaog07",
            sketchfabPageURL: "https://sketchfab.com/3d-models/leather-furniture-set-sofa-5b7d9dae3db24fd499cd2d28283daa3f"
        ),
        SketchfabSlug(
            uid: "f3622d9edcc94f1aa4b0bd95f1d5cda2",
            fallbackBundledPath: "retro_piano.usdz",
            scaleToUnits: 1.5,
            hasBakedAnimation: false,
            license: "CC-BY-4.0",
            attribution: "Blaz Mraz",
            sketchfabPageURL: "https://sketchfab.com/3d-models/couchsofa-set-f3622d9edcc94f1aa4b0bd95f1d5cda2"
        ),
        SketchfabSlug(
            uid: "3220ea6654e7483d8aa101a024cad81a",
            fallbackBundledPath: "retro_piano.usdz",
            scaleToUnits: 0.9,
            hasBakedAnimation: false,
            license: "CC-BY-4.0",
            attribution: "Urielgc",
            sketchfabPageURL: "https://sketchfab.com/3d-models/modern-black-chair-3220ea6654e7483d8aa101a024cad81a"
        ),
    ]

    // MARK: - physics
    // PhysicsDemo target — small dense props for falling-object simulations.
    // CURATED: 2 confirmed UIDs from docs/docs/free-3d-models.md (watch / sneaker).
    // TODO: expand to 5-7 items in Stage 2 — needs verified physics-friendly UIDs.
    static let physics: [SketchfabSlug] = [
        SketchfabSlug(
            uid: "693e3e5fc41b42c5af35bc965ab70014",
            fallbackBundledPath: "game_boy_classic.usdz",
            scaleToUnits: 0.05,
            hasBakedAnimation: false,
            license: "CC-BY-4.0",
            attribution: "DevPoly3D",
            sketchfabPageURL: "https://sketchfab.com/3d-models/watch-luxury-wristwatch-3d-model-693e3e5fc41b42c5af35bc965ab70014"
        ),
        SketchfabSlug(
            uid: "9a0bbbb955384ac68486c6cb3768ccb3",
            fallbackBundledPath: "nike_air_jordan.usdz",
            scaleToUnits: 0.3,
            hasBakedAnimation: false,
            license: "CC-BY-4.0",
            attribution: "Taohid Animation",
            sketchfabPageURL: "https://sketchfab.com/3d-models/shoe-model-realistic-3d-sneaker-9a0bbbb955384ac68486c6cb3768ccb3"
        ),
    ]

    // MARK: - materials
    // MaterialsDemo target — KHR_materials_* showcase (sheen, transmission, …).
    // CURATED: 1 confirmed UID from docs/docs/free-3d-models.md (Golden Watch, PBR-rich).
    // TODO: expand to 5 items in Stage 2 — needs verified KHR_materials_* showcase UIDs.
    static let materials: [SketchfabSlug] = [
        SketchfabSlug(
            uid: "2e53321ad51c41c2b16055437e544d10",
            fallbackBundledPath: "game_boy_classic.usdz",
            scaleToUnits: 0.05,
            hasBakedAnimation: false,
            license: "CC-BY-4.0",
            attribution: "Mehdi Belhous",
            sketchfabPageURL: "https://sketchfab.com/3d-models/golden-watch-2e53321ad51c41c2b16055437e544d10"
        ),
        // TODO: curate before Stage 2 — need 4 more verified PBR-rich CC-BY models.
    ]

    /// Flat list of every registered slug, for whole-app prefetch + Credits-sheet rendering.
    static let all: [SketchfabSlug] =
        solarSystem + gallery + animation + arPlacement + physics + materials

    /// Categories surfaced to UI / docs. Same shape on Android — kept identical so
    /// a Stage-2 PR can migrate the demo once and the resolver returns equivalent
    /// assets on both stores.
    static let byCategory: [(String, [SketchfabSlug])] = [
        ("solarSystem", solarSystem),
        ("gallery", gallery),
        ("animation", animation),
        ("arPlacement", arPlacement),
        ("physics", physics),
        ("materials", materials),
    ]

    /// Build-time validation: assert there are no duplicate UIDs in the registry.
    /// Called from `SceneViewDemoApp.init` so failures surface immediately on launch.
    static func validate() {
        var seen = Set<String>()
        for slug in all {
            precondition(
                !seen.contains(slug.uid),
                "SampleAssets: duplicate SketchfabSlug uid=\(slug.uid) in registry."
            )
            seen.insert(slug.uid)
        }
    }
}
