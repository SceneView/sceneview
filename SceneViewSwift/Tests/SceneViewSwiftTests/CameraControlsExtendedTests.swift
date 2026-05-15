import XCTest
@testable import SceneViewSwift

#if os(iOS) || os(macOS) || os(visionOS)
import RealityKit

// Test classes run on the main actor: their RealityKit node factories
// (`LightNode.directional`, `node.entity`, …) are `@MainActor`. (#1054)
@MainActor
final class CameraControlsExtendedTests: XCTestCase {

    // MARK: - Configurable limits

    func testCustomElevationLimits() {
        var controls = CameraControls(
            mode: .orbit,
            minElevation: -Float.pi / 6,
            maxElevation: Float.pi / 6
        )
        // Drag far down (positive height = positive elevation change)
        controls.handleDrag(CGSize(width: 0, height: 10000))
        XCTAssertLessThanOrEqual(controls.elevation, Float.pi / 6 + 0.01)
    }

    func testCustomRadiusLimits() {
        var controls = CameraControls(mode: .orbit, minRadius: 1.0, maxRadius: 10.0)
        controls.orbitRadius = 5.0

        // Pinch to zoom in a lot
        controls.handlePinch(100.0)
        XCTAssertGreaterThanOrEqual(controls.orbitRadius, 1.0)

        // Pinch to zoom out a lot
        controls.handlePinch(0.001)
        XCTAssertLessThanOrEqual(controls.orbitRadius, 10.0)
    }

    func testAzimuthLimits() {
        var controls = CameraControls(mode: .orbit)
        controls.minAzimuth = -Float.pi / 4
        controls.maxAzimuth = Float.pi / 4

        // Drag far right
        controls.handleDrag(CGSize(width: -100000, height: 0))
        XCTAssertLessThanOrEqual(controls.azimuth, Float.pi / 4 + 0.01)
    }

    // MARK: - Pan mode

    func testPanModeDrag() {
        var controls = CameraControls(mode: .pan)
        let initialTarget = controls.target
        controls.handleDrag(CGSize(width: 100, height: 100))
        // Target should have moved
        XCTAssertNotEqual(controls.target.x, initialTarget.x)
    }

    // MARK: - First person mode

    func testFirstPersonModeDrag() {
        var controls = CameraControls(mode: .firstPerson)
        let initialAzimuth = controls.azimuth
        controls.handleDrag(CGSize(width: 100, height: 0))
        XCTAssertNotEqual(controls.azimuth, initialAzimuth)
    }

    // MARK: - Enabled flag

    func testDisabledControlsIgnoreDrag() {
        var controls = CameraControls(mode: .orbit)
        controls.isEnabled = false
        let initialAzimuth = controls.azimuth
        controls.handleDrag(CGSize(width: 100, height: 0))
        XCTAssertEqual(controls.azimuth, initialAzimuth)
    }

    // MARK: - Builder methods

    func testWithTarget() {
        let controls = CameraControls()
            .withTarget([1, 2, 3])
        XCTAssertEqual(controls.target.x, 1.0, accuracy: 0.001)
    }

    func testWithRadius() {
        let controls = CameraControls()
            .withRadius(10.0)
        XCTAssertEqual(controls.orbitRadius, 10.0, accuracy: 0.001)
    }

    func testWithAzimuthLimits() {
        let controls = CameraControls()
            .withAzimuthLimits(min: -1.0, max: 1.0)
        XCTAssertEqual(controls.minAzimuth ?? 0, -1.0, accuracy: 0.001)
        XCTAssertEqual(controls.maxAzimuth ?? 0, 1.0, accuracy: 0.001)
    }

    func testWithElevationLimits() {
        let controls = CameraControls()
            .withElevationLimits(min: -0.5, max: 0.5)
        XCTAssertEqual(controls.minElevation, -0.5, accuracy: 0.001)
        XCTAssertEqual(controls.maxElevation, 0.5, accuracy: 0.001)
    }

    // MARK: - Sensitivity

    func testCustomSensitivity() {
        let controls = CameraControls(mode: .orbit, sensitivity: 0.01)
        XCTAssertEqual(controls.sensitivity, 0.01, accuracy: 0.0001)
    }

    // MARK: - New properties defaults

    func testNewPropertiesDefaults() {
        let controls = CameraControls()
        XCTAssertNil(controls.minAzimuth)
        XCTAssertNil(controls.maxAzimuth)
        XCTAssertTrue(controls.isEnabled)
        XCTAssertEqual(controls.panSpeed, 0.01, accuracy: 0.001)
        XCTAssertEqual(controls.moveSpeed, 0.1, accuracy: 0.001)
    }
}

#endif // os(iOS) || os(macOS) || os(visionOS)
