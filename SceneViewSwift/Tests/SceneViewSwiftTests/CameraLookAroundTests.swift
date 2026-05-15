import XCTest
import simd
@testable import SceneViewSwift

#if os(iOS) || os(visionOS) || os(macOS)
/// Pins the true look-around `firstPerson` camera math — closes #1236.
///
/// Covers the two regressions called out in the issue:
/// 1. orbit → firstPerson teleport (the camera must stay where it was), and
/// 2. pan → orbit pivot drift (`recenterTarget()` must snap the pivot back).
///
/// Test classes run on the main actor: their RealityKit node factories are
/// `@MainActor` (#1054).
@MainActor
final class CameraLookAroundTests: XCTestCase {

    private let eps: Float = 1e-4

    private func assertClose(
        _ a: SIMD3<Float>, _ b: SIMD3<Float>,
        _ msg: String, accuracy: Float = 1e-3,
        file: StaticString = #filePath, line: UInt = #line
    ) {
        XCTAssertEqual(a.x, b.x, accuracy: accuracy, msg, file: file, line: line)
        XCTAssertEqual(a.y, b.y, accuracy: accuracy, msg, file: file, line: line)
        XCTAssertEqual(a.z, b.z, accuracy: accuracy, msg, file: file, line: line)
    }

    // MARK: - Limitation 1: orbit → firstPerson must NOT teleport

    func testEnterFirstPerson_pinsEyeAtCurrentOrbitPosition() {
        var c = CameraControls(mode: .orbit)
        c.azimuth = .pi / 4          // 45°
        c.elevation = .pi / 3        // 60°
        c.orbitRadius = 4
        let orbitEye = c.cameraPosition()

        c.mode = .firstPerson
        c.enterFirstPerson()

        XCTAssertNotNil(c.firstPersonEye)
        assertClose(c.firstPersonEye!, orbitEye,
                    "entering firstPerson must pin the eye at the exact orbit position — no teleport")
    }

    func testEnterFirstPerson_isIdempotentWithinSession() {
        var c = CameraControls(mode: .firstPerson)
        c.orbitRadius = 4
        c.enterFirstPerson()
        let firstEye = c.firstPersonEye!

        // Look around, then a stray re-entry call must NOT recapture.
        c.handleDrag(CGSize(width: 300, height: 0))
        c.enterFirstPerson()

        assertClose(c.firstPersonEye!, firstEye,
                    "the eye must stay fixed while looking around")
    }

    func testLookAround_keepsEyeFixed_onlyOrientationChanges() {
        var c = CameraControls(mode: .firstPerson)
        c.enterFirstPerson()
        let eye = c.firstPersonEye!
        let orientationBefore = c.lookOrientation()

        c.handleDrag(CGSize(width: 200, height: 80))

        assertClose(c.firstPersonEye!, eye, "look-around must not move the eye")
        let orientationAfter = c.lookOrientation()
        XCTAssertNotEqual(orientationBefore.vector, orientationAfter.vector,
                          "look-around must change the camera orientation")
    }

    // MARK: - orbit ↔ firstPerson is continuous (no teleport either way)

    func testExitFirstPerson_orbitPositionEqualsFirstPersonEye() {
        var c = CameraControls(mode: .orbit)
        c.azimuth = .pi / 5
        c.elevation = .pi / 8
        c.orbitRadius = 3.5

        // orbit → firstPerson
        c.mode = .firstPerson
        c.enterFirstPerson()
        let eye = c.firstPersonEye!

        // look around in place — the eye must stay set throughout
        c.handleDrag(CGSize(width: 150, height: -40))
        XCTAssertNotNil(c.firstPersonEye, "eye stays set while looking around")

        // firstPerson → orbit
        c.mode = .orbit
        c.exitFirstPerson()

        XCTAssertNil(c.firstPersonEye, "leaving firstPerson must clear the eye")
        // The recomputed orbit camera position must land back on the eye —
        // this is what makes the firstPerson → orbit switch teleport-free.
        assertClose(c.cameraPosition(), eye,
                    "orbit position after exitFirstPerson must equal the firstPerson eye")
    }

