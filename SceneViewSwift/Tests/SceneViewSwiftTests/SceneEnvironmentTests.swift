import XCTest
@testable import SceneViewSwift

#if os(iOS) || os(visionOS)
// Test classes run on the main actor: their RealityKit node factories
// (`LightNode.directional`, `node.entity`, …) are `@MainActor`. (#1054)
@MainActor
final class SceneEnvironmentTests: XCTestCase {

    // MARK: - Presets

    func testAllPresetsCount() {
        XCTAssertEqual(SceneEnvironment.allPresets.count, 6)
    }

    func testPresetNames() {
        let names = SceneEnvironment.allPresets.map { $0.name }
        XCTAssertEqual(names, [
            "Studio", "Outdoor", "Sunset", "Night", "Warm", "Autumn"
        ])
    }

    func testPresetHDRResources() {
        XCTAssertEqual(SceneEnvironment.studio.hdrResource, "studio.hdr")
        XCTAssertEqual(SceneEnvironment.outdoor.hdrResource, "outdoor_cloudy.hdr")
        XCTAssertEqual(SceneEnvironment.sunset.hdrResource, "sunset.hdr")
        XCTAssertEqual(SceneEnvironment.night.hdrResource, "rooftop_night.hdr")
        XCTAssertEqual(SceneEnvironment.warm.hdrResource, "studio_warm.hdr")
        XCTAssertEqual(SceneEnvironment.autumn.hdrResource, "autumn_field.hdr")
    }

    func testPresetIntensities() {
        XCTAssertEqual(SceneEnvironment.studio.intensity, 1.0)
        XCTAssertEqual(SceneEnvironment.outdoor.intensity, 1.2)
        XCTAssertEqual(SceneEnvironment.sunset.intensity, 0.8)
        XCTAssertEqual(SceneEnvironment.night.intensity, 0.4)
        XCTAssertEqual(SceneEnvironment.warm.intensity, 1.0)
        XCTAssertEqual(SceneEnvironment.autumn.intensity, 0.9)
    }

    func testPresetShowsSkyboxByDefault() {
        for preset in SceneEnvironment.allPresets {
            XCTAssertTrue(preset.showSkybox, "\(preset.name) should show skybox by default")
        }
    }

    // MARK: - Custom Environment

    func testCustomEnvironment() {
        let custom = SceneEnvironment.custom(
            name: "My Env",
            hdrFile: "my_env.hdr",
            intensity: 0.7,
            showSkybox: false
        )
        XCTAssertEqual(custom.name, "My Env")
        XCTAssertEqual(custom.hdrResource, "my_env.hdr")
        XCTAssertEqual(custom.intensity, 0.7)
        XCTAssertFalse(custom.showSkybox)
    }

    func testInitWithoutHDR() {
        let env = SceneEnvironment(name: "Default")
        XCTAssertNil(env.hdrResource)
        XCTAssertEqual(env.intensity, 1.0)
        XCTAssertTrue(env.showSkybox)
    }

    // MARK: - Cache

    func testEnvironmentCacheStartsEmpty() {
        let cache = EnvironmentCache()
        XCTAssertNil(cache.get("nonexistent"))
    }

    func testEnvironmentCacheClear() {
        let cache = EnvironmentCache()
        // Just verify clear doesn't crash on empty cache
        cache.clear()
        XCTAssertNil(cache.get("anything"))
    }

    // MARK: - Mutability

    func testIntensityIsMutable() {
        var env = SceneEnvironment.studio
        env.intensity = 2.0
        XCTAssertEqual(env.intensity, 2.0)
    }

    func testShowSkyboxIsMutable() {
        var env = SceneEnvironment.studio
        env.showSkybox = false
        XCTAssertFalse(env.showSkybox)
    }
}
#endif
