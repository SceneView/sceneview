import XCTest
@testable import SceneViewSwift

#if os(iOS) || os(macOS) || os(visionOS)
import RealityKit

final class CustomMaterialTests: XCTestCase {

    // MARK: - Glass

    func testGlassCreation() {
        let material = CustomMaterial.glass()
        XCTAssertNotNil(material)
    }

    func testGlassWithCustomParams() {
        let material = CustomMaterial.glass(
            tint: .blue,
            roughness: 0.1,
            opacity: 0.5,
            metallic: 0.2
        )
        XCTAssertNotNil(material)
    }

    // MARK: - Emissive

    func testEmissiveCreation() {
        let material = CustomMaterial.emissive(color: .red, intensity: 2.0)
        XCTAssertNotNil(material)
    }

    func testEmissiveDefaults() {
        let material = CustomMaterial.emissive()
        XCTAssertNotNil(material)
    }

    // MARK: - Clearcoat

    func testClearcoatCreation() {
        let material = CustomMaterial.clearcoat(baseColor: .red)
        XCTAssertNotNil(material)
    }

    func testClearcoatWithParams() {
        let material = CustomMaterial.clearcoat(
            baseColor: .blue,
            metallic: 0.9,
            roughness: 0.2,
            clearcoatRoughness: 0.05
        )
        XCTAssertNotNil(material)
    }

    // MARK: - Matte

    func testMatteCreation() {
        let material = CustomMaterial.matte(color: .white)
        XCTAssertNotNil(material)
    }

    // MARK: - Mirror

    func testMirrorCreation() {
        let material = CustomMaterial.mirror()
        XCTAssertNotNil(material)
    }

    func testMirrorWithTint() {
        let material = CustomMaterial.mirror(tint: .yellow)
        XCTAssertNotNil(material)
    }

    // MARK: - Subsurface

    func testSubsurfaceCreation() {
        let material = CustomMaterial.subsurface()
        XCTAssertNotNil(material)
    }

    // MARK: - Unlit

    func testUnlitCreation() {
        let material = CustomMaterial.unlit(color: .green)
        XCTAssertNotNil(material)
    }

    func testUnlitDefaultColor() {
        let material = CustomMaterial.unlit()
        XCTAssertNotNil(material)
    }

    // MARK: - Debug (deprecated alias)

    @available(*, deprecated)
    func testDebugAliasStillWorks() {
        let material = CustomMaterial.debug(color: .green)
        XCTAssertNotNil(material)
    }

    // MARK: - Integration with GeometryNode

    func testCustomMaterialOnGeometry() {
        let glassMat = CustomMaterial.glass(tint: .cyan, opacity: 0.4)
        let mesh = MeshResource.generateSphere(radius: 0.5)
        let entity = ModelEntity(mesh: mesh, materials: [glassMat])
        XCTAssertNotNil(entity.model)
        XCTAssertEqual(entity.model?.materials.count, 1)
    }
}

#endif // os(iOS) || os(macOS) || os(visionOS)
