import XCTest
@testable import SceneViewSwift

#if os(iOS) || os(macOS) || os(visionOS)
import RealityKit

// Test classes run on the main actor: their RealityKit node factories
// (`LightNode.directional`, `node.entity`, …) are `@MainActor`. (#1054)
@MainActor
final class BillboardNodeTests: XCTestCase {

    // MARK: - Creation

    func testBillboardWrapsChildEntity() {
        let child = ModelEntity()
        let billboard = BillboardNode(child: child)

        XCTAssertNotNil(billboard.entity)
        // The container should have the child
        XCTAssertEqual(billboard.entity.children.count, 1)
    }

    func testBillboardHasBillboardComponent() {
        if #available(iOS 18.0, macOS 15.0, visionOS 2.0, *) {
            let child = Entity()
            let billboard = BillboardNode(child: child)

            let component = billboard.entity.components[BillboardComponent.self]
            XCTAssertNotNil(component)
        }
    }

    // MARK: - Text factory

    func testTextFactoryCreatesEntity() {
        let billboard = BillboardNode.text("Hello!", fontSize: 0.05)
        XCTAssertNotNil(billboard.entity)
        XCTAssertTrue(billboard.entity.children.count > 0)
    }

    func testTextFactoryWithCustomColor() {
        let billboard = BillboardNode.text("Colored", fontSize: 0.03, color: .red)
        XCTAssertNotNil(billboard.entity)
    }

    // MARK: - Transform helpers

    func testPositionSetsEntityPosition() {
        let billboard = BillboardNode.text("Pos")
            .position(.init(x: 1, y: 1.5, z: -2))

        XCTAssertEqual(billboard.entity.position.x, 1.0, accuracy: 0.001)
        XCTAssertEqual(billboard.entity.position.y, 1.5, accuracy: 0.001)
        XCTAssertEqual(billboard.entity.position.z, -2.0, accuracy: 0.001)
    }

    func testScaleSetsUniformScale() {
        let billboard = BillboardNode(child: Entity())
            .scale(2.0)

        XCTAssertEqual(billboard.entity.scale.x, 2.0, accuracy: 0.001)
        XCTAssertEqual(billboard.entity.scale.y, 2.0, accuracy: 0.001)
        XCTAssertEqual(billboard.entity.scale.z, 2.0, accuracy: 0.001)
    }

    func testPositionPropertyGetSet() {
        var billboard = BillboardNode(child: Entity())
        billboard.position = SIMD3<Float>(3, 4, 5)

        XCTAssertEqual(billboard.position.x, 3.0, accuracy: 0.001)
        XCTAssertEqual(billboard.position.y, 4.0, accuracy: 0.001)
        XCTAssertEqual(billboard.position.z, 5.0, accuracy: 0.001)
    }

    // MARK: - Chaining

    func testChainingPositionAndScale() {
        let billboard = BillboardNode.text("Chain")
            .position(.init(x: 0, y: 2, z: -1))
            .scale(0.5)

        XCTAssertEqual(billboard.entity.position.y, 2.0, accuracy: 0.001)
        XCTAssertEqual(billboard.entity.scale.x, 0.5, accuracy: 0.001)
    }

    // MARK: - Scale property

    func testScalePropertyDoesNotAffectChild() {
        let child = ModelEntity()
        let billboard = BillboardNode(child: child)
            .scale(3.0)
        // Scale should apply to the container, not the child
        XCTAssertEqual(billboard.entity.scale.x, 3.0, accuracy: 0.001)
        XCTAssertEqual(child.scale.x, 1.0, accuracy: 0.001)
    }

    // MARK: - Empty text factory

    func testTextFactoryWithEmptyString() {
        let billboard = BillboardNode.text("")
        XCTAssertNotNil(billboard.entity)
        XCTAssertTrue(billboard.entity.children.count > 0)
    }

    // MARK: - Billboard with geometry child

    func testBillboardWithGeometryChild() {
        let cube = GeometryNode.cube(size: 0.3, color: .red)
        let billboard = BillboardNode(child: cube.entity)
        XCTAssertNotNil(billboard.entity)
        XCTAssertEqual(billboard.entity.children.count, 1)
        if #available(iOS 18.0, macOS 15.0, visionOS 2.0, *) {
            XCTAssertNotNil(billboard.entity.components[BillboardComponent.self])
        }
    }
}
#endif
