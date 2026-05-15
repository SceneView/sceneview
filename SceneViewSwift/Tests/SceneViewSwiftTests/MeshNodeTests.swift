import XCTest
@testable import SceneViewSwift

#if os(iOS) || os(macOS) || os(visionOS)
import RealityKit

// Test classes run on the main actor: their RealityKit node factories
// (`LightNode.directional`, `node.entity`, …) are `@MainActor`. (#1054)
@MainActor
final class MeshNodeTests: XCTestCase {

    // MARK: - Test data

    /// A simple triangle: three vertices, one face.
    private static let trianglePositions: [SIMD3<Float>] = [
        SIMD3<Float>(0, 0.5, 0),
        SIMD3<Float>(-0.5, -0.5, 0),
        SIMD3<Float>(0.5, -0.5, 0)
    ]

    private static let triangleNormals: [SIMD3<Float>] = [
        SIMD3<Float>(0, 0, 1),
        SIMD3<Float>(0, 0, 1),
        SIMD3<Float>(0, 0, 1)
    ]

    private static let triangleUVs: [SIMD2<Float>] = [
        SIMD2<Float>(0.5, 1.0),
        SIMD2<Float>(0.0, 0.0),
        SIMD2<Float>(1.0, 0.0)
    ]

    private static let triangleIndices: [UInt32] = [0, 1, 2]

    // MARK: - Factory: fromVertices

    func testFromVerticesCreatesEntity() throws {
        let node = try MeshNode.fromVertices(
            positions: Self.trianglePositions,
            normals: Self.triangleNormals,
            uvs: Self.triangleUVs,
            indices: Self.triangleIndices,
            material: .simple(color: .red)
        )
        XCTAssertNotNil(node.entity)
        XCTAssertNotNil(node.entity.model)
    }

    func testFromVerticesWithoutNormalsOrUVs() throws {
        let node = try MeshNode.fromVertices(
            positions: Self.trianglePositions,
            indices: Self.triangleIndices
        )
        XCTAssertNotNil(node.entity)
        XCTAssertNotNil(node.entity.model)
    }

    func testFromVerticesWithPBRMaterial() throws {
        let node = try MeshNode.fromVertices(
            positions: Self.trianglePositions,
            normals: Self.triangleNormals,
            indices: Self.triangleIndices,
            material: .pbr(color: .gray, metallic: 1.0, roughness: 0.2)
        )
        XCTAssertNotNil(node.entity.model)
        XCTAssertEqual(node.entity.model?.materials.count, 1)
    }

    func testFromVerticesWithUnlitMaterial() throws {
        let node = try MeshNode.fromVertices(
            positions: Self.trianglePositions,
            indices: Self.triangleIndices,
            material: .unlit(color: .cyan)
        )
        XCTAssertNotNil(node.entity.model)
        XCTAssertEqual(node.entity.model?.materials.count, 1)
    }

    // MARK: - Factory: fromDescriptor

    func testFromDescriptorCreatesEntity() throws {
        var descriptor = MeshDescriptor(name: "TestMesh")
        descriptor.positions = MeshBuffers.Positions(Self.trianglePositions)
        descriptor.normals = MeshBuffers.Normals(Self.triangleNormals)
        descriptor.primitives = .triangles(Self.triangleIndices)

        let node = try MeshNode.fromDescriptor(descriptor, material: .simple(color: .blue))
        XCTAssertNotNil(node.entity)
        XCTAssertNotNil(node.entity.model)
    }

    func testFromDescriptorDefaultMaterial() throws {
        var descriptor = MeshDescriptor(name: "DefaultMatMesh")
        descriptor.positions = MeshBuffers.Positions(Self.trianglePositions)
        descriptor.primitives = .triangles(Self.triangleIndices)

        let node = try MeshNode.fromDescriptor(descriptor)
        XCTAssertNotNil(node.entity.model)
        XCTAssertEqual(node.entity.model?.materials.count, 1)
    }

    // MARK: - Transform helpers

    func testPositionSetsEntityPosition() throws {
        let node = try MeshNode.fromVertices(
            positions: Self.trianglePositions,
            indices: Self.triangleIndices
        )
        node.position(.init(x: 1, y: 2, z: 3))

        XCTAssertEqual(node.entity.position.x, 1.0, accuracy: 0.001)
        XCTAssertEqual(node.entity.position.y, 2.0, accuracy: 0.001)
        XCTAssertEqual(node.entity.position.z, 3.0, accuracy: 0.001)
    }

    func testScaleSetsUniformScale() throws {
        let node = try MeshNode.fromVertices(
            positions: Self.trianglePositions,
            indices: Self.triangleIndices
        )
        node.scale(2.0)

        XCTAssertEqual(node.entity.scale.x, 2.0, accuracy: 0.001)
        XCTAssertEqual(node.entity.scale.y, 2.0, accuracy: 0.001)
        XCTAssertEqual(node.entity.scale.z, 2.0, accuracy: 0.001)
    }

    func testScalePerAxis() throws {
        let node = try MeshNode.fromVertices(
            positions: Self.trianglePositions,
            indices: Self.triangleIndices
        )
        node.scale(SIMD3<Float>(1, 2, 3))

        XCTAssertEqual(node.entity.scale.x, 1.0, accuracy: 0.001)
        XCTAssertEqual(node.entity.scale.y, 2.0, accuracy: 0.001)
        XCTAssertEqual(node.entity.scale.z, 3.0, accuracy: 0.001)
    }

