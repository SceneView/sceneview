import XCTest
@testable import SceneViewSwift

#if os(iOS) || os(macOS) || os(visionOS)
import RealityKit

// Test classes run on the main actor: their RealityKit node factories
// (`LightNode.directional`, `node.entity`, …) are `@MainActor`. (#1054)
@MainActor
final class LineNodeTests: XCTestCase {

    // MARK: - Basic creation

    func testLineCreatesEntity() {
        let line = LineNode(
            from: .zero,
            to: .init(x: 1, y: 0, z: 0)
        )
        XCTAssertNotNil(line.entity)
        XCTAssertNotNil(line.entity.model)
    }

    func testLinePositionedAtMidpoint() {
        let from = SIMD3<Float>(0, 0, 0)
        let to = SIMD3<Float>(2, 0, 0)
        let line = LineNode(from: from, to: to)

        // Entity should be at midpoint (1, 0, 0)
        XCTAssertEqual(line.entity.position.x, 1.0, accuracy: 0.01)
        XCTAssertEqual(line.entity.position.y, 0.0, accuracy: 0.01)
        XCTAssertEqual(line.entity.position.z, 0.0, accuracy: 0.01)
    }

    func testVerticalLinePositionedAtMidpoint() {
        let from = SIMD3<Float>(0, 0, 0)
        let to = SIMD3<Float>(0, 4, 0)
        let line = LineNode(from: from, to: to)

        // Midpoint at (0, 2, 0)
        XCTAssertEqual(line.entity.position.x, 0.0, accuracy: 0.01)
        XCTAssertEqual(line.entity.position.y, 2.0, accuracy: 0.01)
        XCTAssertEqual(line.entity.position.z, 0.0, accuracy: 0.01)
    }

    func testDiagonalLineMidpoint() {
        let from = SIMD3<Float>(1, 1, 1)
        let to = SIMD3<Float>(3, 3, 3)
        let line = LineNode(from: from, to: to)

        XCTAssertEqual(line.entity.position.x, 2.0, accuracy: 0.01)
        XCTAssertEqual(line.entity.position.y, 2.0, accuracy: 0.01)
        XCTAssertEqual(line.entity.position.z, 2.0, accuracy: 0.01)
    }

    // MARK: - Orientation

    func testVerticalLineHasIdentityLikeOrientation() {
        // Y-up line should not need rotation since cylinder default is Y-up
        let line = LineNode(
            from: .init(x: 0, y: 0, z: 0),
            to: .init(x: 0, y: 1, z: 0)
        )
        let angle = line.entity.orientation.angle
        XCTAssertEqual(angle, 0.0, accuracy: 0.01)
    }

    // MARK: - Custom thickness and color

    func testCustomThickness() {
        let line = LineNode(
            from: .zero,
            to: .init(x: 1, y: 0, z: 0),
            thickness: 0.02,
            color: .red
        )
        XCTAssertNotNil(line.entity.model)
    }

    // MARK: - Axis gizmo

    func testAxisGizmoCreatesThreeLines() {
        let gizmo = LineNode.axisGizmo()
        XCTAssertEqual(gizmo.count, 3)
    }

    func testAxisGizmoAtCustomOrigin() {
        let origin = SIMD3<Float>(1, 2, 3)
        let gizmo = LineNode.axisGizmo(at: origin, length: 1.0)
        XCTAssertEqual(gizmo.count, 3)

        // X-axis line: midpoint should be at (1.5, 2, 3)
        XCTAssertEqual(gizmo[0].entity.position.x, 1.5, accuracy: 0.01)
        XCTAssertEqual(gizmo[0].entity.position.y, 2.0, accuracy: 0.01)

        // Y-axis line: midpoint should be at (1, 2.5, 3)
        XCTAssertEqual(gizmo[1].entity.position.x, 1.0, accuracy: 0.01)
        XCTAssertEqual(gizmo[1].entity.position.y, 2.5, accuracy: 0.01)

        // Z-axis line: midpoint should be at (1, 2, 3.5)
        XCTAssertEqual(gizmo[2].entity.position.z, 3.5, accuracy: 0.01)
    }

    func testZeroLengthLineDoesNotCrash() {
        let line = LineNode(from: .zero, to: .zero)
        XCTAssertNotNil(line.entity)
    }

    // MARK: - Default thickness

    func testDefaultThickness() {
        let line = LineNode(from: .zero, to: .init(x: 1, y: 0, z: 0))
        // Default thickness is 0.005 — entity should have a model
        XCTAssertNotNil(line.entity.model)
    }

    // MARK: - Very small thickness

    func testVerySmallThickness() {
        let line = LineNode(
            from: .zero,
            to: .init(x: 1, y: 0, z: 0),
            thickness: 0.0001
        )
        XCTAssertNotNil(line.entity.model)
    }

    // MARK: - Zero thickness

    func testZeroThickness() {
        let line = LineNode(
            from: .zero,
            to: .init(x: 1, y: 0, z: 0),
            thickness: 0.0
        )
        XCTAssertNotNil(line.entity)
    }

    // MARK: - Negative direction

    func testNegativeDirectionLine() {
        let line = LineNode(
            from: .init(x: 1, y: 1, z: 1),
            to: .init(x: -1, y: -1, z: -1)
        )
        XCTAssertNotNil(line.entity)
        // Midpoint should be (0, 0, 0)
        XCTAssertEqual(line.entity.position.x, 0.0, accuracy: 0.01)
        XCTAssertEqual(line.entity.position.y, 0.0, accuracy: 0.01)
        XCTAssertEqual(line.entity.position.z, 0.0, accuracy: 0.01)
    }

    // MARK: - Axis gizmo custom parameters

    func testAxisGizmoWithCustomLength() {
        let gizmo = LineNode.axisGizmo(length: 2.0)
        XCTAssertEqual(gizmo.count, 3)
        // X-axis: midpoint at (1, 0, 0)
        XCTAssertEqual(gizmo[0].entity.position.x, 1.0, accuracy: 0.01)
    }

    func testAxisGizmoWithCustomThickness() {
        let gizmo = LineNode.axisGizmo(thickness: 0.02)
        XCTAssertEqual(gizmo.count, 3)
        // Should not crash
        for line in gizmo {
            XCTAssertNotNil(line.entity.model)
        }
    }
}
#endif
