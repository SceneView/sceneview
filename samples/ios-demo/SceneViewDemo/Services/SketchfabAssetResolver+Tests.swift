#if DEBUG
import XCTest
@testable import SceneViewDemo

/// Unit tests for `SketchfabAssetResolver`.
///
/// Mirrors `SketchfabAssetResolverTest.kt` 1-for-1 (Stage 1 of #1152). All
/// tests are offline-only — the `downloader` closure is stubbed so we never
/// hit api.sketchfab.com.
final class SketchfabAssetResolverTests: XCTestCase {

    /// Each test gets its own temp directory so they can run in parallel
    /// without stomping on each other.
    private var tempDir: URL!

    override func setUp() async throws {
        try await super.setUp()
        tempDir = FileManager.default.temporaryDirectory.appendingPathComponent(
            "sketchfab-resolver-test-\(UUID().uuidString)",
            isDirectory: true
        )
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
    }

    override func tearDown() async throws {
        if let tempDir { try? FileManager.default.removeItem(at: tempDir) }
        try await super.tearDown()
    }

    private func cacheRoot(for resolver: SketchfabAssetResolver) async -> URL {
        try! await resolver.cacheRoot()
    }

    private var testSlug: SketchfabSlug {
        SampleAssets.gallery[0]
    }

    /// Build a resolver that caches into a per-test temp directory and uses
    /// the provided downloader / bundle provider / validator / api-key.
    private func makeResolver(
        downloader: @escaping @Sendable (String) async throws -> URL = { _ in
            throw SketchfabError.missingApiKey
        },
        bundleProvider: @escaping @Sendable (String) -> URL? = { _ in nil },
        boundsValidator: @escaping @Sendable (URL, SketchfabSlug) async throws -> Bool =
            SketchfabAssetResolver.acceptAll,
        apiKey: String? = nil,
        cacheBudgetBytes: Int64 = SketchfabAssetResolver.defaultCacheBudgetBytes
    ) -> SketchfabAssetResolver {
        // Each resolver writes to a temp dir by overriding the cache root via
        // a temp-scoped `cacheDirectoryName` whose root is the user caches dir.
        // Tests that need a fully isolated path use the `tempDir` for the
        // bundleProvider fallback file.
        let dirName = "sketchfab-test-\(UUID().uuidString)"
        return SketchfabAssetResolver(
            cacheDirectoryName: dirName,
            downloader: downloader,
            bundleProvider: bundleProvider,
            boundsValidator: boundsValidator,
            cacheBudgetBytes: cacheBudgetBytes,
            apiKeyProvider: { apiKey }
        )
    }

    /// Write a small fake bundled fallback to the temp dir and return a
    /// closure that returns its URL — used as the `bundleProvider` stub.
    private func makeFakeBundleProvider(bytes: Int = 64) -> @Sendable (String) -> URL? {
        let fileURL = tempDir.appendingPathComponent("bundled-fallback.usdz")
        FileManager.default.createFile(
            atPath: fileURL.path,
            contents: Data(count: bytes),
            attributes: nil
        )
        let captured = fileURL
        return { _ in captured }
    }

    // MARK: - 1. Cache-hit short-circuit

    func testResolveReturnsCachedFileWithoutDownloaderOnCacheHit() async throws {
        let resolver = makeResolver(
            downloader: { _ in XCTFail("downloader must not be called on cache hit"); return URL(fileURLWithPath: "/dev/null") }
        )
        // Pre-populate the cache directly.
        let cacheRoot = try await resolver.cacheRoot()
        let cachedURL = cacheRoot.appendingPathComponent("\(testSlug.uid).glb")
        try Data(count: 64).write(to: cachedURL)

        let resolved = try await resolver.resolve(testSlug)
        XCTAssertEqual(resolved.path, cachedURL.path)
    }

    // MARK: - 2. Cache miss + no API key → bundled fallback

    func testResolveFallsBackToBundledAssetWhenApiKeyAbsent() async throws {
        let provider = makeFakeBundleProvider(bytes: 64)
        let resolver = makeResolver(
            downloader: { _ in XCTFail("downloader must not be invoked without api key"); return URL(fileURLWithPath: "/dev/null") },
            bundleProvider: provider,
            apiKey: nil
        )
        let resolved = try await resolver.resolve(testSlug)
        XCTAssertTrue(resolved.lastPathComponent.hasPrefix("fallback-"),
                      "Expected fallback-prefixed file name, got \(resolved.lastPathComponent)")
        XCTAssertTrue(FileManager.default.fileExists(atPath: resolved.path))
    }

