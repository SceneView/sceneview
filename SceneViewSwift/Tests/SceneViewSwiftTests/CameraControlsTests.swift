import XCTest
@testable import SceneViewSwift

#if os(iOS) || os(visionOS)
// Test classes run on the main actor: their RealityKit node factories
// (`LightNode.directional`, `node.entity`, …) are `@MainActor`. (#1054)
@MainActor
final class CameraControlsTests: XCTestCase {

    // MARK: - Initialization

    func testDefaultInit() {
        let controls = CameraControls()
        XCTAssertEqual(controls.azimuth, 0.0)
        XCTAssertEqual(controls.elevation, Float.pi / 6, accuracy: 0.001)
        // v4.4.0 BREAKING: orbitRadius default changed from 5.0 to 2.0 so
        // direct constructors of CameraControls see the same default the
        // SceneView uses internally (the old 5.0 was unreachable through
        // any public modifier, producing a misleading split-brain default).
        // minRadius default changed from 0.5 to 1.0 so pinch-in stops
        // before clipping into 1m-extent content under the new true-camera
        // motion (the old 0.5 was safe under the fake-orbit scale hack).
        XCTAssertEqual(controls.orbitRadius, 2.0)
        XCTAssertEqual(controls.minRadius, 1.0)
        XCTAssertEqual(controls.maxRadius, 50.0)
        XCTAssertEqual(controls.sensitivity, 0.005)
        XCTAssertFalse(controls.isAutoRotating)
    }

    func testInitWithMode() {
        let orbit = CameraControls(mode: .orbit)
        XCTAssertEqual(orbit.mode, .orbit)

        let pan = CameraControls(mode: .pan)
        XCTAssertEqual(pan.mode, .pan)

        let fp = CameraControls(mode: .firstPerson)
        XCTAssertEqual(fp.mode, .firstPerson)
    }

    // MARK: - Camera Position

    func testCameraPositionAtDefaults() {
        let controls = CameraControls()
        let pos = controls.cameraPosition()

        // At azimuth=0, elevation=pi/6, radius=5:
        // x = 5 * cos(pi/6) * sin(0) = 0
        // y = 5 * sin(pi/6) = 2.5
        // z = 5 * cos(pi/6) * cos(0) = 5 * 0.866 = 4.33
        XCTAssertEqual(pos.x, 0.0, accuracy: 0.01)
        XCTAssertEqual(pos.y, 2.5, accuracy: 0.01)
        XCTAssertEqual(pos.z, 4.33, accuracy: 0.01)
    }

    func testCameraPositionAtAzimuth90() {
        var controls = CameraControls()
        controls.azimuth = Float.pi / 2  // 90 degrees
        controls.elevation = 0

        let pos = controls.cameraPosition()
        // x = 5 * cos(0) * sin(pi/2) = 5
        // y = 0
        // z = 5 * cos(0) * cos(pi/2) = 0
        XCTAssertEqual(pos.x, 5.0, accuracy: 0.01)
        XCTAssertEqual(pos.y, 0.0, accuracy: 0.01)
        XCTAssertEqual(pos.z, 0.0, accuracy: 0.01)
    }

    func testCameraPositionWithTarget() {
        var controls = CameraControls()
        controls.target = SIMD3<Float>(1, 2, 3)
        controls.azimuth = 0
        controls.elevation = 0

        let pos = controls.cameraPosition()
        // x = 1 + 5 * 1 * 0 = 1
        // y = 2 + 5 * 0 = 2
        // z = 3 + 5 * 1 * 1 = 8
        XCTAssertEqual(pos.x, 1.0, accuracy: 0.01)
        XCTAssertEqual(pos.y, 2.0, accuracy: 0.01)
        XCTAssertEqual(pos.z, 8.0, accuracy: 0.01)
    }

    // MARK: - Drag Handling

    func testHandleDragUpdatesAzimuth() {
        var controls = CameraControls()
        let initialAzimuth = controls.azimuth

        controls.handleDrag(CGSize(width: 100, height: 0))

        // azimuth should decrease (negative direction for width)
        XCTAssertLessThan(controls.azimuth, initialAzimuth)
        XCTAssertEqual(
            controls.azimuth,
            initialAzimuth - 100 * controls.sensitivity,
            accuracy: 0.0001
        )
    }

    func testHandleDragUpdatesElevation() {
        var controls = CameraControls()
        let initialElevation = controls.elevation

        controls.handleDrag(CGSize(width: 0, height: 100))

        // elevation should increase for downward drag
        XCTAssertGreaterThan(controls.elevation, initialElevation)
    }