    func testRotationSetsOrientation() throws {
        let node = try MeshNode.fromVertices(
            positions: Self.trianglePositions,
            indices: Self.triangleIndices
        )
        let quat = simd_quatf(angle: .pi / 4, axis: SIMD3<Float>(0, 1, 0))
        node.rotation(quat)

        XCTAssertEqual(node.entity.orientation.angle, quat.angle, accuracy: 0.001)
    }

    func testRotationAngleAxis() throws {
        let node = try MeshNode.fromVertices(
            positions: Self.trianglePositions,
            indices: Self.triangleIndices
        )
        node.rotation(angle: .pi / 2, axis: SIMD3<Float>(0, 0, 1))

        XCTAssertEqual(node.entity.orientation.angle, .pi / 2, accuracy: 0.001)
    }

    // MARK: - Builder chaining

    func testChainingReturnsModifiedNode() throws {
        let node = try MeshNode.fromVertices(
            positions: Self.trianglePositions,
            normals: Self.triangleNormals,
            indices: Self.triangleIndices,
            material: .simple(color: .red)
        )
        .position(.init(x: 1, y: 2, z: 3))
        .scale(0.5)
        .rotation(angle: .pi, axis: SIMD3<Float>(0, 1, 0))

        XCTAssertEqual(node.position.x, 1.0, accuracy: 0.001)
        XCTAssertEqual(node.scale.x, 0.5, accuracy: 0.001)
        XCTAssertEqual(node.entity.orientation.angle, .pi, accuracy: 0.01)
    }

    func testPositionPropertyGetSet() throws {
        var node = try MeshNode.fromVertices(
            positions: Self.trianglePositions,
            indices: Self.triangleIndices
        )
        node.position = SIMD3<Float>(5, 6, 7)

        XCTAssertEqual(node.position.x, 5.0, accuracy: 0.001)
        XCTAssertEqual(node.position.y, 6.0, accuracy: 0.001)
        XCTAssertEqual(node.position.z, 7.0, accuracy: 0.001)
    }

    func testScalePropertyGetSet() throws {
        var node = try MeshNode.fromVertices(
            positions: Self.trianglePositions,
            indices: Self.triangleIndices
        )
        node.scale = SIMD3<Float>(2, 3, 4)

        XCTAssertEqual(node.scale.x, 2.0, accuracy: 0.001)
        XCTAssertEqual(node.scale.y, 3.0, accuracy: 0.001)
        XCTAssertEqual(node.scale.z, 4.0, accuracy: 0.001)
    }

    func testRotationPropertyGetSet() throws {
        var node = try MeshNode.fromVertices(
            positions: Self.trianglePositions,
            indices: Self.triangleIndices
        )
        let quat = simd_quatf(angle: .pi / 3, axis: SIMD3<Float>(1, 0, 0))
        node.rotation = quat

        XCTAssertEqual(node.rotation.angle, quat.angle, accuracy: 0.001)
    }

    // MARK: - Init with ModelEntity

    func testInitWithModelEntity() {
        let mesh = MeshResource.generateBox(size: 1.0)
        let material = SimpleMaterial(color: .white, isMetallic: false)
        let entity = ModelEntity(mesh: mesh, materials: [material])
        let node = MeshNode(entity)
        XCTAssertNotNil(node.entity)
        XCTAssertNotNil(node.entity.model)
    }

    // MARK: - Grounding shadow

    func testWithGroundingShadowDoesNotCrash() throws {
        let node = try MeshNode.fromVertices(
            positions: Self.trianglePositions,
            indices: Self.triangleIndices
        )
        .withGroundingShadow()
        XCTAssertNotNil(node.entity)
    }

    // MARK: - Collision shapes

    func testFromVerticesHasCollisionComponent() throws {
        let node = try MeshNode.fromVertices(
            positions: Self.trianglePositions,
            indices: Self.triangleIndices
        )
        XCTAssertNotNil(node.entity.components[CollisionComponent.self])
    }

    func testFromDescriptorHasCollisionComponent() throws {
        var descriptor = MeshDescriptor(name: "Test")
        descriptor.positions = MeshBuffers.Positions(Self.trianglePositions)
        descriptor.primitives = .triangles(Self.triangleIndices)

        let node = try MeshNode.fromDescriptor(descriptor)
        XCTAssertNotNil(node.entity.components[CollisionComponent.self])
    }

    // MARK: - Quad (two triangles)

    func testQuadMesh() throws {
        let positions: [SIMD3<Float>] = [
            SIMD3<Float>(-0.5, -0.5, 0),
            SIMD3<Float>( 0.5, -0.5, 0),
            SIMD3<Float>( 0.5,  0.5, 0),
            SIMD3<Float>(-0.5,  0.5, 0)
        ]
        let indices: [UInt32] = [0, 1, 2, 0, 2, 3]
        let node = try MeshNode.fromVertices(
            positions: positions,
            indices: indices,
            material: .simple(color: .green)
        )
        XCTAssertNotNil(node.entity.model)
    }

    // MARK: - Empty vertex data

    func testEmptyPositionsThrows() {
        XCTAssertThrowsError(
            try MeshNode.fromVertices(
                positions: [],
                indices: []
            )
        )
    }
}
#endif
