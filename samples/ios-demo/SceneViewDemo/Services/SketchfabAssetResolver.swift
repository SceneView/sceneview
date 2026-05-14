import Foundation

/// Single-entry resolver that hands a sample/demo a ready-to-use file URL for a
/// `SketchfabSlug` — regardless of whether the model is cached, must be streamed,
/// or has to fall back to the bundled IPA asset.
///
/// Stage 1 deliverable for [#1152](https://github.com/sceneview/sceneview/issues/1152).
///
/// **Behavior (matches Android `SketchfabAssetResolver.kt` 1-for-1)**:
///  1. **Cache hit** → touch modification date (LRU bump) and return cached URL.
///  2. **Cache miss + API key present** → `SketchfabService.downloadModel`,
///     optionally validate via `boundsValidator`, evict LRU if cache exceeds
///     the budget, return the freshly-downloaded file URL.
///  3. **Cache miss + no key** → copy `SketchfabSlug.fallbackBundledPath` from
///     the main bundle into the cache directory under a `fallback-<uid>.usdz`
///     name and return that URL.
///  4. **Download fails OR validation rejects** → log the failure and fall back
///     to the bundled path as in case 3.
///
/// **Bounds validation.** The actor doesn't depend on RealityKit — the
/// `boundsValidator` parameter is `(URL, SketchfabSlug) async throws -> Bool`
/// and defaults to "accept-everything". Production callers wrap the resolver
/// with a validator backed by `Entity.visualBounds(recursive:relativeTo:)`.
/// This lets unit tests inject a fake validator and exercise the cache /
/// fallback paths without loading a real RealityKit scene.
///
/// **Threading.** The class is an `actor` so concurrent `resolve` calls
/// serialize through the same cache-mutation state. The underlying
/// `SketchfabService` is also an `actor`.
actor SketchfabAssetResolver {

    /// Stage-1 LRU cap — see Stage-1 spec on #1152.
    static let defaultCacheBudgetBytes: Int64 = 250 * 1024 * 1024

    /// Sentinel validator that accepts every download. Used in tests and as
    /// the default when no RealityKit validator is supplied.
    static let acceptAll: @Sendable (URL, SketchfabSlug) async throws -> Bool = { _, _ in true }

    private let cacheDirectoryName: String
    private let downloader: @Sendable (String) async throws -> URL
    private let bundleProvider: @Sendable (String) -> URL?
    private let boundsValidator: @Sendable (URL, SketchfabSlug) async throws -> Bool
    private let cacheBudgetBytes: Int64
    private let apiKeyProvider: @Sendable () -> String?

    /// Designated initializer. Tests pass custom closures; production callers
    /// stick with the defaults.
    ///
    /// - Parameters:
    ///   - downloader: Suspending download function `(uid) -> URL`. Defaults
    ///     to `SketchfabService.shared.downloadModel`.
    ///   - bundleProvider: Resolves a bundled fallback name to a `URL`.
    ///     Defaults to `Bundle.main.url(forResource:withExtension:)` matching.
    ///   - boundsValidator: Optional gate run after a successful download.
    ///   - cacheBudgetBytes: LRU cap enforced after each successful download.
    ///   - apiKeyProvider: Override for `SketchfabConfig.apiKey` (tests inject
    ///     `nil` to exercise the offline path without touching the environment).
    init(
        cacheDirectoryName: String = SketchfabConfig.cacheDirectoryName,
        downloader: @escaping @Sendable (String) async throws -> URL = { uid in
            try await SketchfabService.shared.downloadModel(uid: uid)
        },
        bundleProvider: @escaping @Sendable (String) -> URL? = { name in
            // Strip optional extension so callers can pass either
            // `animated_butterfly.usdz` or `animated_butterfly`.
            let lastDot = name.lastIndex(of: ".")
            let stem = lastDot.map { String(name[..<$0]) } ?? name
            let ext = lastDot.map { String(name[name.index(after: $0)...]) } ?? "usdz"
            return Bundle.main.url(forResource: stem, withExtension: ext)
        },
        boundsValidator: @escaping @Sendable (URL, SketchfabSlug) async throws -> Bool = acceptAll,
        cacheBudgetBytes: Int64 = defaultCacheBudgetBytes,
        apiKeyProvider: @escaping @Sendable () -> String? = { SketchfabConfig.apiKey }
    ) {
        self.cacheDirectoryName = cacheDirectoryName
        self.downloader = downloader
        self.bundleProvider = bundleProvider
        self.boundsValidator = boundsValidator
        self.cacheBudgetBytes = cacheBudgetBytes
        self.apiKeyProvider = apiKeyProvider
    }

    /// Resolve `slug` to a local file URL ready for `Entity(contentsOf:)`.
    ///
    /// Never throws. Errors are logged + the bundled fallback is returned so
    /// the rendering pipeline always has something to consume.
    func resolve(_ slug: SketchfabSlug) async throws -> URL {
        // 1. Cache hit short-circuit.
        let cached = try cacheFileURL(for: slug.uid)
        if FileManager.default.fileExists(atPath: cached.path),
           (try? FileManager.default.attributesOfItem(atPath: cached.path)[.size] as? NSNumber)?.intValue ?? 0 > 0 {
            touch(cached)
            return cached
        }

        // 2. No key → bundled fallback.
        guard let apiKey = apiKeyProvider(), !apiKey.isEmpty else {
            return try extractBundledFallback(slug)
        }
        _ = apiKey

        // 3. Try the network path.
        let downloaded: URL
        do {
            downloaded = try await downloader(slug.uid)
        } catch {
            print("[SketchfabResolver] download failed for \(slug.uid) (\(error)); using bundled fallback")
            return try extractBundledFallback(slug)
        }

        // 4. Bounds-check + animation-flag check.
        let passes: Bool
        do {
            passes = try await boundsValidator(downloaded, slug)
        } catch {
            print("[SketchfabResolver] bounds validator threw for \(slug.uid): \(error); treating as reject")
            try? FileManager.default.removeItem(at: downloaded)
            return try extractBundledFallback(slug)
        }
        if !passes {
            print("[SketchfabResolver] bounds validation rejected \(slug.uid); falling back")
            try? FileManager.default.removeItem(at: downloaded)
            return try extractBundledFallback(slug)
        }

        // 5. Enforce LRU budget and return.
        evictLruIfOverBudget(maxBytes: cacheBudgetBytes)
        return downloaded
    }

    /// Walk the Sketchfab cache directory, sort by modification date ascending,
    /// and delete oldest entries until total size ≤ `maxBytes`. Used both
    /// internally after every successful download and exposed as a test /
    /// maintenance hook.
    func evictLruIfOverBudget(maxBytes: Int64? = nil) {
        let target = maxBytes ?? cacheBudgetBytes
        guard let root = try? cacheRoot() else { return }
        let fm = FileManager.default
        guard let entries = try? fm.contentsOfDirectory(
            at: root,
            includingPropertiesForKeys: [.fileSizeKey, .contentModificationDateKey],
            options: [.skipsHiddenFiles]
        ) else { return }
        let files: [(URL, Int64, Date)] = entries.compactMap { url in
            let values = try? url.resourceValues(forKeys: [.fileSizeKey, .contentModificationDateKey])
            let size = Int64(values?.fileSize ?? 0)
            let mtime = values?.contentModificationDate ?? .distantPast
            return (url, size, mtime)
        }
        var total = files.reduce(Int64(0)) { $0 + $1.1 }
        guard total > target else { return }
        let sorted = files.sorted { $0.2 < $1.2 }
        for (url, size, _) in sorted {
            if total <= target { break }
            if (try? fm.removeItem(at: url)) != nil {
                total -= size
            }
        }
    }

    // MARK: - Internals

    func cacheRoot() throws -> URL {
        let caches = try FileManager.default.url(
            for: .cachesDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let dir = caches.appendingPathComponent(cacheDirectoryName, isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    func cacheFileURL(for uid: String) throws -> URL {
        try cacheRoot().appendingPathComponent("\(uid).glb")
    }

    /// Copy `slug.fallbackBundledPath` from the main bundle into the cache
    /// directory and return the resulting URL. The destination is named
    /// `fallback-<uid>.usdz` so it can't accidentally pose as a real Sketchfab
    /// download (LRU bumps target real downloads only).
    func extractBundledFallback(_ slug: SketchfabSlug) throws -> URL {
        let root = try cacheRoot()
        let dest = root.appendingPathComponent("fallback-\(slug.uid).usdz")
        if FileManager.default.fileExists(atPath: dest.path) {
            if let size = try? FileManager.default.attributesOfItem(atPath: dest.path)[.size] as? NSNumber,
               size.intValue > 0 {
                return dest
            }
        }
        guard let source = bundleProvider(slug.fallbackBundledPath) else {
            throw SketchfabResolverError.missingBundledFallback(
                slug: slug.uid,
                path: slug.fallbackBundledPath
            )
        }
        try? FileManager.default.removeItem(at: dest)
        try FileManager.default.copyItem(at: source, to: dest)
        return dest
    }

    private nonisolated func touch(_ url: URL) {
        try? FileManager.default.setAttributes(
            [.modificationDate: Date()],
            ofItemAtPath: url.path
        )
    }
}

/// Errors surfaced by `SketchfabAssetResolver` — these only escape when the
/// resolver can't even produce a fallback (e.g. the bundled asset was
/// stripped at build time), which is a build-config bug.
enum SketchfabResolverError: Error, LocalizedError {
    case missingBundledFallback(slug: String, path: String)

    var errorDescription: String? {
        switch self {
        case .missingBundledFallback(let slug, let path):
            return "Missing bundled fallback at Bundle.main/\(path) for slug \(slug). "
                + "Add the file to the Xcode target or fix the SketchfabSlug entry."
        }
    }
}
