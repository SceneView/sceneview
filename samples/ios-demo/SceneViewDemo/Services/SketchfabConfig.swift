import Foundation

/// Configuration for the Sketchfab Data API v3.
///
/// The API key is read from the `SKETCHFAB_API_KEY` environment variable so it
/// can be injected at build time via the Xcode scheme or a CI secret rather
/// than checked in.
///
/// TODO V1.1: move to backend proxy via mcp-gateway to avoid bundling key
/// directly in the iOS app binary. End-users should authenticate against the
/// proxy (which holds the master key server-side) so we don't ship a long-lived
/// token that can be extracted from `.ipa` files.
enum SketchfabConfig {
    /// Base URL of the Sketchfab Data API v3.
    static let baseURL = URL(string: "https://api.sketchfab.com/v3/")!

    /// API key read from the process environment.
    ///
    /// Returns `nil` when the variable is unset; callers should surface
    /// `SketchfabError.missingApiKey` in that case rather than firing
    /// unauthenticated requests.
    static var apiKey: String? {
        let value = ProcessInfo.processInfo.environment["SKETCHFAB_API_KEY"]
        guard let value, !value.isEmpty else { return nil }
        return value
    }

    /// Maximum cache size on disk, in bytes (500 MB).
    static let maxCacheBytes: Int64 = 500 * 1024 * 1024

    /// Subdirectory under `Caches/` where downloaded GLB files live.
    static let cacheDirectoryName = "sketchfab"
}
