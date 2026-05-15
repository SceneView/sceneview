#if os(iOS) || os(macOS) || os(visionOS)
import XCTest
import simd
import RealityKit
@testable import SceneViewSwift

// Test classes run on the main actor: their RealityKit node factories
// (`LightNode.directional`, `node.entity`, …) are `@MainActor`. (#1054)
@MainActor
final class ShapeNodeTests: XCTestCase {

    // MARK: - Basic creation

    func testTriangleCreation() {
        let shape = ShapeNode(
            points: [
                SIMD2<Float>(0, 0.5),
                SIMD2<Float>(-0.5, -0.5),
                SIMD2<Float>(0.5, -0.5)
            ],
            color: .red
        )
        XCTAssertNotNil(shape.entity)
        XCTAssertEqual(shape.points.count, 3)
    }

    func testSquareCreation() {
        let shape = ShapeNode(
            points: [
                SIMD2<Float>(-0.5, -0.5),
                SIMD2<Float>(0.5, -0.5),
                SIMD2<Float>(0.5, 0.5),
                SIMD2<Float>(-0.5, 0.5)
            ]
        )
        XCTAssertNotNil(shape.entity)
        XCTAssertEqual(shape.points.count, 4)
    }

    func testExtrudedShape() {
        let shape = ShapeNode(
            points: [
                SIMD2<Float>(0, 0),
                SIMD2<Float>(1, 0),
                SIMD2<Float>(0.5, 1)
            ],
            extrusionDepth: 0.2,
            color: .blue
        )
        XCTAssertNotNil(shape.entity)
    }

    func testDegeneratePolygon_LessThan3Points() {
        let shape = ShapeNode(
            points: [SIMD2<Float>(0, 0), SIMD2<Float>(1, 0)]
        )
        // Should not crash, produces a minimal entity
        XCTAssertNotNil(shape.entity)
    }

    func testEmptyPoints() {
        let shape = ShapeNode(points: [])
        XCTAssertNotNil(shape.entity)
    }

    // MARK: - Presets

    func testRegularPolygon_Hexagon() {
        let hex = ShapeNode.regularPolygon(sides: 6, radius: 0.5)
        XCTAssertEqual(hex.points.count, 6)
    }

    func testRegularPolygon_Triangle() {
        let tri = ShapeNode.regularPolygon(sides: 3, radius: 1.0, color: .green)
        XCTAssertEqual(tri.points.count, 3)
    }

    func testRegularPolygon_MinimumSides() {
        // Requesting 2 sides should clamp to 3
        let shape = ShapeNode.regularPolygon(sides: 2, radius: 0.5)
        XCTAssertEqual(shape.points.count, 3)
    }

    func testStar() {
        let star = ShapeNode.star(pointCount: 5, outerRadius: 0.5, innerRadius: 0.2)
        XCTAssertEqual(star.points.count, 10) // 5 outer + 5 inner
    }

    func testExtrudedStar() {
        let star = ShapeNode.star(
            pointCount: 6,
            outerRadius: 0.4,
            innerRadius: 0.15,
            extrusionDepth: 0.1,
            color: .yellow
        )
        XCTAssertNotNil(star.entity)
    }

    // MARK: - Transform helpers

    func testPosition() {
        let shape = ShapeNode.regularPolygon(sides: 4, radius: 0.3)
            .position(SIMD3<Float>(1, 2, 3))
        XCTAssertEqual(shape.entity.position.x, 1, accuracy: 0.001)
        XCTAssertEqual(shape.entity.position.y, 2, accuracy: 0.001)
        XCTAssertEqual(shape.entity.position.z, 3, accuracy: 0.001)
    }

    func testScaleUniform() {
        let shape = ShapeNode.regularPolygon(sides: 5, radius: 0.5)
            .scale(2.0)
        XCTAssertEqual(shape.entity.scale.x, 2.0, accuracy: 0.001)
        XCTAssertEqual(shape.entity.scale.y, 2.0, accuracy: 0.001)
        XCTAssertEqual(shape.entity.scale.z, 2.0, accuracy: 0.001)
    }

