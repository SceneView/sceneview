import XCTest
@testable import SceneViewSwift

#if os(iOS) || os(macOS) || os(visionOS)
import RealityKit
import AVFoundation

/// Verifies all node types can be instantiated, have non-nil entities,
/// and support builder method chaining on all Apple platforms.
// Test classes run on the main actor: their RealityKit node factories
// (`LightNode.directional`, `node.entity`, …) are `@MainActor`. (#1054)
@MainActor
final class PlatformTests: XCTestCase {

    // MARK: - Instantiation & entity non-nil

    func testGeometryNodeCubeInstantiation() {
        let node = GeometryNode.cube()
        XCTAssertNotNil(node.entity)
    }

    func testGeometryNodeSphereInstantiation() {
        let node = GeometryNode.sphere()
        XCTAssertNotNil(node.entity)
    }

    func testGeometryNodeCylinderInstantiation() {
        let node = GeometryNode.cylinder()
        XCTAssertNotNil(node.entity)
    }

    func testGeometryNodePlaneInstantiation() {
        let node = GeometryNode.plane()
        XCTAssertNotNil(node.entity)
    }

    func testGeometryNodeConeInstantiation() {
        let node = GeometryNode.cone()
        XCTAssertNotNil(node.entity)
    }

    func testModelNodeInstantiation() {
        let node = ModelNode(ModelEntity())
        XCTAssertNotNil(node.entity)
    }

    func testLightNodeDirectionalInstantiation() {
        let node = LightNode.directional()
        XCTAssertNotNil(node.entity)
    }

    func testLightNodePointInstantiation() {
        let node = LightNode.point()
        XCTAssertNotNil(node.entity)
    }

    func testLightNodeSpotInstantiation() {
        let node = LightNode.spot()
        XCTAssertNotNil(node.entity)
    }

    func testTextNodeInstantiation() {
        let node = TextNode(text: "Test")
        XCTAssertNotNil(node.entity)
    }

    func testLineNodeInstantiation() {
        let node = LineNode(from: .zero, to: SIMD3<Float>(1, 0, 0))
        XCTAssertNotNil(node.entity)
    }

    func testPathNodeInstantiation() {
        let node = PathNode(points: [.zero, SIMD3<Float>(1, 0, 0)])
        XCTAssertNotNil(node.entity)
    }

    func testImageNodeInstantiation() {
        let node = ImageNode.color(.red)
        XCTAssertNotNil(node.entity)
    }

    func testVideoNodeInstantiation() {
        let player = AVPlayer()
        let node = VideoNode.create(player: player)
        XCTAssertNotNil(node.entity)
    }

    func testCameraNodeInstantiation() {
        let node = CameraNode()
        XCTAssertNotNil(node.entity)
    }

    func testBillboardNodeInstantiation() {
        let child = Entity()
        let node = BillboardNode(child: child)
        XCTAssertNotNil(node.entity)
    }

    func testFogNodeLinearInstantiation() {
        let node = FogNode.linear(start: 1.0, end: 10.0)
        XCTAssertNotNil(node.entity)
    }

    func testReflectionProbeNodeBoxInstantiation() {
        let node = ReflectionProbeNode.box(size: SIMD3<Float>(2, 2, 2))
        XCTAssertNotNil(node.entity)
    }

    func testDynamicSkyNodeNoonInstantiation() {
        let node = DynamicSkyNode.noon()
        XCTAssertNotNil(node.entity)
    }

    // MARK: - Builder method chaining for all node types

    func testGeometryNodeChaining() {
        let node = GeometryNode.cube(size: 0.5, color: .red)
            .position(SIMD3<Float>(1, 2, 3))
            .scale(0.5)
            .withGroundingShadow()
        XCTAssertEqual(node.position.x, 1.0, accuracy: 0.001)
        XCTAssertEqual(node.scale.x, 0.5, accuracy: 0.001)
    }

    func testModelNodeChaining() {
        let mesh = MeshResource.generateBox(size: 1.0)
        let material = SimpleMaterial(color: .white, isMetallic: false)
        let entity = ModelEntity(mesh: mesh, materials: [material])
        let node = ModelNode(entity)
            .position(SIMD3<Float>(1, 2, 3))
            .scale(2.0)
            .rotation(angle: .pi / 4, axis: SIMD3<Float>(0, 1, 0))
            .setColor(.red)
            .opacity(0.8)
            .setMetallic(0.5)
            .setRoughness(0.3)
        XCTAssertEqual(node.position.x, 1.0, accuracy: 0.001)
        XCTAssertEqual(node.scale.x, 2.0, accuracy: 0.001)
    }

