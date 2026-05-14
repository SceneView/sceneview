#if DEBUG
import XCTest
@testable import SceneViewDemo

/// Unit tests for `SketchfabService`.
///
/// Network-dependent tests are gated behind `SketchfabConfig.apiKey`, which
/// resolves from either the `Info.plist` `SketchfabAPIKey` substitution
/// (release / archive builds) or the `SKETCHFAB_API_KEY` process environment
/// variable (Xcode dev scheme + CI test runs). When the key is present in CI
/// — both `ios.yml`'s demo build step and `app-store.yml`'s archive step now
/// inject the GitHub secret — the live `testSearchReturnsResults` round-trip
/// actually executes; without the secret (forks, fork PRs) it skips
/// gracefully. See issue #1157.
final class SketchfabServiceTests: XCTestCase {

    /// Offline: verify the URL builder produces the expected `/v3/search?...`.
    func testDownloadURLPath() async throws {
        let service = SketchfabService()
        let url = service.buildURL(
            path: "models/abc123/download",
            queryItems: []
        )
        XCTAssertEqual(
            url?.absoluteString,
            "https://api.sketchfab.com/v3/models/abc123/download"
        )

        let searchURL = service.buildURL(
            path: "search",
            queryItems: [
                URLQueryItem(name: "type", value: "models"),
                URLQueryItem(name: "q", value: "car"),
                URLQueryItem(name: "downloadable", value: "true"),
                URLQueryItem(name: "count", value: "24")
            ]
        )
        XCTAssertNotNil(searchURL)
        XCTAssertTrue(searchURL!.absoluteString.hasPrefix("https://api.sketchfab.com/v3/search?"))
        let query = searchURL!.query ?? ""
        XCTAssertTrue(query.contains("type=models"))
        XCTAssertTrue(query.contains("q=car"))
        XCTAssertTrue(query.contains("downloadable=true"))
        XCTAssertTrue(query.contains("count=24"))
    }

    /// Online: real round-trip. Skipped when the API key is not present.
    func testSearchReturnsResults() async throws {
        try XCTSkipIf(
            SketchfabConfig.apiKey == nil,
            "SKETCHFAB_API_KEY not set — skipping live network test"
        )
        let service = SketchfabService()
        let results = try await service.search(query: "car", limit: 5)
        XCTAssertGreaterThan(results.count, 0, "Expected at least one model for 'car'")
        if let first = results.first {
            XCTAssertFalse(first.uid.isEmpty)
            XCTAssertFalse(first.name.isEmpty)
        }
    }
}
#endif
