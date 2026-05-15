import XCTest
@testable import SceneViewSwift

#if os(iOS) || os(macOS) || os(visionOS)
import RealityKit

// Test classes run on the main actor: their RealityKit node factories
// (`LightNode.directional`, `node.entity`, …) are `@MainActor`. (#1054)
@MainActor
final class GeometryNodeTests: XCTestCase {

    // MARK: - Factory methods

    func testCubeCreatesEntity() {
        let cube = GeometryNode.cube(size: 0.5, color: .red)
        XCTAssertNotNil(cube.entity)
        XCTAssertNotNil(cube.entity.model)
    }

    func testSphereCreatesEntity() {
        let sphere = GeometryNode.sphere(radius: 0.3, color: .blue)
        XCTAssertNotNil(sphere.entity)
        XCTAssertNotNil(sphere.entity.model)
    }

    func testCylinderCreatesEntity() {
        let cyl = GeometryNode.cylinder(radius: 0.2, height: 0.8, color: .green)
        XCTAssertNotNil(cyl.entity)
        XCTAssertNotNil(cyl.entity.model)
    }

    func testPlaneCreatesEntity() {
        let plane = GeometryNode.plane(width: 2.0, depth: 1.5, color: .gray)
        XCTAssertNotNil(plane.entity)
        XCTAssertNotNil(plane.entity.model)
    }

    func testConeCreatesEntity() {
        let cone = GeometryNode.cone(height: 1.0, radius: 0.5, color: .yellow)
        XCTAssertNotNil(cone.entity)
        XCTAssertNotNil(cone.entity.model)
    }

    // MARK: - Materials

    func testCubeWithPBRMaterial() {
        let cube = GeometryNode.cube(
            size: 1.0,
            material: .pbr(color: .gray, metallic: 1.0, roughness: 0.2)
        )
        XCTAssertNotNil(cube.entity.model)
        XCTAssertEqual(cube.entity.model?.materials.count, 1)
    }

    func testCubeWithSimpleMaterial() {
        let cube = GeometryNode.cube(
            size: 1.0,
            material: .simple(color: .red)
        )
        XCTAssertNotNil(cube.entity.model)
    }

    func testSphereWithUnlitMaterial() {
        let sphere = GeometryNode.sphere(
            radius: 0.5,
            material: .unlit(color: .cyan)
        )
        XCTAssertNotNil(sphere.entity.model)
        XCTAssertEqual(sphere.entity.model?.materials.count, 1)
    }

    // MARK: - Transform helpers

    func testPositionSetsEntityPosition() {
        let cube = GeometryNode.cube(size: 1.0)
        let pos = SIMD3<Float>(1, 2, 3)
        cube.position(pos)

        XCTAssertEqual(cube.entity.position.x, 1.0, accuracy: 0.001)
        XCTAssertEqual(cube.entity.position.y, 2.0, accuracy: 0.001)
        XCTAssertEqual(cube.entity.position.z, 3.0, accuracy: 0.001)
    }

    func testScaleSetsUniformScale() {
        let sphere = GeometryNode.sphere(radius: 0.5)
        sphere.scale(2.0)

        XCTAssertEqual(sphere.entity.scale.x, 2.0, accuracy: 0.001)
        XCTAssertEqual(sphere.entity.scale.y, 2.0, accuracy: 0.001)
        XCTAssertEqual(sphere.entity.scale.z, 2.0, accuracy: 0.001)
    }

    func testPositionPropertyGetSet() {
        var node = GeometryNode.cube()
        node.position = SIMD3<Float>(5, 6, 7)

        XCTAssertEqual(node.position.x, 5.0, accuracy: 0.001)
        XCTAssertEqual(node.position.y, 6.0, accuracy: 0.001)
        XCTAssertEqual(node.position.z, 7.0, accuracy: 0.001)
    }

    func testScalePropertyGetSet() {
        var node = GeometryNode.sphere()
        node.scale = SIMD3<Float>(2, 3, 4)

        XCTAssertEqual(node.scale.x, 2.0, accuracy: 0.001)
        XCTAssertEqual(node.scale.y, 3.0, accuracy: 0.001)
        XCTAssertEqual(node.scale.z, 4.0, accuracy: 0.001)
    }

