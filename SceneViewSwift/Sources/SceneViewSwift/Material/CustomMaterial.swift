#if os(iOS) || os(macOS) || os(visionOS)
import RealityKit
import Foundation
#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

/// Extended material types beyond basic PBR.
///
/// Provides factory methods for creating specialized materials including
/// glass, emissive, clearcoat, and subsurface scattering materials using
/// RealityKit's `PhysicallyBasedMaterial`.
///
/// ```swift
/// // Glass material
/// let glass = CustomMaterial.glass(tint: .blue, roughness: 0.05)
/// let sphere = GeometryNode.sphere(radius: 0.3, material: .custom(glass))
///
/// // Emissive (glowing) material
/// let glow = CustomMaterial.emissive(color: .red, intensity: 2.0)
///
/// // Clearcoat (car paint)
/// let paint = CustomMaterial.clearcoat(
///     baseColor: .red,
///     clearcoatRoughness: 0.1
/// )
/// ```
public enum CustomMaterial {

    // MARK: - Glass / Transparent

    /// Creates a glass-like transparent material.
    ///
    /// Uses RealityKit's `PhysicallyBasedMaterial` with low roughness and
    /// transparent blending for a glass effect.
    ///
    /// - Parameters:
    ///   - tint: Color tint of the glass. Default white (clear).
    ///   - roughness: Surface roughness. 0 = perfect mirror, 1 = frosted. Default 0.05.
    ///   - opacity: Transparency. 0 = invisible, 1 = opaque. Default 0.3.
    ///   - metallic: Metallic factor. Default 0.0.
    /// - Returns: A configured RealityKit material.
    public static func glass(
        tint: SimpleMaterial.Color = .white,
        roughness: Float = 0.05,
        opacity: Float = 0.3,
        metallic: Float = 0.0
    ) -> any RealityKit.Material {
        var mat = PhysicallyBasedMaterial()
        mat.baseColor = .init(tint: tint)
        mat.roughness = .init(floatLiteral: roughness)
        mat.metallic = .init(floatLiteral: metallic)
        mat.blending = .transparent(opacity: .init(floatLiteral: opacity))
        return mat
    }

    // MARK: - Emissive (Glow)

    /// Creates an emissive (glowing) material.
    ///
    /// The material emits light and appears to glow. Useful for neon signs,
    /// energy effects, or highlighted UI elements.
    ///
    /// - Parameters:
    ///   - color: Emission color. Default white.
    ///   - intensity: Emission intensity multiplier. Default 1.0.
    /// - Returns: A configured RealityKit material.
    public static func emissive(
        color: SimpleMaterial.Color = .white,
        intensity: Float = 1.0
    ) -> any RealityKit.Material {
        var mat = PhysicallyBasedMaterial()
        mat.baseColor = .init(tint: .black)
        // emissiveColor requires PhysicallyBasedMaterial.EmissiveColor
        // which accepts a color tint directly
        mat.emissiveColor = PhysicallyBasedMaterial.EmissiveColor(color: color)
        mat.emissiveIntensity = intensity
        return mat
    }

    // MARK: - Clearcoat (Car Paint)

    /// Creates a clearcoat material (two-layer coating like car paint).
    ///
    /// Adds a clear, glossy layer on top of the base color for a polished look.
    ///
    /// - Parameters:
    ///   - baseColor: Base layer color. Default red.
    ///   - metallic: Base layer metallic. Default 0.8.
    ///   - roughness: Base layer roughness. Default 0.3.
    ///   - clearcoatRoughness: Top coat roughness. Default 0.1.
    /// - Returns: A configured RealityKit material.
    public static func clearcoat(
        baseColor: SimpleMaterial.Color = .red,
        metallic: Float = 0.8,
        roughness: Float = 0.3,
        clearcoatRoughness: Float = 0.1
    ) -> any RealityKit.Material {
        var mat = PhysicallyBasedMaterial()
        mat.baseColor = .init(tint: baseColor)
        mat.metallic = .init(floatLiteral: metallic)
        mat.roughness = .init(floatLiteral: roughness)
        mat.clearcoat = .init(floatLiteral: 1.0)
        mat.clearcoatRoughness = .init(floatLiteral: clearcoatRoughness)
        return mat
    }