    func testRoundTrip_orbitFirstPersonOrbit_isStable() {
        var c = CameraControls(mode: .orbit)
        c.azimuth = 0.6
        c.elevation = 0.4
        c.orbitRadius = 5
        let eye0 = c.cameraPosition()

        c.mode = .firstPerson
        c.enterFirstPerson()
        c.mode = .orbit
        c.exitFirstPerson()

        assertClose(c.cameraPosition(), eye0,
                    "a no-look round trip orbit→firstPerson→orbit must be a no-op")
    }

    // MARK: - look math consistency

    func testLookForward_matchesOrbitForward() {
        var c = CameraControls(mode: .orbit)
        c.azimuth = 0.9
        c.elevation = -0.3
        c.orbitRadius = 2

        // In orbit the camera looks from cameraPosition() toward target.
        let orbitForward = simd_normalize(c.target - c.cameraPosition())
        assertClose(c.lookForward(), orbitForward, "lookForward must equal the orbit look direction")
    }

    func testLookOrientation_minusZMapsToLookForward() {
        var c = CameraControls(mode: .firstPerson)
        c.azimuth = 1.1
        c.elevation = 0.2

        let q = c.lookOrientation()
        let rotatedMinusZ = q.act(SIMD3<Float>(0, 0, -1))
        assertClose(rotatedMinusZ, c.lookForward(),
                    "the camera's local -Z must map to the look-forward vector", accuracy: 1e-4)
    }

    // MARK: - Limitation 2: pan → orbit pivot drift

    func testRecenterTarget_snapsPivotToOrigin() {
        var c = CameraControls(mode: .pan)
        // Simulate repeated panning that drifted the target.
        c.target = SIMD3<Float>(2, 0.5, -1)

        c.recenterTarget()  // default centroid = origin

        assertClose(c.target, .zero, "recenterTarget() must snap the pivot back to the centroid")
    }

    func testRecenterTarget_acceptsExplicitCentroid() {
        var c = CameraControls(mode: .orbit)
        c.target = SIMD3<Float>(5, 5, 5)
        let centroid = SIMD3<Float>(1, 2, 3)

        c.recenterTarget(centroid)

        assertClose(c.target, centroid, "recenterTarget(_:) must honour an explicit centroid")
    }

    func testRecenterTarget_preservesViewingAngleAndRadius() {
        var c = CameraControls(mode: .orbit)
        c.azimuth = 0.7
        c.elevation = 0.3
        c.orbitRadius = 4
        c.target = SIMD3<Float>(3, 0, 0)

        c.recenterTarget()

        XCTAssertEqual(c.azimuth, 0.7, accuracy: eps, "recenter must keep azimuth")
        XCTAssertEqual(c.elevation, 0.3, accuracy: eps, "recenter must keep elevation")
        XCTAssertEqual(c.orbitRadius, 4, accuracy: eps, "recenter must keep radius")
    }

    func testRecenterTarget_clearsFirstPersonEye() {
        var c = CameraControls(mode: .firstPerson)
        c.enterFirstPerson()
        XCTAssertNotNil(c.firstPersonEye)

        c.recenterTarget()

        XCTAssertNil(c.firstPersonEye, "recenterTarget() must drop a stale firstPerson eye")
    }

    func testPanThenRecenter_pivotDriftEliminated() {
        // Repeated pan-then-orbit workflow from the issue: target drifts,
        // recenterTarget() brings the pivot back to the content centroid.
        var c = CameraControls(mode: .pan)
        for _ in 0..<5 {
            c.handleDrag(CGSize(width: 100, height: 0))  // drift the pivot
        }
        XCTAssertGreaterThan(abs(c.target.x), 0.5, "panning must have drifted the pivot")

        c.mode = .orbit
        c.recenterTarget()

        assertClose(c.target, .zero, "after recenter the orbit pivot is back on the centroid")
    }
}
#endif
