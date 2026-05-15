import XCTest
@testable import SceneViewSwift

#if os(iOS) || os(macOS) || os(visionOS)
import RealityKit
import AVFoundation

// Test classes run on the main actor: their RealityKit node factories
// (`LightNode.directional`, `node.entity`, …) are `@MainActor`. (#1054)
@MainActor
final class EdgeCaseTests: XCTestCase {

    // MARK: - GeometryNode zero/negative dimensions

    func testCubeWithZeroSize() {
        let cube = GeometryNode.cube(size: 0.0)
        XCTAssertNotNil(cube.entity)
        XCTAssertNotNil(cube.entity.model)
    }

    func testCubeWithNegativeSize() {
        // RealityKit may clamp or handle negative sizes; should not crash
        let cube = GeometryNode.cube(size: -1.0)
        XCTAssertNotNil(cube.entity)
    }

    func testSphereWithZeroRadius() {
        let sphere = GeometryNode.sphere(radius: 0.0)
        XCTAssertNotNil(sphere.entity)
        XCTAssertNotNil(sphere.entity.model)
    }

    func testSphereWithNegativeRadius() {
        let sphere = GeometryNode.sphere(radius: -0.5)
        XCTAssertNotNil(sphere.entity)
    }

    func testCylinderWithZeroDimensions() {
        let cyl = GeometryNode.cylinder(radius: 0.0, height: 0.0)
        XCTAssertNotNil(cyl.entity)
        XCTAssertNotNil(cyl.entity.model)
    }

    func testCylinderWithNegativeHeight() {
        let cyl = GeometryNode.cylinder(radius: 0.5, height: -1.0)
        XCTAssertNotNil(cyl.entity)
    }

    func testPlaneWithZeroDimensions() {
        let plane = GeometryNode.plane(width: 0.0, depth: 0.0)
        XCTAssertNotNil(plane.entity)
        XCTAssertNotNil(plane.entity.model)
    }

    func testPlaneWithNegativeDimensions() {
        let plane = GeometryNode.plane(width: -1.0, depth: -2.0)
        XCTAssertNotNil(plane.entity)
    }

    func testConeWithZeroDimensions() {
        let cone = GeometryNode.cone(height: 0.0, radius: 0.0)
        XCTAssertNotNil(cone.entity)
        XCTAssertNotNil(cone.entity.model)
    }

    func testConeWithNegativeDimensions() {
        let cone = GeometryNode.cone(height: -1.0, radius: -0.5)
        XCTAssertNotNil(cone.entity)
    }

    // MARK: - LightNode intensity edge cases

    func testDirectionalLightWithZeroIntensity() {
        let light = LightNode.directional(intensity: 0)
        XCTAssertNotNil(light.entity)
        XCTAssertTrue(light.entity is DirectionalLight)
    }

    func testPointLightWithZeroIntensity() {
        let light = LightNode.point(intensity: 0)
        XCTAssertNotNil(light.entity)
        XCTAssertTrue(light.entity is PointLight)
    }

    func testSpotLightWithZeroIntensity() {
        let light = LightNode.spot(intensity: 0)
        XCTAssertNotNil(light.entity)
        XCTAssertTrue(light.entity is SpotLight)
    }

    func testDirectionalLightWithVeryHighIntensity() {
        let light = LightNode.directional(intensity: 1_000_000)
        XCTAssertNotNil(light.entity)
        XCTAssertTrue(light.entity is DirectionalLight)
    }

    func testPointLightWithVeryHighIntensity() {
        let light = LightNode.point(intensity: 1_000_000)
        XCTAssertNotNil(light.entity)
        XCTAssertTrue(light.entity is PointLight)
    }

    func testSpotLightWithVeryHighIntensity() {
        let light = LightNode.spot(intensity: 1_000_000)
        XCTAssertNotNil(light.entity)
        XCTAssertTrue(light.entity is SpotLight)
    }

    // MARK: - TextNode edge cases

    func testTextNodeWithEmptyString() {
        let node = TextNode(text: "")
        XCTAssertNotNil(node.entity)
        XCTAssertEqual(node.text, "")
        // Entity should still be created even with empty text
        XCTAssertNotNil(node.entity.model)
    }

    func testTextNodeWithVeryLongString() {
        let longString = String(repeating: "A", count: 10_000)
        let node = TextNode(text: longString)
        XCTAssertNotNil(node.entity)
        XCTAssertEqual(node.text, longString)
        XCTAssertNotNil(node.entity.model)
    }

    func testTextNodeWithSpecialCharacters() {
        let node = TextNode(text: "Hello\nWorld\t!\u{00A0}")
        XCTAssertNotNil(node.entity)
        XCTAssertNotNil(node.entity.model)
    }

    // MARK: - LineNode edge cases

    func testLineNodeWithIdenticalStartEndPoints() {
        let point = SIMD3<Float>(1, 2, 3)
        let line = LineNode(from: point, to: point)
        // Should not crash — zero-length line
        XCTAssertNotNil(line.entity)
        XCTAssertNotNil(line.entity.model)
        // Entity positioned at the point (midpoint of identical points)
        XCTAssertEqual(line.entity.position.x, 1.0, accuracy: 0.01)
        XCTAssertEqual(line.entity.position.y, 2.0, accuracy: 0.01)
        XCTAssertEqual(line.entity.position.z, 3.0, accuracy: 0.01)
    }