    func testElevationClampedToAvoidGimbalLock() {
        var controls = CameraControls()

        // Drag a huge amount upward
        controls.handleDrag(CGSize(width: 0, height: 100000))

        let maxElev = Float.pi / 2 - 0.087
        XCTAssertLessThanOrEqual(controls.elevation, maxElev)

        // Drag a huge amount downward
        controls.handleDrag(CGSize(width: 0, height: -200000))
        XCTAssertGreaterThanOrEqual(controls.elevation, -maxElev)
    }

    // MARK: - Pinch Handling

    func testHandlePinchZoomIn() {
        var controls = CameraControls()
        let initialRadius = controls.orbitRadius

        controls.handlePinch(2.0)  // Pinch out = zoom in

        XCTAssertLessThan(controls.orbitRadius, initialRadius)
        XCTAssertEqual(controls.orbitRadius, initialRadius / 2.0, accuracy: 0.01)
    }

    func testHandlePinchZoomOut() {
        var controls = CameraControls()
        let initialRadius = controls.orbitRadius

        controls.handlePinch(0.5)  // Pinch in = zoom out

        XCTAssertGreaterThan(controls.orbitRadius, initialRadius)
    }

    func testPinchClampedToMinRadius() {
        var controls = CameraControls()
        controls.minRadius = 1.0

        // Extreme zoom in
        controls.handlePinch(1000.0)

        XCTAssertGreaterThanOrEqual(controls.orbitRadius, controls.minRadius)
    }

    func testPinchClampedToMaxRadius() {
        var controls = CameraControls()
        controls.maxRadius = 10.0

        // Extreme zoom out
        controls.handlePinch(0.001)

        XCTAssertLessThanOrEqual(controls.orbitRadius, controls.maxRadius)
    }

    // MARK: - Inertia

    func testInertiaDecays() {
        var controls = CameraControls()
        controls.inertiaVelocity = CGSize(width: 100, height: 50)

        let active = controls.applyInertia()
        XCTAssertTrue(active)

        // Velocity should have decreased
        XCTAssertLessThan(abs(controls.inertiaVelocity.width), 100)
        XCTAssertLessThan(abs(controls.inertiaVelocity.height), 50)
    }

    func testInertiaStopsAtThreshold() {
        var controls = CameraControls()
        controls.inertiaVelocity = CGSize(width: 0.005, height: 0.005)

        let active = controls.applyInertia()
        XCTAssertFalse(active)
        XCTAssertEqual(controls.inertiaVelocity.width, 0)
        XCTAssertEqual(controls.inertiaVelocity.height, 0)
    }

    func testInertiaDampingFactor() {
        var controls = CameraControls()
        controls.inertiaDamping = 0.5
        controls.inertiaVelocity = CGSize(width: 10, height: 10)

        controls.applyInertia()

        XCTAssertEqual(controls.inertiaVelocity.width, 5.0, accuracy: 0.01)
        XCTAssertEqual(controls.inertiaVelocity.height, 5.0, accuracy: 0.01)
    }

    // MARK: - Auto Rotation

    func testAutoRotationDisabledByDefault() {
        var controls = CameraControls()
        let initialAzimuth = controls.azimuth

        controls.applyAutoRotation(dt: 1.0)

        XCTAssertEqual(controls.azimuth, initialAzimuth)
    }

    func testAutoRotationEnabled() {
        var controls = CameraControls()
        controls.isAutoRotating = true
        controls.autoRotateSpeed = 1.0
        let initialAzimuth = controls.azimuth

        controls.applyAutoRotation(dt: 0.5)

        XCTAssertEqual(controls.azimuth, initialAzimuth + 0.5, accuracy: 0.001)
    }

    // MARK: - Camera Transform Matrix

    func testCameraTransformIsValid4x4() {
        let controls = CameraControls()
        let transform = controls.cameraTransform()

        // Should be a valid 4x4 matrix (last row = [0, 0, 0, 1] is not
        // guaranteed for a view matrix built this way, but the last column
        // should have w=1)
        XCTAssertEqual(transform.columns.3.w, 1.0, accuracy: 0.001)
    }

    func testSceneRotationIsUnitQuaternion() {
        let controls = CameraControls()
        let rot = controls.sceneRotation()
        let length = sqrt(
            rot.real * rot.real +
            rot.imag.x * rot.imag.x +
            rot.imag.y * rot.imag.y +
            rot.imag.z * rot.imag.z
        )
        XCTAssertEqual(length, 1.0, accuracy: 0.001)
    }
}
#endif
