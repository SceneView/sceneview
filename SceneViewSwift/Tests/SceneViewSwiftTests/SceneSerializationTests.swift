import XCTest
@testable import SceneViewSwift

#if os(iOS) || os(macOS) || os(visionOS)
import RealityKit

// Test classes run on the main actor: their RealityKit node factories
// (`LightNode.directional`, `node.entity`, …) are `@MainActor`. (#1054)
@MainActor
final class SceneSerializationTests: XCTestCase {

    // MARK: - SIMD3CodableFloat

    func testSIMD3CodableRoundTrip() {
        let original = SIMD3CodableFloat(SIMD3<Float>(1.5, 2.5, 3.5))
        XCTAssertEqual(original.x, 1.5, accuracy: 0.001)
        XCTAssertEqual(original.y, 2.5, accuracy: 0.001)
        XCTAssertEqual(original.z, 3.5, accuracy: 0.001)

        let simd = original.simd
        XCTAssertEqual(simd.x, 1.5, accuracy: 0.001)
        XCTAssertEqual(simd.y, 2.5, accuracy: 0.001)
        XCTAssertEqual(simd.z, 3.5, accuracy: 0.001)
    }

    func testSIMD4CodableRoundTrip() {
        let original = SIMD4CodableFloat(SIMD4<Float>(0.1, 0.2, 0.3, 0.9))
        XCTAssertEqual(original.x, 0.1, accuracy: 0.001)
        XCTAssertEqual(original.w, 0.9, accuracy: 0.001)

        let simd = original.simd4
        XCTAssertEqual(simd.x, 0.1, accuracy: 0.001)
        XCTAssertEqual(simd.w, 0.9, accuracy: 0.001)
    }

    // MARK: - NodeSnapshot

    func testNodeSnapshotFromEntity() {
        let entity = Entity()
        entity.name = "TestEntity"
        entity.position = SIMD3<Float>(1, 2, 3)
        entity.scale = SIMD3<Float>(0.5, 0.5, 0.5)

        let snapshot = NodeSnapshot(entity: entity)
        XCTAssertEqual(snapshot.name, "TestEntity")
        XCTAssertEqual(snapshot.position.x, 1.0, accuracy: 0.001)
        XCTAssertEqual(snapshot.scale.x, 0.5, accuracy: 0.001)
        XCTAssertTrue(snapshot.isEnabled)
    }

    func testNodeSnapshotApplyToEntity() {
        let entity = Entity()
        entity.name = "TestEntity"
        entity.position = SIMD3<Float>(1, 2, 3)

        let snapshot = NodeSnapshot(entity: entity)

        let target = Entity()
        target.name = "TestEntity"
        snapshot.apply(to: target)

        XCTAssertEqual(target.position.x, 1.0, accuracy: 0.001)
        XCTAssertEqual(target.position.y, 2.0, accuracy: 0.001)
        XCTAssertEqual(target.position.z, 3.0, accuracy: 0.001)
    }

    // MARK: - SceneState

    func testSceneStateCapture() {
        let root = Entity()
        root.name = "Root"

        let child = Entity()
        child.name = "Child"
        child.position = [1, 0, 0]
        root.addChild(child)

        let state = SceneState(root: root)
        XCTAssertEqual(state.nodes.count, 2)
    }

    func testSceneStateWithMetadata() {
        let root = Entity()
        root.name = "Root"

        let state = SceneState(
            root: root,
            metadata: ["version": "1.0", "author": "test"]
        )
        XCTAssertEqual(state.metadata["version"], "1.0")
        XCTAssertEqual(state.metadata["author"], "test")
    }

    func testSceneStateApply() {
        // Create original scene
        let root = Entity()
        root.name = "Root"
        let child = Entity()
        child.name = "Child"
        child.position = [5, 5, 5]
        root.addChild(child)

        // Capture state
        let state = SceneState(root: root)

        // Modify positions
        child.position = [0, 0, 0]

        // Restore
        state.apply(to: root)
        XCTAssertEqual(child.position.x, 5.0, accuracy: 0.001)
    }

    func testSceneStateSkipsUnnamedEntities() {
        let root = Entity()
        root.name = "Root"
        let unnamed = Entity()  // No name
        root.addChild(unnamed)

        let state = SceneState(root: root)
        // Only named entities are captured
        XCTAssertEqual(state.nodes.count, 1)
    }

    // MARK: - SceneSerializer

    func testEncodeDecodeRoundTrip() throws {
        let root = Entity()
        root.name = "Root"
        let child = Entity()
        child.name = "Child"
        child.position = [3, 4, 5]
        child.scale = [2, 2, 2]
        root.addChild(child)

        let state = SceneState(root: root)
        let data = try SceneSerializer.encode(state)
        let decoded = try SceneSerializer.decode(from: data)

        XCTAssertEqual(decoded.nodes.count, 2)

        let childSnapshot = decoded.nodes.first(where: { $0.name == "Child" })
        XCTAssertNotNil(childSnapshot)
        XCTAssertEqual(childSnapshot?.position.x ?? 0, 3.0, accuracy: 0.001)
        XCTAssertEqual(childSnapshot?.scale.x ?? 0, 2.0, accuracy: 0.001)
    }

    func testEncodeDecodeStringRoundTrip() throws {
        let root = Entity()
        root.name = "Root"

        let state = SceneState(root: root)
        let jsonString = try SceneSerializer.encodeToString(state)
        XCTAssertTrue(jsonString.contains("Root"))

        let decoded = try SceneSerializer.decode(from: jsonString)
        XCTAssertEqual(decoded.nodes.first?.name, "Root")
    }

    func testDecodeInvalidStringThrows() {
        XCTAssertThrowsError(try SceneSerializer.decode(from: "not valid json"))
    }

    func testDecodeInvalidDataThrows() {
        let badData = Data([0xFF, 0xFE])
        XCTAssertThrowsError(try SceneSerializer.decode(from: badData))
    }

    // MARK: - SceneSerializerError

    func testErrorDescriptions() {
        XCTAssertNotNil(SceneSerializerError.encodingFailed.errorDescription)
        XCTAssertNotNil(SceneSerializerError.invalidString.errorDescription)
    }
}

#endif // os(iOS) || os(macOS) || os(visionOS)
