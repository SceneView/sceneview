#if os(iOS) || os(macOS) || os(visionOS)
import SwiftUI
import RealityKit

/// A SwiftUI view for rendering 3D content using RealityKit.
///
/// Mirrors SceneView Android's `SceneView { }` composable — declare nodes declaratively
/// inside the content builder, with built-in orbit camera, environment lighting,
/// and gesture handling.
///
/// ```swift
/// @State private var model: ModelNode?
///
/// SceneView { root in
///     if let model {
///         root.addChild(model.entity)
///     }
/// }
/// .environment(.studio)
/// .cameraControls(.orbit)
/// .onEntityTapped { entity in
///     print("Tapped: \(entity)")
/// }
/// .task {
///     model = try? await ModelNode.load("models/car.usdz")
/// }
/// ```
/// How a `SceneView` should provision a main / fill light slot.
///
/// Mirrors SceneView Android's `mainLightNode: LightNode?` / `fillLightNode: LightNode?`
/// composable parameters (`Scene.kt`), but with a 3-state enum instead of a double-Optional
/// sentinel — clearer at call sites and exhaustive in `switch`.
///
/// ```swift
/// SceneView { /* ... */ }
///   .fillLight(.systemDefault)              // (no-op — default fill at 3 000 lux)
///   .fillLight(.disabled)                   // single-light setup
///   .fillLight(.custom(LightNode.fill(intensity: 5_000)))   // user override
/// ```
///
/// Note: today the slot is read once during scene setup. Reactive replacement
/// (`SceneView(...).fillLight(.custom(newLight))` mid-frame) is tracked as a
/// follow-up — the current `RealityView.update:` closure does not yet diff lights.
public enum LightSlot {
    /// Use the built-in default for this light slot.
    /// - Main slot default: directional at `10_000` lux, oriented straight down, casts shadows.
    /// - Fill slot default: directional at `3_000` lux, oriented from `(0.5, -0.5, 0.5)`, no shadow.
    case systemDefault

    /// Remove this light slot entirely. Use `.disabled` on the fill slot to get a single-light setup.
    case disabled

    /// Provision the slot with the caller's `LightNode`. The entity is added to the scene root
    /// during setup; orient it via ``LightNode/lookAt(_:)`` or ``LightNode/position(_:)``
    /// before passing it in.
    case custom(LightNode)
}

public struct SceneView: View {
    let content: (Entity) -> Void
    var sceneEnvironment: SceneEnvironment?
    var cameraControlMode: CameraControlMode?
    var onEntityTapped: ((Entity) -> Void)?
    var enableAutoRotate: Bool = false
    var autoRotateSpeed: Float = 0.3

    // Light slot overrides — read once during scene setup. Defaults to `.systemDefault`
    // which provisions Android-parity 10 000-lux main + 3 000-lux fill (matches the
    // v4.1.0 BREAKING render-defaults change on Android, finally reaching iOS in v4.2.0).
    // Reactive replacement is tracked as a follow-up issue (#1017).
    var mainLightSlot: LightSlot = .systemDefault
    var fillLightSlot: LightSlot = .systemDefault

    // Render-quality preset — applied after scene setup to toggle shadows + IBL
    // intensity per-tier. Mirrors Android's `RenderQuality` enum (`#1004`).
    // Default `.default` preserves the scene's loaded settings.
    var renderQualityPreset: RenderQuality = .default

    /// Creates a 3D scene with imperative content setup.
    ///
    /// - Parameter content: A closure that populates the scene. Receives a root
    ///   entity — add your content as children of this entity.
    public init(_ content: @escaping (Entity) -> Void) {
        self.content = content
    }

    /// Creates a 3D scene with declarative content using `@NodeBuilder`.
    ///
    /// Mirrors the Android `SceneView { }` composable pattern — declare nodes inline:
    ///
    /// ```swift
    /// SceneView {
    ///     GeometryNode.cube(size: 0.3, color: .red)
    ///         .position(.init(x: -1, y: 0, z: -2))
    ///     GeometryNode.sphere(radius: 0.2, color: .blue)
    ///         .position(.init(x: 1, y: 0, z: -2))
    ///     LightNode.directional(intensity: 1000)
    /// }
    /// ```
    public init(@NodeBuilder content: @escaping () -> [Entity]) {
        self.content = { root in
            for entity in content() {
                root.addChild(entity)
            }
        }
    }