    // MARK: - Default values

    func testCubeDefaultSize() {
        let cube = GeometryNode.cube()
        // Default position should be zero
        XCTAssertEqual(cube.entity.position.x, 0.0, accuracy: 0.001)
        XCTAssertEqual(cube.entity.position.y, 0.0, accuracy: 0.001)
        XCTAssertEqual(cube.entity.position.z, 0.0, accuracy: 0.001)
    }

    func testChainingReturnsModifiedNode() {
        let node = GeometryNode.cube(size: 0.5, color: .red)
            .position(.init(x: 1, y: 2, z: 3))
            .scale(0.5)

        XCTAssertEqual(node.position.x, 1.0, accuracy: 0.001)
        XCTAssertEqual(node.scale.x, 0.5, accuracy: 0.001)
    }

    // MARK: - Corner radius

    func testCubeWithCornerRadius() {
        let cube = GeometryNode.cube(size: 1.0, cornerRadius: 0.1)
        XCTAssertNotNil(cube.entity)
        XCTAssertNotNil(cube.entity.model)
    }

    func testCubeWithPBRMaterialAndCornerRadius() {
        let cube = GeometryNode.cube(
            size: 1.0,
            material: .pbr(color: .blue, metallic: 0.5, roughness: 0.5),
            cornerRadius: 0.2
        )
        XCTAssertNotNil(cube.entity)
        XCTAssertNotNil(cube.entity.model)
    }

    func testCubeWithZeroCornerRadius() {
        let cube = GeometryNode.cube(size: 1.0, cornerRadius: 0.0)
        XCTAssertNotNil(cube.entity.model)
    }

    // MARK: - Collision shapes

    func testCubeHasCollisionComponent() {
        let cube = GeometryNode.cube(size: 1.0)
        XCTAssertNotNil(cube.entity.components[CollisionComponent.self])
    }

    func testSphereHasCollisionComponent() {
        let sphere = GeometryNode.sphere(radius: 0.5)
        XCTAssertNotNil(sphere.entity.components[CollisionComponent.self])
    }

    func testCylinderHasCollisionComponent() {
        let cyl = GeometryNode.cylinder()
        XCTAssertNotNil(cyl.entity.components[CollisionComponent.self])
    }

    func testPlaneHasCollisionComponent() {
        let plane = GeometryNode.plane()
        XCTAssertNotNil(plane.entity.components[CollisionComponent.self])
    }

    func testConeHasCollisionComponent() {
        let cone = GeometryNode.cone()
        XCTAssertNotNil(cone.entity.components[CollisionComponent.self])
    }

    // MARK: - Grounding shadow

    func testWithGroundingShadowDoesNotCrash() {
        let cube = GeometryNode.cube(size: 0.5, color: .red)
            .withGroundingShadow()
        XCTAssertNotNil(cube.entity)
    }

    func testWithGroundingShadowChaining() {
        let node = GeometryNode.sphere(radius: 0.3)
            .position(.init(x: 0, y: 1, z: 0))
            .scale(2.0)
            .withGroundingShadow()
        XCTAssertEqual(node.position.y, 1.0, accuracy: 0.001)
        XCTAssertEqual(node.scale.x, 2.0, accuracy: 0.001)
    }

    // MARK: - Material count

    func testAllFactoryMethodsProduceSingleMaterial() {
        let cube = GeometryNode.cube()
        let sphere = GeometryNode.sphere()
        let cyl = GeometryNode.cylinder()
        let plane = GeometryNode.plane()
        let cone = GeometryNode.cone()

        XCTAssertEqual(cube.entity.model?.materials.count, 1)
        XCTAssertEqual(sphere.entity.model?.materials.count, 1)
        XCTAssertEqual(cyl.entity.model?.materials.count, 1)
        XCTAssertEqual(plane.entity.model?.materials.count, 1)
        XCTAssertEqual(cone.entity.model?.materials.count, 1)
    }
}
#endif
