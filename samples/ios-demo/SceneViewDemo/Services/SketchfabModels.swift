import Foundation

/// A Sketchfab model entry returned by `/v3/search` and `/v3/models`.
struct SketchfabModel: Codable, Identifiable, Hashable {
    let uid: String
    let name: String
    let description: String?
    let thumbnails: SketchfabThumbnails
    let viewerUrl: String
    let downloadable: Bool
    let tags: [SketchfabTag]?

    var id: String { uid }

    enum CodingKeys: String, CodingKey {
        case uid, name, description, thumbnails, downloadable, tags
        case viewerUrl
    }
}

/// Wrapper around the `images` array returned by Sketchfab for each model.
struct SketchfabThumbnails: Codable, Hashable {
    let images: [SketchfabThumbnail]
}

/// A single thumbnail at a specific resolution.
struct SketchfabThumbnail: Codable, Hashable {
    let url: String
    let width: Int
    let height: Int
}

/// A tag attached to a model. Sketchfab returns more fields (slug, uri, …) but
/// only `name` is needed for filtering/display in the demo app.
struct SketchfabTag: Codable, Hashable {
    let name: String
}

/// Paginated search/list response.
struct SketchfabSearchResponse: Codable {
    let results: [SketchfabModel]
    let next: String?
    let previous: String?
}

/// Response of `GET /v3/models/{uid}/download`.
///
/// Sketchfab returns up to three format entries (`gltf`, `glb`, `usdz`); all
/// are optional because availability depends on the model. The iOS demo
/// prefers `glb` (single binary, no companion files) and falls back to `gltf`.
struct SketchfabDownloadResponse: Codable {
    let gltf: SketchfabDownloadUrl?
    let glb: SketchfabDownloadUrl?
    let usdz: SketchfabDownloadUrl?

    /// Best format for SceneView consumption: prefer GLB (self-contained),
    /// fall back to glTF, then USDZ as a last resort.
    var preferred: SketchfabDownloadUrl? {
        glb ?? gltf ?? usdz
    }
}

/// A signed download URL with its size and expiration timestamp (epoch seconds).
struct SketchfabDownloadUrl: Codable, Hashable {
    let url: String
    let size: Int
    let expires: Int
}
