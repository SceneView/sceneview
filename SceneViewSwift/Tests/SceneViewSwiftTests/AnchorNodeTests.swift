import XCTest
@testable import SceneViewSwift

#if os(iOS) || os(macOS) || os(visionOS)
import RealityKit

/// Tests for anchor-like entity patterns used in SceneView.
///
/// SceneViewSwift does not have a dedicated AnchorNode type, but RealityKit's
/// AnchorEntity is the standard way to anchor content. These tests verify
/// basic anchor entity behavior that SceneView relies on.
// Test classes run on the main actor: their RealityKit node factories
// (`LightNode.directional`, `node.entity`, …) are `@MainActor`. (#1054)
@MainActor
final class AnchorNodeTests: XCTestCase {

    // MARK: - World anchor creation

    func testWorldAnchorCreation() {
        let anchor = AnchorEntity(world: .init(x: 1, y: 0, z: -2))
        XCTAssertNotNil(anchor)
    }

    func testWorldAnchorAtOrigin() {
        let anchor = AnchorEntity(world: .zero)
        XCTAssertNotNil(anchor)
        XCTAssertEqual(anchor.position.x, 0.0, accuracy: 0.001)
        XCTAssertEqual(anchor.position.y, 0.0, accuracy: 0.001)
        XCTAssertEqual(anchor.position.z, 0.0, accuracy: 0.001)
    }

    // MARK: - Plane anchor creation

    func testPlaneAnchorHorizontal() {
        let anchor = AnchorEntity(
            plane: .horizontal,
            minimumBounds: .init(x: 0.2, y: 0.2)
        )
        XCTAssertNotNil(anchor)
    }

    func testPlaneAnchorVertical() {
        let anchor = AnchorEntity(
            plane: .vertical,
            minimumBounds: .init(x: 0.1, y: 0.1)
        )
        XCTAssertNotNil(anchor)
    }

    // MARK: - Child management

    func testAddChildToAnchor() {
        let anchor = AnchorEntity(world: .zero)
        let child = ModelEntity()
        anchor.addChild(child)

        XCTAssertEqual(anchor.children.count, 1)
    }

    func testAddMultipleChildrenToAnchor() {
        let anchor = AnchorEntity(world: .zero)
        let child1 = ModelEntity()
        let child2 = ModelEntity()
        let child3 = Entity()
        anchor.addChild(child1)
        anchor.addChild(child2)
        anchor.addChild(child3)

        XCTAssertEqual(anchor.children.count, 3)
    }

    func testRemoveChildFromAnchor() {
        let anchor = AnchorEntity(world: .zero)
        let child = ModelEntity()
        anchor.addChild(child)
        XCTAssertEqual(anchor.children.count, 1)

        child.removeFromParent()
        XCTAssertEqual(anchor.children.count, 0)
    }

    // MARK: - Anchor with GeometryNode

    func testAnchorWithGeometryNode() {
        let anchor = AnchorEntity(world: .init(x: 0, y: 0, z: -1))
        let cube = GeometryNode.cube(size: 0.3, color: .blue)
        anchor.addChild(cube.entity)

        XCTAssertEqual(anchor.children.count, 1)
    }

    func testAnchorWithBillboardNode() {
        let anchor = AnchorEntity(world: .init(x: 0, y: 1, z: -2))
        let billboard = BillboardNode.text("Label")
        anchor.addChild(billboard.entity)

        XCTAssertEqual(anchor.children.count, 1)
    }
}
#endif