    // MARK: - Matte

    /// Creates a fully matte (non-reflective) material.
    ///
    /// - Parameters:
    ///   - color: Material color. Default white.
    /// - Returns: A configured RealityKit material.
    public static func matte(
        color: SimpleMaterial.Color = .white
    ) -> any RealityKit.Material {
        var mat = PhysicallyBasedMaterial()
        mat.baseColor = .init(tint: color)
        mat.metallic = .init(floatLiteral: 0.0)
        mat.roughness = .init(floatLiteral: 1.0)
        return mat
    }

    // MARK: - Mirror

    /// Creates a mirror-like highly reflective material.
    ///
    /// - Parameters:
    ///   - tint: Slight color tint. Default white.
    /// - Returns: A configured RealityKit material.
    public static func mirror(
        tint: SimpleMaterial.Color = .white
    ) -> any RealityKit.Material {
        var mat = PhysicallyBasedMaterial()
        mat.baseColor = .init(tint: tint)
        mat.metallic = .init(floatLiteral: 1.0)
        mat.roughness = .init(floatLiteral: 0.0)
        return mat
    }

    // MARK: - Subsurface Scattering (Skin/Wax)

    /// Creates a material with subsurface scattering properties.
    ///
    /// Simulates light penetrating and scattering beneath the surface.
    /// Useful for skin, wax, marble, and other translucent materials.
    ///
    /// - Parameters:
    ///   - color: Surface color. Default light skin tone.
    ///   - scatteringColor: Color of scattered light. Default pinkish.
    /// - Returns: A configured RealityKit material.
    public static func subsurface(
        color: SimpleMaterial.Color = SimpleMaterial.Color(
            red: 0.96, green: 0.87, blue: 0.80, alpha: 1.0
        ),
        scatteringColor: SimpleMaterial.Color = SimpleMaterial.Color(
            red: 1.0, green: 0.4, blue: 0.3, alpha: 1.0
        )
    ) -> any RealityKit.Material {
        // RealityKit's PBR does not natively expose a subsurface parameter.
        // We approximate it with low metallic, moderate roughness, and slight transparency.
        var mat = PhysicallyBasedMaterial()
        mat.baseColor = .init(tint: color)
        mat.metallic = .init(floatLiteral: 0.0)
        mat.roughness = .init(floatLiteral: 0.6)
        mat.blending = .transparent(opacity: .init(floatLiteral: 0.92))
        return mat
    }

    // MARK: - Unlit (Flat color, ignores scene lighting)

    /// Creates an unlit (flat-colored) material that ignores the scene lighting.
    ///
    /// The unlit shading model is independent of light: surfaces appear as flat
    /// colors and ignore the scene's lights and environment. Use this for
    /// HUD-like overlays, debug visualizations, billboards, or stylized rendering
    /// where physically-based shading is not desired.
    ///
    /// Mirrors `MaterialLoader.createUnlitColorInstance(color:)` on the Android side.
    ///
    /// - Parameter color: The flat color. Default white.
    /// - Returns: A RealityKit `UnlitMaterial`.
    public static func unlit(
        color: SimpleMaterial.Color = .white
    ) -> any RealityKit.Material {
        UnlitMaterial(color: color)
    }

    /// Alias for `unlit(color:)` kept for source compatibility.
    ///
    /// Prefer `CustomMaterial.unlit(color:)` — the name aligns with the Filament
    /// shading model and the Android `createUnlitColorInstance` API.
    @available(*, deprecated, renamed: "unlit(color:)")
    public static func debug(
        color: SimpleMaterial.Color = .green
    ) -> any RealityKit.Material {
        UnlitMaterial(color: color)
    }
}

#endif // os(iOS) || os(macOS) || os(visionOS)
