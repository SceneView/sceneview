import Foundation

/// Configuration for the Sketchfab Data API v3.
///
/// The API key is resolved in two stages:
///
/// 1. `Info.plist` — the canonical lookup. The `SketchfabAPIKey` entry is
///    declared with the `$(SKETCHFAB_API_KEY)` build-setting placeholder so
///    that `xcodebuild archive` substitutes the value into the shipped
///    `Info.plist` at build time (CI passes the secret as a `SKETCHFAB_API_KEY`
///    user-defined build setting — see `.github/workflows/app-store.yml` and
///    `.github/workflows/ios.yml`). This is the only path that works for
///    TestFlight + App Store builds because environment variables set in the
///    CI job don't survive `xcodebuild archive` into the `.ipa`.
/// 2. `ProcessInfo` — fallback for Xcode local development. The legacy
///    `SKETCHFAB_API_KEY` scheme env var still works when running under
///    Xcode's "Run" scheme so contributors don't have to edit `Info.plist`.
///
/// TODO V1.1: move to backend proxy via mcp-gateway to avoid bundling key
/// directly in the iOS app binary. End-users should authenticate against the
/// proxy (which holds the master key server-side) so we don't ship a long-lived
/// token that can be extracted from `.ipa` files.
enum SketchfabConfig {
    /// Base URL of the Sketchfab Data API v3.
    static let baseURL = URL(string: "https://api.sketchfab.com/v3/")!

    /// API key, resolved from `Info.plist` first then `ProcessInfo` fallback.
    ///
    /// Returns `nil` when neither source provides a non-empty value; callers
    /// should surface `SketchfabError.missingApiKey` in that case rather than
    /// firing unauthenticated requests.
    static var apiKey: String? {
        // Primary lookup: `Info.plist` (works for `xcodebuild archive` →
        // TestFlight + App Store builds).
        if let fromPlist = Bundle.main.object(forInfoDictionaryKey: "SketchfabAPIKey") as? String,
           !fromPlist.isEmpty,
           // Guard against an unsubstituted `$(SKETCHFAB_API_KEY)` xcconfig
           // token leaking through as a string literal — happens when the
           // build setting wasn't passed to `xcodebuild` (e.g. forks without
           // the secret, manual Archive without -PSKETCHFAB_API_KEY=...).
           fromPlist != "$(SKETCHFAB_API_KEY)" {
            return fromPlist
        }
        // Fallback: legacy `ProcessInfo` env var. Works under Xcode "Run"
        // schemes (Edit Scheme → Run → Arguments → Environment Variables)
        // but NOT for archived builds.
        let fromEnv = ProcessInfo.processInfo.environment["SKETCHFAB_API_KEY"]
        guard let fromEnv, !fromEnv.isEmpty else { return nil }
        return fromEnv
    }

    /// Maximum cache size on disk, in bytes (500 MB).
    static let maxCacheBytes: Int64 = 500 * 1024 * 1024

    /// Subdirectory under `Caches/` where downloaded GLB files live.
    static let cacheDirectoryName = "sketchfab"
}
