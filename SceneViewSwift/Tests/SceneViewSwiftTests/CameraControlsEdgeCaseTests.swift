import XCTest
@testable import SceneViewSwift

#if os(iOS) || os(visionOS)
// Test classes run on the main actor: their RealityKit node factories
// (`LightNode.directional`, `node.entity`, …) are `@MainActor`. (#1054)
@MainActor
final class CameraControlsEdgeCaseTests: XCTestCase {

    // MARK: - Rapid successive drags

    func testRapidDragAccumulation() {
        var controls = CameraControls()
        let startAzimuth = controls.azimuth

        // 100 small drags should accumulate smoothly
        for _ in 0..<100 {
            controls.handleDrag(CGSize(width: 1, height: 0))
        }

        let expectedAzimuth = startAzimuth - 100 * controls.sensitivity
        XCTAssertEqual(controls.azimuth, expectedAzimuth, accuracy: 0.001)
    }

    // MARK: - Zero-length drag

    func testZeroDragNoChange() {
        var controls = CameraControls()
        let startAzimuth = controls.azimuth
        let startElevation = controls.elevation

        controls.handleDrag(.zero)

        XCTAssertEqual(controls.azimuth, startAzimuth)
        XCTAssertEqual(controls.elevation, startElevation)
    }

    // MARK: - Pinch at boundaries

    func testPinchAtMinRadius() {
        var controls = CameraControls()
        controls.orbitRadius = controls.minRadius

        // Should not go below minimum
        controls.handlePinch(10.0)
        XCTAssertGreaterThanOrEqual(controls.orbitRadius, controls.minRadius)
    }

    func testPinchAtMaxRadius() {
        var controls = CameraControls()
        controls.orbitRadius = controls.maxRadius

        // Should not go above maximum
        controls.handlePinch(0.001)
        XCTAssertLessThanOrEqual(controls.orbitRadius, controls.maxRadius)
    }

    func testPinchScaleOfOneNoChange() {
        var controls = CameraControls()
        let startRadius = controls.orbitRadius

        controls.handlePinch(1.0)

        XCTAssertEqual(controls.orbitRadius, startRadius, accuracy: 0.001)
    }

    // MARK: - Full rotation

    func testFullRotationWrapsAround() {
        var controls = CameraControls()

        // Rotate 360 degrees
        let steps = 1000
        let totalWidth = Float(2 * Float.pi) / controls.sensitivity
        for _ in 0..<steps {
            controls.handleDrag(CGSize(
                width: Double(totalWidth) / Double(steps),
                height: 0
            ))
        }

        // After full rotation, azimuth should be near -2*pi
        // (not clamped, just accumulated)
        XCTAssertEqual(controls.azimuth, -2 * .pi, accuracy: 0.1)
    }

    // MARK: - Camera position at extremes

    func testCameraPositionTopDown() {
        var controls = CameraControls()
        controls.elevation = Float.pi / 2 - 0.087  // near max elevation
        controls.orbitRadius = 10.0

        let pos = controls.cameraPosition()
        // Y should be close to radius (looking straight down)
        XCTAssertGreaterThan(pos.y, 9.0)
    }

    func testCameraPositionWithZeroRadius() {
        var controls = CameraControls()
        controls.orbitRadius = 0.0

        let pos = controls.cameraPosition()
        // Camera should be at target
        XCTAssertEqual(pos.x, controls.target.x, accuracy: 0.01)
        XCTAssertEqual(pos.y, controls.target.y, accuracy: 0.01)
        XCTAssertEqual(pos.z, controls.target.z, accuracy: 0.01)
    }

    // MARK: - Inertia edge cases

    func testInertiaWithZeroVelocity() {
        var controls = CameraControls()
        controls.inertiaVelocity = .zero

        let active = controls.applyInertia()
        XCTAssertFalse(active)
    }

    func testInertiaConvergesToZero() {
        var controls = CameraControls()
        controls.inertiaVelocity = CGSize(width: 1000, height: 1000)
        controls.inertiaDamping = 0.5

        // Apply many iterations — should converge to zero
        for _ in 0..<100 {
            controls.applyInertia()
        }

        XCTAssertEqual(controls.inertiaVelocity.width, 0, accuracy: 0.01)
        XCTAssertEqual(controls.inertiaVelocity.height, 0, accuracy: 0.01)
    }

    // MARK: - Custom parameters

    func testCustomSensitivity() {
        var controls = CameraControls()
        controls.sensitivity = 0.01  // 2x default

        let startAzimuth = controls.azimuth
        controls.handleDrag(CGSize(width: 100, height: 0))

        XCTAssertEqual(
            controls.azimuth,
            startAzimuth - 100 * 0.01,
            accuracy: 0.0001
        )
    }

    func testCustomRadiusBounds() {
        var controls = CameraControls()
        controls.minRadius = 2.0
        controls.maxRadius = 3.0
        controls.orbitRadius = 2.5

        // Zoom in past min
        controls.handlePinch(100.0)
        XCTAssertEqual(controls.orbitRadius, 2.0, accuracy: 0.01)

        // Zoom out past max
        controls.handlePinch(0.001)
        XCTAssertEqual(controls.orbitRadius, 3.0, accuracy: 0.01)
    }
}
#endif
