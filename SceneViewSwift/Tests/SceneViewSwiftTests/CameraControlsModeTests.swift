import XCTest
@testable import SceneViewSwift

#if os(iOS) || os(visionOS) || os(macOS)
/// Pins per-mode `CameraControls` behaviour — closes #1034.
///
/// Mirrors the per-mode handler split from Android
/// `CameraGestureDetector` (ORBIT / PAN / FREE_FLIGHT). Each test asserts
/// that swapping `mode` redirects the drag / pinch math to the right
/// channel without bleeding into the orbit defaults.
final class CameraControlsModeTests: XCTestCase {

    // MARK: - drag math is mode-aware

    func testDragOrbitMode_updatesAzimuthAndElevation_butNotTarget() {
        var c = CameraControls(mode: .orbit)
        let initialTarget = c.target
        let initialAz = c.azimuth
        let initialEl = c.elevation

        c.handleDrag(CGSize(width: 100, height: 50))

        XCTAssertNotEqual(c.azimuth, initialAz, "orbit drag must rotate yaw")
        XCTAssertNotEqual(c.elevation, initialEl, "orbit drag must rotate pitch")
        XCTAssertEqual(c.target, initialTarget, "orbit drag must NOT translate target")
    }

    func testDragPanMode_translatesTarget_butNotAzimuthOrElevation() {
        var c = CameraControls(mode: .pan)
        let initialAz = c.azimuth
        let initialEl = c.elevation
        let initialTarget = c.target

        c.handleDrag(CGSize(width: 100, height: 50))

        XCTAssertEqual(c.azimuth, initialAz, "pan drag must NOT rotate yaw")
        XCTAssertEqual(c.elevation, initialEl, "pan drag must NOT rotate pitch")
        XCTAssertNotEqual(c.target, initialTarget, "pan drag must translate target")
        // panSpeed=0.01, width=100 ⇒ ~1 unit X; height=50 (screen-down ⇒ -y world)
        XCTAssertEqual(c.target.x, initialTarget.x + 1.0, accuracy: 0.01)
        XCTAssertEqual(c.target.y, initialTarget.y - 0.5, accuracy: 0.01)
    }

    func testDragFirstPersonMode_updatesAzimuthAndElevation_butNotTarget() {
        var c = CameraControls(mode: .firstPerson)
        let initialTarget = c.target
        let initialAz = c.azimuth

        c.handleDrag(CGSize(width: 200, height: 0))

        XCTAssertNotEqual(c.azimuth, initialAz, "firstPerson drag must rotate yaw")
        XCTAssertEqual(c.target, initialTarget, "firstPerson drag must NOT translate target")
    }

    // MARK: - pinch math is mode-aware

    func testPinchOrbitMode_scalesOrbitRadius_butNotFov() {
        var c = CameraControls(mode: .orbit)
        let initialFov = c.fov
        let initialRadius = c.orbitRadius

        c.handlePinch(2.0)

        XCTAssertLessThan(c.orbitRadius, initialRadius, "orbit pinch must dolly radius")
        XCTAssertEqual(c.fov, initialFov, "orbit pinch must NOT mutate FOV")
    }

    func testPinchPanMode_scalesOrbitRadius_butNotFov() {
        var c = CameraControls(mode: .pan)
        let initialFov = c.fov
        let initialRadius = c.orbitRadius

        c.handlePinch(2.0)

        XCTAssertLessThan(c.orbitRadius, initialRadius, "pan pinch must dolly radius")
        XCTAssertEqual(c.fov, initialFov, "pan pinch must NOT mutate FOV")
    }

    func testPinchFirstPersonMode_scalesFov_butNotOrbitRadius() {
        var c = CameraControls(mode: .firstPerson)
        let initialFov = c.fov
        let initialRadius = c.orbitRadius

        c.handlePinch(2.0)

        XCTAssertLessThan(c.fov, initialFov, "firstPerson pinch out (zoom in) must shrink FOV")
        XCTAssertEqual(c.orbitRadius, initialRadius, "firstPerson pinch must NOT mutate orbit radius")
    }

    func testFirstPersonFovClampedToMinFov() {
        var c = CameraControls(mode: .firstPerson)
        c.minFov = 20.0

        // Extreme zoom-in
        c.handlePinch(1000.0)

        XCTAssertGreaterThanOrEqual(c.fov, c.minFov, "FOV must clamp to minFov")
    }

    func testFirstPersonFovClampedToMaxFov() {
        var c = CameraControls(mode: .firstPerson)
        c.maxFov = 90.0

        // Extreme zoom-out
        c.handlePinch(0.001)

        XCTAssertLessThanOrEqual(c.fov, c.maxFov, "FOV must clamp to maxFov")
    }

    // MARK: - per-mode defaults

    func testFirstPersonFovDefaultMatchesPerspectiveCamera() {
        let c = CameraControls(mode: .firstPerson)
        // Matches the perspCamera default set in SceneView.setupScene.
        XCTAssertEqual(c.fov, 60.0, accuracy: 0.01)
    }

    func testFirstPersonFovRangeMatchesAndroidDefault() {
        let c = CameraControls(mode: .firstPerson)
        // Mirrors Android `FovZoomCameraManipulator.fovRangeDegrees = 10°..120°`.
        XCTAssertEqual(c.minFov, 10.0, accuracy: 0.01)
        XCTAssertEqual(c.maxFov, 120.0, accuracy: 0.01)
    }

    // MARK: - isEnabled gates all modes

    func testHandlePinchRespectsIsEnabled() {
        for mode in [CameraControlMode.orbit, .pan, .firstPerson] {
            var c = CameraControls(mode: mode)
            c.isEnabled = false
            let radius = c.orbitRadius
            let fov = c.fov

            c.handlePinch(2.0)

            XCTAssertEqual(c.orbitRadius, radius, "disabled pinch must no-op (mode=\(mode))")
            XCTAssertEqual(c.fov, fov, "disabled pinch must no-op (mode=\(mode))")
        }
    }
}
#endif
