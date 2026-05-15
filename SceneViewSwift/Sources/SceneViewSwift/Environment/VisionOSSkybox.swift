#if os(visionOS)
import RealityKit
import Foundation

/// visionOS-only skybox plumbing for fully immersive (`ImmersiveSpace`) consumers.
///
/// `RealityViewContent.environment = .skybox(_:)` — the windowed iOS / macOS
/// skybox path landed in #1215 — is `@available(visionOS, unavailable)`:
/// a windowed or volumetric `RealityView` on visionOS composites over
/// passthrough and ignores the RealityKit environment setter. Correct for the
/// windowed case, but a `SceneView` pulled into an `ImmersiveSpace` with the
/// `.full` style *can* host an HDR background — the immersive scene replaces
/// passthrough entirely.
///
/// Since the `environment` setter is unavailable, the background is rendered
/// the documented way for a fully immersive experience: a large inverted
/// sphere textured with the equirectangular HDR via an ``UnlitMaterial``,
/// parented under a root entity tagged with RealityKit's ``WorldComponent``
/// so it is positioned in absolute world space rather than relative to the
/// (absent) windowed origin.
///
/// Closes #1235. See also the parent skybox port #1215.
enum VisionOSSkybox {

    /// Marks the entity that hosts the immersive skybox sphere so the
    /// `RealityView.update:` diff can locate, refresh, or remove it across
    /// renders — mirrors the `LightSlotMarker` discipline used for the
    /// reactive light slots (#1017).
    struct Marker: Component {
        /// Bundle resource name of the HDR currently applied to the sphere,
        /// so a diff can skip a same-resource re-build.
        let hdrResource: String
    }

    /// Radius of the skybox sphere, in meters. Large enough to enclose any
    /// reasonable immersive scene while staying well inside RealityKit's
    /// rendering range. Matches Apple's immersive-media sample convention.
    static let sphereRadius: Float = 1_000

    /// Loads an equirectangular HDR/EXR file from the main bundle as a
    /// `TextureResource`.
    ///
    /// `SceneEnvironment` HDRs ship in the *consumer's* app bundle (e.g. the
    /// demo app's `Environments/`), the same bundle `EnvironmentResource(named:)`
    /// resolves against, so the main bundle is searched here too.
    ///
    /// `EnvironmentResource.skybox` is a *cubemap* `TextureResource` and does
    /// not UV-map onto a sphere, so the equirectangular HDR file itself is
    /// loaded instead. Returns `nil` when the file cannot be resolved or
    /// decoded — the caller then leaves the immersive background neutral, the
    /// same graceful-degradation contract as the windowed path.
    @MainActor
    static func loadEquirectangularTexture(named resource: String) async -> TextureResource? {
        // `resource` may be a bare name or carry an extension (e.g.
        // "studio.hdr") — `EnvironmentResource` accepts both, so mirror that.
        let name = (resource as NSString).deletingPathExtension
        let ext = (resource as NSString).pathExtension
        let candidateExtensions = ext.isEmpty ? ["hdr", "exr"] : [ext]
        for candidate in candidateExtensions {
            guard let url = Bundle.main.url(forResource: name, withExtension: candidate)
            else { continue }
            if let texture = try? await TextureResource(
                contentsOf: url,
                options: .init(semantic: .hdrColor)
            ) {
                return texture
            }
        }
        print("[SceneViewSwift] visionOS immersive skybox: "
            + "could not load HDR '\(resource)' — background stays neutral.")
        return nil
    }

    /// Builds the immersive skybox host entity from an already-loaded
    /// equirectangular texture.
    ///
    /// The returned entity carries a ``WorldComponent`` (so its subtree is
    /// placed in absolute immersive-space world coordinates) and a single
    /// inverted-sphere child textured with the HDR. Synchronous so it can run
    /// inside the `RealityView.update:` closure.
    ///
    /// - Parameters:
    ///   - texture: The equirectangular HDR loaded via
    ///     ``loadEquirectangularTexture(named:)``.
    ///   - hdrResource: The HDR resource name, stamped onto the ``Marker`` so
    ///     a later diff can skip a same-resource rebuild.
    @MainActor
    static func makeHost(texture: TextureResource, hdrResource: String) -> Entity {
        var material = UnlitMaterial()
        material.color = .init(tint: .white, texture: .init(texture))

        let mesh = MeshResource.generateSphere(radius: sphereRadius)
        let sphere = ModelEntity(mesh: mesh, materials: [material])
        // Flip the sphere inside-out so its inner surface faces the viewer at
        // the immersive-space origin. Negative X scale reverses winding; the
        // unlit material renders the inner faces regardless.
        sphere.scale = [-1, 1, 1]

        let host = Entity()
        host.components.set(WorldComponent())
        host.components.set(Marker(hdrResource: hdrResource))
        host.addChild(sphere)
        return host
    }
}
#endif // os(visionOS)