    func testScalePerAxis() {
        let shape = ShapeNode.regularPolygon(sides: 4, radius: 0.5)
            .scale(SIMD3<Float>(1, 2, 3))
        XCTAssertEqual(shape.entity.scale.x, 1, accuracy: 0.001)
        XCTAssertEqual(shape.entity.scale.y, 2, accuracy: 0.001)
        XCTAssertEqual(shape.entity.scale.z, 3, accuracy: 0.001)
    }

    func testRotation() {
        let shape = ShapeNode.regularPolygon(sides: 4, radius: 0.5)
            .rotation(angle: .pi / 4, axis: SIMD3<Float>(0, 1, 0))
        XCTAssertNotNil(shape.entity.orientation)
    }

    func testRotationQuaternion() {
        let q = simd_quatf(angle: .pi / 2, axis: SIMD3<Float>(0, 0, 1))
        let shape = ShapeNode.regularPolygon(sides: 3, radius: 0.5)
            .rotation(q)
        XCTAssertEqual(shape.entity.orientation.angle, q.angle, accuracy: 0.001)
    }

    // MARK: - Material

    func testGeometryMaterial() {
        let shape = ShapeNode(
            points: [
                SIMD2<Float>(0, 0),
                SIMD2<Float>(1, 0),
                SIMD2<Float>(0.5, 1)
            ],
            material: .pbr(color: .gray, metallic: 1.0, roughness: 0.2)
        )
        XCTAssertNotNil(shape.entity)
    }

    // MARK: - EntityProvider

    func testEntityProvider() {
        let shape = ShapeNode.regularPolygon(sides: 6, radius: 0.3)
        XCTAssertNotNil(shape.sceneEntity)
        XCTAssert(shape.sceneEntity === shape.entity)
    }

    // MARK: - `unlit:` parameter (#1359)

    private static let triangle: [SIMD2<Float>] = [
        SIMD2<Float>(0, 0.5),
        SIMD2<Float>(-0.5, -0.5),
        SIMD2<Float>(0.5, -0.5)
    ]

    /// `unlit: true` must produce an `UnlitMaterial` — a flat fill that does
    /// not respond to image-based lighting — not a lit `SimpleMaterial`.
    func testUnlitParameterYieldsUnlitMaterial() {
        let shape = ShapeNode(points: Self.triangle, color: .red, unlit: true)
        let material = shape.entity.model?.materials.first
        XCTAssertNotNil(material)
        XCTAssertTrue(
            material is UnlitMaterial,
            "unlit: true must yield an UnlitMaterial (no lighting response), got \(type(of: material))"
        )
        XCTAssertFalse(material is SimpleMaterial, "unlit material must not be a lit SimpleMaterial")
    }

    /// The `unlit:` contract holds for extruded shapes too.
    func testExtrudedUnlitParameterYieldsUnlitMaterial() {
        let shape = ShapeNode(
            points: Self.triangle,
            extrusionDepth: 0.1,
            color: .red,
            unlit: true
        )
        XCTAssertTrue(shape.entity.model?.materials.first is UnlitMaterial)
    }

    /// The default (`unlit: false`) path stays physically-based.
    func testDefaultParameterYieldsPBRMaterial() {
        let shape = ShapeNode(points: Self.triangle, color: .red)
        XCTAssertTrue(
            shape.entity.model?.materials.first is PhysicallyBasedMaterial,
            "default material must be physically-based"
        )
    }

    // MARK: - Concave polygon

    func testConcavePolygon_LShape() {
        let shape = ShapeNode(
            points: [
                SIMD2<Float>(0, 0),
                SIMD2<Float>(0.5, 0),
                SIMD2<Float>(0.5, 0.2),
                SIMD2<Float>(0.2, 0.2),
                SIMD2<Float>(0.2, 0.8),
                SIMD2<Float>(0, 0.8)
            ],
            color: .blue
        )
        XCTAssertNotNil(shape.entity)
        XCTAssertEqual(shape.points.count, 6)
    }
}

#endif // os(iOS) || os(macOS) || os(visionOS)
