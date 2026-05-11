import XCTest
@testable import SceneViewSwift

#if os(iOS) || os(macOS) || os(visionOS)
import RealityKit

final class PathNodeTests: XCTestCase {

    // MARK: - Basic creation

    func testPathWithTwoPointsCreatesOneSegment() {
        let path = PathNode(
            points: [.zero, .init(x: 1, y: 0, z: 0)]
        )
        XCTAssertNotNil(path.entity)
        // One line segment = one child entity
        XCTAssertEqual(path.entity.children.count, 1)
    }

    func testPathWithThreePointsCreatesTwoSegments() {
        let path = PathNode(
            points: [
                .zero,
                .init(x: 1, y: 0, z: 0),
                .init(x: 1, y: 1, z: 0)
            ]
        )
        XCTAssertEqual(path.entity.children.count, 2)
    }

    func testClosedPathAddsExtraSegment() {
        let points: [SIMD3<Float>] = [
            .zero,
            .init(x: 1, y: 0, z: 0),
            .init(x: 0.5, y: 1, z: 0)
        ]
        let open = PathNode(points: points, closed: false)
        let closed = PathNode(points: points, closed: true)

        // Closed should have one more segment than open
        XCTAssertEqual(open.entity.children.count, 2)
        XCTAssertEqual(closed.entity.children.count, 3)
    }

    func testPathStoresPoints() {
        let points: [SIMD3<Float>] = [
            .init(x: 0, y: 0, z: 0),
            .init(x: 1, y: 0, z: 0),
            .init(x: 1, y: 1, z: 0)
        ]
        let path = PathNode(points: points)
        XCTAssertEqual(path.points.count, 3)
        XCTAssertEqual(path.points[0].x, 0.0, accuracy: 0.001)
        XCTAssertEqual(path.points[1].x, 1.0, accuracy: 0.001)
    }

    // MARK: - Edge cases

    func testSinglePointCreatesNoSegments() {
        let path = PathNode(points: [.zero])
        XCTAssertEqual(path.entity.children.count, 0)
    }

    func testEmptyPointsCreatesNoSegments() {
        let path = PathNode(points: [])
        XCTAssertEqual(path.entity.children.count, 0)
    }

    // MARK: - Circle factory

    func testCircleCreatesClosedPath() {
        let circle = PathNode.circle(radius: 0.5, segments: 16)
        // `segments: 16` means 16 sample points around the circle. PathNode
        // produces (n - 1) inter-point segments + 1 closing segment = n segments
        // total. The pre-fix assertion of 17 was off by one — `segments: N`
        // returns N child entities, not N + 1. (#883)
        XCTAssertEqual(circle.entity.children.count, 16)
    }

    func testCirclePointCount() {
        let circle = PathNode.circle(segments: 8)
        XCTAssertEqual(circle.points.count, 8)
    }

    func testCirclePointsLieOnXZPlane() {
        let center = SIMD3<Float>(0, 1, 0)
        let circle = PathNode.circle(center: center, radius: 1.0, segments: 4)

        for point in circle.points {
            // All points should be at y=1 (the center's y)
            XCTAssertEqual(point.y, 1.0, accuracy: 0.001)
            // Distance from center should be the radius
            let dx = point.x - center.x
            let dz = point.z - center.z
            let dist = sqrt(dx * dx + dz * dz)
            XCTAssertEqual(dist, 1.0, accuracy: 0.01)
        }
    }

    // MARK: - Grid factory

    func testGridCreatesLines() {
        let grid = PathNode.grid(size: 2.0, divisions: 4)
        // 4 divisions => 5 lines in each direction => 10 total child entities
        XCTAssertEqual(grid.entity.children.count, 10)
    }

    // MARK: - Transform

    func testPositionSetsEntityPosition() {
        let path = PathNode(
            points: [.zero, .init(x: 1, y: 0, z: 0)]
        ).position(.init(x: 0, y: 1, z: 0))

        XCTAssertEqual(path.entity.position.y, 1.0, accuracy: 0.001)
    }

    // MARK: - Custom thickness and color

    func testPathWithCustomThickness() {
        let path = PathNode(
            points: [.zero, .init(x: 1, y: 0, z: 0)],
            thickness: 0.02,
            color: .red
        )
        XCTAssertNotNil(path.entity)
        XCTAssertEqual(path.entity.children.count, 1)
    }

    func testCircleWithCustomThicknessAndColor() {
        let circle = PathNode.circle(
            radius: 1.0,
            segments: 8,
            thickness: 0.01,
            color: .blue
        )
        XCTAssertNotNil(circle.entity)
        // `segments: 8` → 8 child entities total (7 inter-point + 1 closing).
        // Pre-fix the assertion was 9 — same off-by-one as testCircleCreatesClosedPath. (#883)
        XCTAssertEqual(circle.entity.children.count, 8)
    }

    func testGridWithCustomThicknessAndColor() {
        let grid = PathNode.grid(
            size: 1.0,
            divisions: 2,
            thickness: 0.01,
            color: .green
        )
        XCTAssertNotNil(grid.entity)
        // 2 divisions => 3 lines per axis => 6 total
        XCTAssertEqual(grid.entity.children.count, 6)
    }

    // MARK: - Circle edge cases

    func testCircleWithOneSegment() {
        let circle = PathNode.circle(segments: 1)
        XCTAssertEqual(circle.points.count, 1)
    }

    func testCircleWithZeroRadius() {
        let circle = PathNode.circle(radius: 0.0, segments: 4)
        // All points should be at center
        for point in circle.points {
            XCTAssertEqual(point.x, 0.0, accuracy: 0.001)
            XCTAssertEqual(point.z, 0.0, accuracy: 0.001)
        }
    }

    // MARK: - Grid edge cases

    func testGridWithOneDivision() {
        let grid = PathNode.grid(size: 1.0, divisions: 1)
        // 1 division => 2 lines per axis => 4 total
        XCTAssertEqual(grid.entity.children.count, 4)
    }

    func testGridWithZeroDivisions() {
        let grid = PathNode.grid(size: 1.0, divisions: 0)
        // 0 divisions => 1 line per axis => 2 total
        XCTAssertEqual(grid.entity.children.count, 2)
    }

    // MARK: - Large point counts

    func testPathWithManyPoints() {
        var points: [SIMD3<Float>] = []
        for i in 0..<100 {
            points.append(SIMD3<Float>(Float(i) * 0.1, 0, 0))
        }
        let path = PathNode(points: points)
        XCTAssertEqual(path.entity.children.count, 99)
        XCTAssertEqual(path.points.count, 100)
    }
}
#endif