    func testLineNodeWithVeryLongDistance() {
        let line = LineNode(
            from: .zero,
            to: SIMD3<Float>(1000, 1000, 1000)
        )
        XCTAssertNotNil(line.entity)
        XCTAssertNotNil(line.entity.model)
    }

    // MARK: - PathNode edge cases

    func testPathNodeWithZeroPoints() {
        let path = PathNode(points: [])
        XCTAssertNotNil(path.entity)
        XCTAssertEqual(path.entity.children.count, 0)
        XCTAssertTrue(path.points.isEmpty)
    }

    func testPathNodeWithOnePoint() {
        let path = PathNode(points: [.zero])
        XCTAssertNotNil(path.entity)
        XCTAssertEqual(path.entity.children.count, 0)
        XCTAssertEqual(path.points.count, 1)
    }

    func testPathNodeWithTwoPoints() {
        let path = PathNode(
            points: [.zero, SIMD3<Float>(1, 0, 0)]
        )
        XCTAssertNotNil(path.entity)
        XCTAssertEqual(path.entity.children.count, 1)
        XCTAssertEqual(path.points.count, 2)
    }

    func testPathNodeClosedWithTwoPoints() {
        let path = PathNode(
            points: [.zero, SIMD3<Float>(1, 0, 0)],
            closed: true
        )
        XCTAssertNotNil(path.entity)
        // 1 segment + 1 closing segment = 2
        XCTAssertEqual(path.entity.children.count, 2)
    }

    func testPathNodeWithDuplicatePoints() {
        let path = PathNode(
            points: [.zero, .zero, .zero]
        )
        // Should not crash with all identical points (zero-length segments)
        XCTAssertNotNil(path.entity)
        XCTAssertEqual(path.entity.children.count, 2)
    }

    // MARK: - ImageNode edge cases

    func testImageNodeColorWithAlphaZero() {
        #if canImport(UIKit)
        let transparentColor = UIColor(red: 1, green: 0, blue: 0, alpha: 0)
        #elseif canImport(AppKit)
        let transparentColor = NSColor(red: 1, green: 0, blue: 0, alpha: 0)
        #endif
        let node = ImageNode.color(transparentColor, width: 1.0, height: 1.0)
        XCTAssertNotNil(node.entity)
        XCTAssertNotNil(node.entity.model)
    }

    func testImageNodeWithZeroDimensions() {
        let node = ImageNode.color(.red, width: 0.0, height: 0.0)
        XCTAssertNotNil(node.entity)
    }

    func testImageNodeResizeToZero() {
        let node = ImageNode.color(.blue)
            .size(width: 0.0, height: 0.0)
        XCTAssertNotNil(node.entity)
    }

    // MARK: - VideoNode edge cases

    func testVideoNodeVolumeAtZero() {
        let player = AVPlayer()
        let node = VideoNode.create(player: player)
        node.volume(0.0)
        XCTAssertEqual(node.player.volume, 0.0, accuracy: 0.001)
    }

    func testVideoNodeVolumeAtMax() {
        let player = AVPlayer()
        let node = VideoNode.create(player: player)
        node.volume(1.0)
        XCTAssertEqual(node.player.volume, 1.0, accuracy: 0.001)
    }

    func testVideoNodeVolumeAboveMax() {
        let player = AVPlayer()
        let node = VideoNode.create(player: player)
        // AVPlayer clamps volume, should not crash
        node.volume(5.0)
        XCTAssertNotNil(node.entity)
    }

    func testVideoNodeVolumeNegative() {
        let player = AVPlayer()
        let node = VideoNode.create(player: player)
        // Negative volume should not crash
        node.volume(-1.0)
        XCTAssertNotNil(node.entity)
    }

    func testVideoNodeSizeZero() {
        let player = AVPlayer()
        let node = VideoNode.create(player: player)
            .size(width: 0.0, height: 0.0)
        XCTAssertNotNil(node.entity)
    }

    // MARK: - CameraNode edge cases

    #if !os(macOS)
    func testCameraNodeClipPlanesNearGreaterThanFar() {
        let camera = CameraNode()
            .clipPlanes(near: 100.0, far: 0.1)
        // Should accept the values even if near > far
        XCTAssertEqual(camera.nearClip, 100.0, accuracy: 0.001)
        XCTAssertEqual(camera.farClip, 0.1, accuracy: 0.001)
    }

    func testCameraNodeClipPlanesZero() {
        let camera = CameraNode()
            .clipPlanes(near: 0.0, far: 0.0)
        XCTAssertEqual(camera.nearClip, 0.0, accuracy: 0.001)
        XCTAssertEqual(camera.farClip, 0.0, accuracy: 0.001)
    }

    func testCameraNodeClipPlanesNegative() {
        let camera = CameraNode()
            .clipPlanes(near: -1.0, far: -0.5)
        // Should not crash with negative values
        XCTAssertNotNil(camera.entity)
    }

    func testCameraNodeClipPlanesVeryLargeFar() {
        let camera = CameraNode()
            .clipPlanes(near: 0.01, far: 1_000_000)
        XCTAssertEqual(camera.nearClip, 0.01, accuracy: 0.001)
        XCTAssertEqual(camera.farClip, 1_000_000, accuracy: 1.0)
    }
    #endif

    func testCameraNodeLookAtSamePosition() {
        let pos = SIMD3<Float>(0, 0, 0)
        let camera = CameraNode()
            .position(pos)
            .lookAt(pos)
        // Looking at own position is degenerate but should not crash
        XCTAssertNotNil(camera.entity)
    }
}
#endif