    public var body: some View {
        SceneViewRepresentation(
            content: content,
            sceneEnvironment: sceneEnvironment,
            cameraControlMode: cameraControlMode ?? .orbit,
            onEntityTapped: onEntityTapped,
            enableAutoRotate: enableAutoRotate,
            autoRotateSpeed: autoRotateSpeed,
            mainLightSlot: mainLightSlot,
            fillLightSlot: fillLightSlot,
            renderQualityPreset: renderQualityPreset
        )
    }

    // MARK: - View Modifiers

    /// Sets the IBL environment for the scene.
    public func environment(_ environment: SceneEnvironment) -> SceneView {
        var copy = self
        copy.sceneEnvironment = environment
        return copy
    }

    /// Sets the camera control mode (orbit, pan, first-person).
    public func cameraControls(_ mode: CameraControlMode) -> SceneView {
        var copy = self
        copy.cameraControlMode = mode
        return copy
    }

    /// Called when an entity in the scene is tapped.
    public func onEntityTapped(_ handler: @escaping (Entity) -> Void) -> SceneView {
        var copy = self
        copy.onEntityTapped = handler
        return copy
    }

    /// Enables automatic camera rotation around the scene.
    ///
    /// - Parameter speed: Rotation speed in radians per second. Default 0.3.
    public func autoRotate(speed: Float = 0.3) -> SceneView {
        var copy = self
        copy.enableAutoRotate = true
        copy.autoRotateSpeed = speed
        return copy
    }

    /// Configures the main / key directional light slot.
    ///
    /// Mirrors SceneView Android's `mainLightNode: LightNode? = rememberMainLightNode(engine)`
    /// composable parameter (`Scene.kt`). Pass `.disabled` for a rare IBL-only setup;
    /// `.custom(LightNode)` to provide your own.
    ///
    /// Default (`.systemDefault`): directional light at `10 000` lux pointing straight
    /// down (`-Y`), shadow casting enabled. Mirrors the Android v4.1.0 BREAKING
    /// render-defaults change finally reaching iOS in v4.2.0.
    ///
    /// ```swift
    /// SceneView { /* ... */ }
    ///   .mainLight(.custom(LightNode.directional(intensity: 5_000)
    ///       .lookAt(.zero)))                              // weaker key, custom angle
    ///
    /// SceneView { /* ... */ }
    ///   .mainLight(.disabled)                              // IBL-only (rare)
    /// ```
    public func mainLight(_ slot: LightSlot) -> SceneView {
        var copy = self
        copy.mainLightSlot = slot
        return copy
    }

    /// Configures the secondary fill directional light slot.
    ///
    /// Mirrors SceneView Android's `fillLightNode: LightNode? = rememberFillLightNode(engine)`
    /// composable parameter (`Scene.kt`). Pair with ``mainLight(_:)`` to fully
    /// override the key+fill setup.
    ///
    /// Default (`.systemDefault`): ``LightNode/fill(color:intensity:castsShadow:)`` at
    /// `3 000` lux, no shadow, oriented along the canonical Android direction
    /// `(0.5, -0.5, 0.5)` (upper-back-left → down-front-right) so the unlit side of
    /// objects gets a soft kick without flattening them.
    ///
    /// ```swift
    /// SceneView { /* ... */ }
    ///   .fillLight(.custom(LightNode.fill(intensity: 6_000)))   // brighter fill
    ///
    /// SceneView { /* ... */ }
    ///   .fillLight(.disabled)                                    // single-light setup
    /// ```
    public func fillLight(_ slot: LightSlot) -> SceneView {
        var copy = self
        copy.fillLightSlot = slot
        return copy
    }

    /// Applies a coherent set of rendering-quality defaults via a single preset.
    ///
    /// Mirrors Android's `SceneView(renderQuality = ...)` composable parameter and
    /// `View.applyRenderQuality(...)` extension (`RenderQuality.kt`). Choose:
    ///
    /// - ``RenderQuality/cinematic`` — hero shots, product viewers (full shadow distance, IBL ≥ 1.0).
    /// - ``RenderQuality/default`` — out-of-the-box balanced setup (preserves loaded settings).
    /// - ``RenderQuality/performance`` — low-end / AR camera-feed (drops shadows, halves IBL).
    ///
    /// See ``RenderQuality`` for the iOS↔Android parity table — RealityKit's render-options
    /// API is narrower than Filament's so the iOS preset honours what RealityKit exposes
    /// (per-light shadow toggle + IBL intensity exponent) and documents the gaps for
    /// SSAO / MSAA / HDR-buffer-quality / bloom strength which RealityKit does not expose.
    ///
    /// ```swift
    /// SceneView { /* ... */ }
    ///   .renderQuality(.cinematic)         // full key+fill+shadow, bright IBL
    ///
    /// SceneView { /* ... */ }
    ///   .renderQuality(.performance)       // shadows off, IBL halved
    /// ```
    public func renderQuality(_ preset: RenderQuality) -> SceneView {
        var copy = self
        copy.renderQualityPreset = preset
        return copy
    }
}