    func testLightNodeChaining() {
        let node = LightNode.directional()
            .position(SIMD3<Float>(0, 5, 0))
            .castsShadow(true)
            .shadowMaximumDistance(20.0)
            .shadowColor(.warm)
            .attenuationRadius(10.0)
            .lookAt(.zero)
        XCTAssertEqual(node.position.y, 5.0, accuracy: 0.001)
    }

    func testTextNodeChaining() {
        let node = TextNode(text: "Hello", fontSize: 0.1, color: .blue)
            .position(SIMD3<Float>(0, 1, -2))
            .scale(2.0)
            .centered()
        XCTAssertNotNil(node.entity)
        XCTAssertEqual(node.text, "Hello")
    }

    func testLineNodeEntityHasModel() {
        let node = LineNode(
            from: .zero,
            to: SIMD3<Float>(1, 1, 0),
            thickness: 0.01,
            color: .green
        )
        XCTAssertNotNil(node.entity)
        XCTAssertNotNil(node.entity.model)
    }

    func testPathNodeChaining() {
        let node = PathNode(
            points: [.zero, SIMD3<Float>(1, 0, 0), SIMD3<Float>(1, 1, 0)],
            closed: true,
            thickness: 0.01,
            color: .yellow
        ).position(SIMD3<Float>(0, 1, 0))
        XCTAssertEqual(node.entity.position.y, 1.0, accuracy: 0.001)
    }

    func testImageNodeChaining() {
        let node = ImageNode.color(.green, width: 2.0, height: 1.5)
            .position(SIMD3<Float>(0, 1, -3))
            .scale(0.5)
            .rotation(angle: .pi / 2, axis: SIMD3<Float>(1, 0, 0))
            .size(width: 3.0, height: 2.0)
        XCTAssertEqual(node.position.y, 1.0, accuracy: 0.001)
    }

    func testVideoNodeChaining() {
        let player = AVPlayer()
        let node = VideoNode.create(player: player)
            .position(SIMD3<Float>(0, 2, -5))
            .rotation(simd_quatf(angle: .pi / 6, axis: SIMD3<Float>(0, 1, 0)))
            .size(width: 3.2, height: 1.8)
        XCTAssertEqual(node.position.y, 2.0, accuracy: 0.001)
        XCTAssertEqual(node.scale.x, 3.2, accuracy: 0.001)
    }

    func testCameraNodeChaining() {
        let node = CameraNode()
            .position(SIMD3<Float>(0, 1.5, 3))
            .lookAt(.zero)
            .rotation(simd_quatf(angle: .pi / 4, axis: SIMD3<Float>(0, 1, 0)))
        XCTAssertEqual(node.position.y, 1.5, accuracy: 0.001)
    }

    #if !os(macOS)
    func testCameraNodeFullChaining() {
        let node = CameraNode()
            .position(SIMD3<Float>(0, 1.5, 3))
            .lookAt(.zero)
            .fieldOfView(60.0)
            .clipPlanes(near: 0.1, far: 500)
            .exposure(1.5)
            .depthOfField(focusDistance: 2.0, aperture: 1.4)
        XCTAssertEqual(node.position.y, 1.5, accuracy: 0.001)
        XCTAssertNotNil(node.entity.components[PerspectiveCameraComponent.self])
    }
    #endif

    func testBillboardNodeChaining() {
        let child = Entity()
        let node = BillboardNode(child: child)
            .position(SIMD3<Float>(0, 2, -1))
        XCTAssertEqual(node.position.y, 2.0, accuracy: 0.001)
    }

    func testFogNodeChaining() {
        let node = FogNode.linear(start: 1.0, end: 20.0)
            .color(.cool)
        XCTAssertNotNil(node.entity)
    }

    func testReflectionProbeNodeChaining() {
        let node = ReflectionProbeNode.box(size: SIMD3<Float>(4, 3, 4))
            .position(SIMD3<Float>(0, 1.5, 0))
            .intensity(1.0)
        XCTAssertEqual(node.position.y, 1.5, accuracy: 0.001)
    }

    func testDynamicSkyNodeChaining() {
        let node = DynamicSkyNode.noon()
            .position(SIMD3<Float>(0, 5, 0))
        XCTAssertEqual(node.entity.position.y, 5.0, accuracy: 0.001)
    }

    // MARK: - Entity names

    func testCameraNodeEntityName() {
        let node = CameraNode()
        XCTAssertEqual(node.entity.name, "CameraNode")
    }

    func testVideoNodeEntityName() {
        let player = AVPlayer()
        let node = VideoNode.create(player: player)
        XCTAssertEqual(node.entity.name, "VideoNode")
    }
}
#endif
