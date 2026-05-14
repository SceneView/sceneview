import Foundation

/// Configuration for the Sketchfab Data API v3.
///
/// The API key is resolved at runtime from two sources, in this order:
///
/// 1. `Info.plist` key `SketchfabAPIKey`, which the Xcode build system
///    substitutes from the `SKETCHFAB_API_KEY` build setting at archive time.
///    This is the path used by both CI (`app-store.yml` / `ios.yml`) and
///    `xcodebuild archive` invocations — environment variables do *not*
///    survive `xcodebuild archive` into the shipped `.ipa`, so the
///    Info.plist substitution is the only reliable channel for App Store
///    builds. See issue #1157.
/// 2. `ProcessInfo.processInfo.environment["SKETCHFAB_API_KEY"]` — preserved
///    for the Xcode "Run" scheme dev workflow, where the scheme injects the
///    variable into the running process without rebuilding the bundle.
///
/// TODO V1.1: move to backend proxy via mcp-gateway to avoid bundling key
/// directly in the iOS app binary. End-users should authenticate against the
/// proxy (which holds the master key server-side) so we don't ship a long-lived
/// token that can be extracted from `.ipa` files.
enum SketchfabConfig {
    /// Base URL of the Sketchfab Data API v3.
    static let baseURL = URL(string: "https://api.sketchfab.com/v3/")!

    /// API key resolved from `Info.plist` (build-time substitution) with a
    /// fallback to the `SKETCHFAB_API_KEY` environment variable (Xcode dev
    /// scheme).
    ///
    /// Returns `nil` when neither source provides a non-empty key; callers
    /// should surface `SketchfabError.missingApiKey` in that case rather than
    /// firing unauthenticated requests.
    ///
    /// Guards against the unsubstituted `$(SKETCHFAB_API_KEY)` xcconfig
    /// literal — if the build setting is missing, Xcode leaves the placeholder
    /// in the Info.plist value verbatim and we must treat that as "key absent"
    /// rather than passing the literal string as a bearer token.
    static var apiKey: String? {
        if let fromPlist = Bundle.main.object(forInfoDictionaryKey: "SketchfabAPIKey") as? String,
           !fromPlist.isEmpty,
           fromPlist != "$(SKETCHFAB_API_KEY)" {
            return fromPlist
        }
        let fromEnv = ProcessInfo.processInfo.environment["SKETCHFAB_API_KEY"]
        guard let fromEnv, !fromEnv.isEmpty else { return nil }
        return fromEnv
    }

    /// Maximum cache size on disk, in bytes (500 MB).
    static let maxCacheBytes: Int64 = 500 * 1024 * 1024

    /// Subdirectory under `Caches/` where downloaded GLB files live.
    static let cacheDirectoryName = "sketchfab"
}
