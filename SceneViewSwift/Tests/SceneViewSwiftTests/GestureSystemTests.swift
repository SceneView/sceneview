import XCTest
@testable import SceneViewSwift

#if os(iOS) || os(macOS) || os(visionOS)
import RealityKit

// Test classes run on the main actor: their RealityKit node factories
// (`LightNode.directional`, `node.entity`, …) are `@MainActor`. (#1054)
@MainActor
final class GestureSystemTests: XCTestCase {

    @MainActor
    override func tearDown() {
        super.tearDown()
        NodeGesture.removeAllHandlers()
    }

    // MARK: - Registration

    @MainActor
    func testOnTapRegistersHandler() {
        let entity = ModelEntity(
            mesh: .generateBox(size: 1.0),
            materials: [SimpleMaterial()]
        )
        NodeGesture.onTap(entity) { }
        XCTAssertTrue(NodeGesture.hasHandlers(for: entity))
    }

    @MainActor
    func testOnDragRegistersHandler() {
        let entity = ModelEntity(
            mesh: .generateBox(size: 1.0),
            materials: [SimpleMaterial()]
        )
        NodeGesture.onDrag(entity) { _ in }
        XCTAssertTrue(NodeGesture.hasHandlers(for: entity))
    }

    @MainActor
    func testOnScaleRegistersHandler() {
        let entity = ModelEntity(
            mesh: .generateBox(size: 1.0),
            materials: [SimpleMaterial()]
        )
        NodeGesture.onScale(entity) { _ in }
        XCTAssertTrue(NodeGesture.hasHandlers(for: entity))
    }

    @MainActor
    func testOnRotateRegistersHandler() {
        let entity = ModelEntity(
            mesh: .generateBox(size: 1.0),
            materials: [SimpleMaterial()]
        )
        NodeGesture.onRotate(entity) { _ in }
        XCTAssertTrue(NodeGesture.hasHandlers(for: entity))
    }

    @MainActor
    func testOnLongPressRegistersHandler() {
        let entity = ModelEntity(
            mesh: .generateBox(size: 1.0),
            materials: [SimpleMaterial()]
        )
        NodeGesture.onLongPress(entity) { }
        XCTAssertTrue(NodeGesture.hasHandlers(for: entity))
    }

    // MARK: - Dispatch

    @MainActor
    func testDispatchTapCallsHandler() {
        let entity = ModelEntity(
            mesh: .generateBox(size: 1.0),
            materials: [SimpleMaterial()]
        )
        var tapped = false
        NodeGesture.onTap(entity) { tapped = true }
        NodeGesture.dispatchTap(on: entity)
        XCTAssertTrue(tapped)
    }

    @MainActor
    func testDispatchDragCallsHandler() {
        let entity = ModelEntity(
            mesh: .generateBox(size: 1.0),
            materials: [SimpleMaterial()]
        )
        var receivedTranslation: SIMD3<Float>?
        NodeGesture.onDrag(entity) { t in receivedTranslation = t }
        NodeGesture.dispatchDrag(on: entity, translation: [1, 2, 3])
        XCTAssertEqual(receivedTranslation?.x ?? 0, 1.0, accuracy: 0.001)
    }

    @MainActor
    func testDispatchScaleCallsHandler() {
        let entity = ModelEntity(
            mesh: .generateBox(size: 1.0),
            materials: [SimpleMaterial()]
        )
        var receivedScale: Float?
        NodeGesture.onScale(entity) { s in receivedScale = s }
        NodeGesture.dispatchScale(on: entity, magnification: 2.0)
        XCTAssertEqual(receivedScale ?? 0, 2.0, accuracy: 0.001)
    }

    // MARK: - Removal

    @MainActor
    func testRemoveAllFromEntity() {
        let entity = ModelEntity(
            mesh: .generateBox(size: 1.0),
            materials: [SimpleMaterial()]
        )
        NodeGesture.onTap(entity) { }
        NodeGesture.onDrag(entity) { _ in }
        XCTAssertTrue(NodeGesture.hasHandlers(for: entity))

        NodeGesture.removeAll(from: entity)
        XCTAssertFalse(NodeGesture.hasHandlers(for: entity))
    }

    @MainActor
    func testRemoveAllHandlers() {
        let entity1 = ModelEntity(
            mesh: .generateBox(size: 1.0),
            materials: [SimpleMaterial()]
        )
        let entity2 = ModelEntity(
            mesh: .generateBox(size: 1.0),
            materials: [SimpleMaterial()]
        )
        NodeGesture.onTap(entity1) { }
        NodeGesture.onTap(entity2) { }

        NodeGesture.removeAllHandlers()
        XCTAssertFalse(NodeGesture.hasHandlers(for: entity1))
        XCTAssertFalse(NodeGesture.hasHandlers(for: entity2))
    }

    @MainActor
    func testHasHandlersReturnsFalseForUnregistered() {
        let entity = Entity()
        XCTAssertFalse(NodeGesture.hasHandlers(for: entity))
    }
}

#endif // os(iOS) || os(macOS) || os(visionOS)
