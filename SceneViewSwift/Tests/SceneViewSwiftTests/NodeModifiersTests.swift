import XCTest
@testable import SceneViewSwift

#if os(iOS) || os(macOS) || os(visionOS)
import RealityKit

// Test classes run on the main actor: their RealityKit node factories
// (`LightNode.directional`, `node.entity`, …) are `@MainActor`. (#1054)
@MainActor
final class NodeModifiersTests: XCTestCase {

    // MARK: - Entity modifiers

    func testPositionedAt() {
        let entity = Entity()
        entity.positioned(at: [1, 2, 3])
        XCTAssertEqual(entity.position.x, 1.0, accuracy: 0.001)
        XCTAssertEqual(entity.position.y, 2.0, accuracy: 0.001)
        XCTAssertEqual(entity.position.z, 3.0, accuracy: 0.001)
    }

    func testScaledToUniform() {
        let entity = Entity()
        entity.scaled(to: 2.0)
        XCTAssertEqual(entity.scale.x, 2.0, accuracy: 0.001)
        XCTAssertEqual(entity.scale.y, 2.0, accuracy: 0.001)
        XCTAssertEqual(entity.scale.z, 2.0, accuracy: 0.001)
    }

    func testScaledToPerAxis() {
        let entity = Entity()
        entity.scaled(to: [1, 2, 3])
        XCTAssertEqual(entity.scale.x, 1.0, accuracy: 0.001)
        XCTAssertEqual(entity.scale.y, 2.0, accuracy: 0.001)
        XCTAssertEqual(entity.scale.z, 3.0, accuracy: 0.001)
    }

    func testRotatedByAngle() {
        let entity = Entity()
        entity.rotated(by: .pi / 2, around: [0, 1, 0])
        XCTAssertEqual(entity.orientation.angle, Float.pi / 2, accuracy: 0.01)
    }

    func testOrientedToQuaternion() {
        let entity = Entity()
        let q = simd_quatf(angle: .pi / 4, axis: [1, 0, 0])
        entity.oriented(to: q)
        XCTAssertEqual(entity.orientation.angle, Float.pi / 4, accuracy: 0.01)
    }

    func testNamed() {
        let entity = Entity()
        entity.named("myEntity")
        XCTAssertEqual(entity.name, "myEntity")
    }

    func testEnabled() {
        let entity = Entity()
        entity.enabled(false)
        XCTAssertFalse(entity.isEnabled)
        entity.enabled(true)
        XCTAssertTrue(entity.isEnabled)
    }

    func testWithChild() {
        let parent = Entity()
        let child = Entity()
        parent.withChild(child)
        XCTAssertEqual(parent.children.count, 1)
    }

    func testWithChildren() {
        let parent = Entity()
        let children = [Entity(), Entity(), Entity()]
        parent.withChildren(children)
        XCTAssertEqual(parent.children.count, 3)
    }

    // MARK: - Chaining

    func testChainingModifiers() {
        let entity = Entity()
        entity
            .positioned(at: [1, 0, -2])
            .scaled(to: 0.5)
            .named("test")
            .enabled(true)

        XCTAssertEqual(entity.position.x, 1.0, accuracy: 0.001)
        XCTAssertEqual(entity.scale.x, 0.5, accuracy: 0.001)
        XCTAssertEqual(entity.name, "test")
        XCTAssertTrue(entity.isEnabled)
    }

    // MARK: - ModelEntity modifiers

    func testWithCollision() {
        let entity = ModelEntity(
            mesh: .generateBox(size: 1.0),
            materials: [SimpleMaterial()]
        )
        entity.withCollision()
        XCTAssertNotNil(entity.components[CollisionComponent.self])
    }

    func testWithShadow() {
        let entity = ModelEntity()
        entity.withShadow()
        // Just verify it doesn't crash
        XCTAssertNotNil(entity)
    }
}

#endif // os(iOS) || os(macOS) || os(visionOS)
