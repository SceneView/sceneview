#if DEBUG
import XCTest
@testable import SceneViewDemo

/// Unit tests for `SketchfabService`.
///
/// Network-dependent tests are gated behind `SketchfabConfig.apiKey` (which
/// resolves from `Info.plist`'s `SketchfabAPIKey` placeholder first, then
/// falls back to the legacy `SKETCHFAB_API_KEY` env var — see
/// `SketchfabConfig.swift`). The CI iOS workflow now injects the secret as a
/// `xcodebuild` build setting, so this test actually executes on the SceneView
/// repo CI; it stays skipped on forks without the secret + on contributor
/// machines that haven't wired the env var in the Xcode scheme.
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
