import XCTest

// Test classes run on the main actor: their RealityKit node factories
// (`LightNode.directional`, `node.entity`, …) are `@MainActor`. (#1054)
@MainActor
final class SceneViewSwiftTests: XCTestCase {
    func testPackageLoads() {
        // Basic test to verify the package structure is valid
        XCTAssertTrue(true)
    }
}