    // MARK: - 3. Cache miss + downloader succeeds → returned URL

    func testResolveInvokesDownloaderAndReturnsItsFileOnCacheMiss() async throws {
        actor Counter { var count = 0; func bump() { count += 1 } }
        let counter = Counter()
        // Need a captured URL we can write into and return from the stub.
        let resolverHolder = ResolverHolder()
        let resolver = makeResolver(
            downloader: { uid in
                await counter.bump()
                let root = try await resolverHolder.value!.cacheRoot()
                let dest = root.appendingPathComponent("\(uid).glb")
                try? FileManager.default.removeItem(at: dest)
                try Data(count: 1024).write(to: dest)
                return dest
            },
            apiKey: "test-key"
        )
        await resolverHolder.set(resolver)
        let resolved = try await resolver.resolve(testSlug)
        let calls = await counter.count
        XCTAssertEqual(calls, 1)
        XCTAssertEqual(resolved.lastPathComponent, "\(testSlug.uid).glb")
        let size = (try? FileManager.default.attributesOfItem(atPath: resolved.path)[.size] as? NSNumber)?.intValue ?? 0
        XCTAssertEqual(size, 1024)
    }

    // MARK: - 4. LRU eviction past the configured budget

    func testEvictLruDeletesOldestFilesUntilUnderBudget() async throws {
        let resolver = makeResolver(cacheBudgetBytes: 300 * 1024)
        let root = try await resolver.cacheRoot()
        // 5 files × 100 KB = 500 KB total.
        for i in 1...5 {
            let url = root.appendingPathComponent("file_\(i).glb")
            try Data(count: 100 * 1024).write(to: url)
            // Stagger modification dates so eviction order is deterministic.
            let date = Date(timeIntervalSince1970: TimeInterval(1_000_000 + i * 60))
            try FileManager.default.setAttributes([.modificationDate: date], ofItemAtPath: url.path)
        }
        await resolver.evictLruIfOverBudget(maxBytes: nil)
        let remaining = try FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: nil)
        XCTAssertEqual(remaining.count, 3)
        let names = Set(remaining.map { $0.lastPathComponent })
        XCTAssertFalse(names.contains("file_1.glb"))
        XCTAssertFalse(names.contains("file_2.glb"))
        XCTAssertTrue(names.contains("file_3.glb"))
        XCTAssertTrue(names.contains("file_4.glb"))
        XCTAssertTrue(names.contains("file_5.glb"))
    }

    func testEvictLruIsNoOpWhenUnderBudget() async throws {
        let resolver = makeResolver(cacheBudgetBytes: 1_000_000)
        let root = try await resolver.cacheRoot()
        let url = root.appendingPathComponent("only.glb")
        try Data(count: 100).write(to: url)
        await resolver.evictLruIfOverBudget()
        let remaining = try FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: nil)
        XCTAssertEqual(remaining.count, 1)
    }

    // MARK: - 5. License contract — build-time precondition

    func testSampleAssetsAllSlugsValidateLicense() {
        // Every slug shipped in `SampleAssets.all` must have already passed
        // its own `precondition`. We re-check here to catch hand-edits.
        for slug in SampleAssets.all {
            XCTAssertTrue(
                SketchfabSlug.validLicenses.contains(slug.license),
                "Slug \(slug.uid) ships an invalid license \(slug.license)"
            )
            XCTAssertFalse(slug.attribution.isEmpty,
                           "Slug \(slug.uid) missing attribution")
            XCTAssertTrue(slug.sketchfabPageURL.hasPrefix("https://sketchfab.com/"),
                          "Slug \(slug.uid) has non-sketchfab URL")
        }
    }

    func testSampleAssetsRegistryHasNoDuplicateUIDs() {
        let uids = SampleAssets.all.map { $0.uid }
        XCTAssertEqual(uids.count, Set(uids).count,
                       "Duplicate UID detected: \(uids)")
    }
}

/// Helper actor to wire up the test stub that needs the resolver itself to
/// know where the temp cache dir lives.
fileprivate actor ResolverHolder {
    var value: SketchfabAssetResolver?
    func set(_ value: SketchfabAssetResolver) { self.value = value }
}

#endif