// MARK: - Scene entities holder

/// Holds the persistent RealityKit entity references for a scene.
/// Using a class ensures the Entity objects are never accidentally copied
/// or recreated across SwiftUI re-renders — critical because @State on a
/// reference type does not guarantee identity stability in all SwiftUI versions.
private final class SceneEntities: ObservableObject {
    let root = Entity()
    let ibl = Entity()
}

// MARK: - Internal implementation

/// Internal view that manages the RealityView, camera, gestures, and environment.
private struct SceneViewRepresentation: View {
    let content: (Entity) -> Void
    let sceneEnvironment: SceneEnvironment?
    let cameraControlMode: CameraControlMode
    let onEntityTapped: ((Entity) -> Void)?
    let enableAutoRotate: Bool
    let autoRotateSpeed: Float
    let mainLightSlot: LightSlot
    let fillLightSlot: LightSlot
    let renderQualityPreset: RenderQuality

    @State private var camera = CameraControls(mode: .orbit)
    @StateObject private var entities = SceneEntities()
    @State private var lastDragTranslation: CGSize = .zero
    @State private var initialPinchRadius: Float? = nil
    @State private var isDragging = false

    var body: some View {
        realityViewContent
            .gesture(dragGesture)
            .gesture(pinchGesture)
            .simultaneousGesture(tapGesture)
            .task {
                // Auto-rotation loop
                guard enableAutoRotate else { return }
                camera.isAutoRotating = true
                camera.autoRotateSpeed = autoRotateSpeed
                var lastTime = CFAbsoluteTimeGetCurrent()
                while !Task.isCancelled {
                    try? await Task.sleep(nanoseconds: 16_666_667) // ~60 fps
                    let now = CFAbsoluteTimeGetCurrent()
                    let dt = Float(now - lastTime)
                    lastTime = now
                    if !isDragging {
                        camera.applyAutoRotation(dt: dt)
                    }
                }
            }
            .task(id: sceneEnvironment?.name) {
                // Load and apply IBL environment when it changes
                guard let env = sceneEnvironment else { return }
                await loadEnvironment(env)
            }
    }

    @ViewBuilder
    private var realityViewContent: some View {
        #if os(iOS) || os(visionOS) || os(macOS)
        RealityView { realityContent in
            setupScene(&realityContent)
        } update: { _ in
            applyCamera()
        }
        #else
        // RealityView requires macOS 15.0+; fall back to a placeholder on older SDKs
        Text("3D view requires macOS 15.0 or later")
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        #endif
    }

    // MARK: - Scene Setup

    #if os(iOS) || os(visionOS) || os(macOS)
    private func setupScene(_ realityContent: inout RealityViewCameraContent) {
        // Use virtual camera (fixed perspective) instead of AR spatial tracking.
        // Without this, RealityKit defaults to .spatialTracking which requires a
        // physical device camera — causing a black screen in the simulator.
        #if !os(visionOS)
        // Virtual camera: fixed perspective, no AR spatial tracking.
        // spatialTracking (the default) requires a physical device camera →
        // black screen in the simulator.
        realityContent.camera = .virtual

        // Perspective camera: positioned for a nice 3/4 view.
        // Content is placed at origin (0,0,0) and rotated by applyCamera() to
        // simulate orbit. The slight Y elevation gives a natural bird's-eye angle.
        // look(at:from:) handles both position and orientation — in RealityKit,
        // entities face +Z by default so explicit orientation is required.
        let perspCamera = PerspectiveCamera()
        perspCamera.camera.fieldOfViewInDegrees = 60
        perspCamera.look(at: .zero, from: [0, 0.3, 2], relativeTo: nil)
        realityContent.add(perspCamera)
        #endif

        // Root entity holds all user content
        realityContent.add(entities.root)

        // IBL entity (will be configured when environment loads)
        realityContent.add(entities.ibl)

        // Main / key directional light slot. Defaults to Android-parity 10 000 lux
        // pointing straight down with shadow casting — matches the v4.1.0 BREAKING
        // render-defaults change that finally reaches iOS in v4.2.0.
        // Lights are added as children of `entities.root` so `applyRenderQuality(...)`
        // can walk-and-tune them later in this same setup pass.
        switch mainLightSlot {
        case .systemDefault:
            let main = LightNode.directional(
                color: .white,
                intensity: 10_000,
                castsShadow: true
            )
            // RealityKit directional lights emit along the entity's -Z axis; lookAt
            // origin from above so the light points straight down (Android default
            // direction `(0, -1, 0)`).
            main.entity.look(at: .zero, from: [0, 1, 0], relativeTo: nil)
            entities.root.addChild(main.entity)
        case .disabled:
            break
        case .custom(let node):
            entities.root.addChild(node.entity)
        }

        // Fill light slot. Defaults to LightNode.fill() at 3 000 lux from the canonical
        // Android direction `(0.5, -0.5, 0.5)` (upper-back-left → down-front-right),
        // no shadow. 30 % of main intensity — the soft kick on the unlit side.
        switch fillLightSlot {
        case .systemDefault:
            let fill = LightNode.fill(intensity: 3_000)
            // Orient toward origin from the canonical Android fill direction's source
            // point: the light travels from `(0.5, -0.5, 0.5)` direction → place the
            // entity at the opposite point and look at origin so emission lands on the
            // shadow side of objects centered at world origin.
            fill.entity.look(at: .zero, from: [-0.5, 0.5, -0.5], relativeTo: nil)
            entities.root.addChild(fill.entity)
        case .disabled:
            break
        case .custom(let node):
            entities.root.addChild(node.entity)
        }

        // Populate user content
        content(entities.root)

        // Apply RenderQuality preset to all directional lights + the IBL receiver entity.
        // Runs AFTER lights + content are added so the preset walks the full scene tree.
        // Cinematic bumps shadow distance + ensures IBL ≥ 1.0; Performance drops shadows
        // + halves IBL. Default preserves whatever the scene loaded. Mirrors Android's
        // `View.applyRenderQuality(RenderQuality.X)` pattern (#1004).
        _ = applyRenderQuality(
            renderQualityPreset,
            to: entities.root,
            iblReceiver: entities.ibl
        )
    }
    #endif

    // MARK: - Environment IBL

    @MainActor
    private func loadEnvironment(_ env: SceneEnvironment) async {
        do {
            let resource = try await env.load()
            #if os(iOS) || os(visionOS) || os(macOS)
            // Apply IBL to the scene via ImageBasedLightComponent
            entities.ibl.components.set(
                ImageBasedLightComponent(
                    source: .single(resource),
                    intensityExponent: env.intensity
                )
            )
            // Make root entity receive IBL
            entities.root.components.set(
                ImageBasedLightReceiverComponent(imageBasedLight: entities.ibl)
            )
            #endif
        } catch {
            // Environment loading failed — scene continues with default lighting
            print("[SceneViewSwift] Failed to load environment '\(env.name)': \(error)")
        }
    }

    // MARK: - Camera

    private func applyCamera() {
        let yaw = simd_quatf(angle: -camera.azimuth, axis: [0, 1, 0])
        let pitch = simd_quatf(angle: -camera.elevation, axis: [1, 0, 0])
        entities.root.orientation = yaw * pitch

        let zoomScale = 5.0 / camera.orbitRadius
        entities.root.scale = SIMD3<Float>(repeating: zoomScale)
    }

    // MARK: - Gestures

    private var dragGesture: some Gesture {
        DragGesture()
            .onChanged { value in
                let delta = CGSize(
                    width: value.translation.width - lastDragTranslation.width,
                    height: value.translation.height - lastDragTranslation.height
                )
                camera.handleDrag(delta)
                lastDragTranslation = value.translation
                isDragging = true
            }
            .onEnded { _ in
                lastDragTranslation = .zero
                isDragging = false
            }
    }

    private var pinchGesture: some Gesture {
        MagnifyGesture()
            .onChanged { value in
                if initialPinchRadius == nil {
                    initialPinchRadius = camera.orbitRadius
                }
                if let initial = initialPinchRadius {
                    let newRadius = initial / Float(value.magnification)
                    camera.orbitRadius = min(
                        max(newRadius, camera.minRadius),
                        camera.maxRadius
                    )
                }
            }
            .onEnded { _ in
                initialPinchRadius = nil
            }
    }

    private var tapGesture: some Gesture {
        SpatialTapGesture()
            .onEnded { _ in
                onEntityTapped?(entities.root)
            }
    }
}
#endif // os(iOS) || os(macOS) || os(visionOS)
