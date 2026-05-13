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
/// Slot values are diffed reactively: `SceneView(...).fillLight(.custom(newLight))`
/// from a `@State` mutation triggers a swap on the next render frame (the
/// `RealityView.update:` closure walks the scene root for tagged entities and
/// replaces them when the slot value changes). Closes #1017.
public enum LightSlot: Equatable {

    /// Equatable conformance compares cases, with `.custom` cases compared by
    /// `Entity` reference identity (`===`) — sufficient because a single
    /// `LightNode` instance owns a single `Entity` and the slot is the only
    /// retainer of interest here.
    public static func == (lhs: LightSlot, rhs: LightSlot) -> Bool {
        switch (lhs, rhs) {
        case (.systemDefault, .systemDefault), (.disabled, .disabled):
            return true
        case (.custom(let a), .custom(let b)):
            return a.entity === b.entity
        default:
            return false
        }
    }

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
    #if os(iOS) || os(macOS)
    /// Holds the perspective camera so gesture handlers can mutate its
    /// field-of-view directly. Used by ``CameraControlMode/firstPerson``
    /// pinch — see #1034. visionOS renders through the headset, no manual
    /// camera entity exists.
    let perspCamera = PerspectiveCamera()
    #endif
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
    /// Snapshotted FOV when a pinch begins in ``CameraControlMode/firstPerson``
    /// — kept separate from `initialPinchRadius` so the orbit/pan dolly path
    /// stays untouched. Closes #1034.
    @State private var initialPinchFov: Float? = nil
    @State private var isDragging = false

    // Reactive light-slot caches — compared in `update:` closure to detect when the
    // caller mutated `.mainLight(_:)` / `.fillLight(_:)` so the corresponding entity
    // can be swapped in-place without a full RealityView teardown. Closes #1017.
    @State private var appliedMainSlot: LightSlot? = nil
    @State private var appliedFillSlot: LightSlot? = nil

    var body: some View {
        realityViewContent
            .gesture(dragGesture)
            .gesture(pinchGesture)
            .simultaneousGesture(tapGesture)
            // Per-entity gestures — dispatched to handlers registered via
            // `Entity.onTap / onDrag / onScale / onRotate / onLongPress`
            // (NodeGesture system). Each gesture is `.targetedToAnyEntity()`
            // so it only fires when the user touches a real entity with a
            // collision shape, and the camera-control gestures above keep
            // working on empty-space drags. Closes the #928 silent-stub
            // batch for NodeGesture dispatch.
            .simultaneousGesture(entityTapGesture)
            .simultaneousGesture(entityDragGesture)
            .simultaneousGesture(entityMagnifyGesture)
            .simultaneousGesture(entityRotateGesture)
            .simultaneousGesture(entityLongPressGesture)
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
            // Diff light slots and swap entities when the caller's modifier value
            // changed since last frame. Closes #1017 (Android `prevFillLightRef`
            // pattern in `Scene.kt:287-305` ported to iOS).
            refreshLightSlot(.main, slot: mainLightSlot)
            refreshLightSlot(.fill, slot: fillLightSlot)
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
        // We keep a ref on the SceneEntities holder so `.firstPerson` pinch can
        // mutate the FOV at runtime (#1034).
        entities.perspCamera.camera.fieldOfViewInDegrees = 60
        entities.perspCamera.look(at: .zero, from: [0, 0.3, 2], relativeTo: nil)
        realityContent.add(entities.perspCamera)
        #endif

        // Root entity holds all user content
        realityContent.add(entities.root)

        // IBL entity (will be configured when environment loads)
        realityContent.add(entities.ibl)

        // Provision both light slots via the shared helper so the same code path runs
        // on initial setup AND on reactive swap (refreshLightSlot below). Lights are
        // tagged with `LightSlotMarker` so the diff in `update:` can locate + remove
        // them when the caller's modifier value changes. Closes #1017.
        provisionLightSlot(.main, slot: mainLightSlot)
        provisionLightSlot(.fill, slot: fillLightSlot)
        appliedMainSlot = mainLightSlot
        appliedFillSlot = fillLightSlot

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

    // MARK: - Light slot reactive plumbing (#1017)

    /// Tags entities provisioned by ``provisionLightSlot(_:slot:)`` so the
    /// reactive diff in ``refreshLightSlot(_:slot:)`` can locate them on
    /// subsequent renders and replace them when the caller's `LightSlot`
    /// value changes.
    fileprivate struct LightSlotMarker: Component {
        enum Slot { case main, fill }
        let slot: Slot
    }

    /// Provisions a light entity for the given slot under `entities.root`,
    /// applying the per-slot defaults when `slot == .systemDefault` and
    /// tagging the entity with `LightSlotMarker` for later diffing.
    /// Mirrors the original setupScene logic but factored out so the
    /// reactive swap path can re-use it.
    @MainActor
    private func provisionLightSlot(_ which: LightSlotMarker.Slot, slot: LightSlot) {
        let entity: Entity?
        switch slot {
        case .systemDefault:
            switch which {
            case .main:
                let main = LightNode.directional(
                    color: .white,
                    intensity: 10_000,
                    castsShadow: true
                )
                main.entity.look(at: .zero, from: [0, 1, 0], relativeTo: nil)
                entity = main.entity
            case .fill:
                let fill = LightNode.fill(intensity: 3_000)
                fill.entity.look(at: .zero, from: [-0.5, 0.5, -0.5], relativeTo: nil)
                entity = fill.entity
            }
        case .disabled:
            entity = nil
        case .custom(let node):
            entity = node.entity
        }
        if let entity {
            entity.components.set(LightSlotMarker(slot: which))
            entities.root.addChild(entity)
        }
    }

    /// Diffs the current slot value against the cached `applied{Main,Fill}Slot`
    /// and swaps the tagged entity in-place when they differ. Called from the
    /// `RealityView.update:` closure on every render. Closes #1017.
    ///
    /// Mirrors Android's `Scene.kt:287-305` `prevFillLightRef` pattern — the
    /// idea is the same (cache the previously-applied value, diff on
    /// recomposition / re-render, swap the scene-attached entity) but the
    /// mechanism differs because RealityKit's `RealityViewContent` is
    /// inout-only inside the `update:` closure (no explicit remove API on the
    /// content; we go through the entity hierarchy via `removeFromParent()`).
    @MainActor
    private func refreshLightSlot(_ which: LightSlotMarker.Slot, slot: LightSlot) {
        let applied: LightSlot? = (which == .main) ? appliedMainSlot : appliedFillSlot
        guard applied != slot else { return }   // no change since last frame
        // Remove the old tagged entity if present.
        let toRemove = entities.root.children.first { entity in
            entity.components[LightSlotMarker.self]?.slot == which
        }
        toRemove?.removeFromParent()
        // Provision the new one (or none if the new slot is .disabled).
        provisionLightSlot(which, slot: slot)
        // Update the cache so the next frame's diff is a no-op.
        if which == .main {
            appliedMainSlot = slot
        } else {
            appliedFillSlot = slot
        }
    }

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
        // Sync the camera state's mode with the modifier-provided value. Done
        // every frame because the modifier propagates as a `let` while the
        // camera state is `@State` — without this, calling
        // `.cameraControls(.pan)` would be silently ignored. Closes #1034.
        if camera.mode != cameraControlMode {
            camera.mode = cameraControlMode
        }

        let yaw = simd_quatf(angle: -camera.azimuth, axis: [0, 1, 0])
        let pitch = simd_quatf(angle: -camera.elevation, axis: [1, 0, 0])
        entities.root.orientation = yaw * pitch

        // Per-mode application of zoom + translation. Mirrors Android's
        // `CameraGestureDetector` modes (ORBIT / PAN / FREE_FLIGHT) — closes
        // #1034 (last #928 silent-stub item).
        switch camera.mode {
        case .orbit:
            // Standard orbit: pinch scales scene; no translation.
            let zoomScale = 5.0 / camera.orbitRadius
            entities.root.scale = SIMD3<Float>(repeating: zoomScale)
            entities.root.position = .zero

        case .pan:
            // Pan: pinch still dollies, drag translates `target` laterally.
            // Visually the scene slides in screen space — equivalent to
            // moving the camera in the opposite direction.
            let zoomScale = 5.0 / camera.orbitRadius
            entities.root.scale = SIMD3<Float>(repeating: zoomScale)
            entities.root.position = -camera.target * zoomScale

        case .firstPerson:
            // First-person: no scale change (pinch mutates FOV instead — see
            // pinchGesture). Rotation makes the scene yaw/pitch around the
            // user as if they were standing still and looking around.
            entities.root.scale = SIMD3<Float>(repeating: 1)
            entities.root.position = .zero
            #if os(iOS) || os(macOS)
            // Sync the perspective camera FOV to the camera-controls state.
            // Done every frame so external mutations of `camera.fov` also
            // take effect; cheap enough that the cost is negligible.
            entities.perspCamera.camera.fieldOfViewInDegrees = camera.fov
            #endif
        }
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
        // Mode-aware pinch: orbit/pan dollies the radius, firstPerson scales
        // the perspective camera's FOV (#1034). We snapshot the value at
        // gesture start so the delta is applied to a stable base — same
        // pattern as Android's `Manipulator.scrollBegin / scrollUpdate`.
        MagnifyGesture()
            .onChanged { value in
                switch camera.mode {
                case .orbit, .pan:
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
                case .firstPerson:
                    if initialPinchFov == nil {
                        initialPinchFov = camera.fov
                    }
                    if let initial = initialPinchFov {
                        let newFov = initial / Float(value.magnification)
                        camera.fov = min(
                            max(newFov, camera.minFov),
                            camera.maxFov
                        )
                    }
                }
            }
            .onEnded { _ in
                initialPinchRadius = nil
                initialPinchFov = nil
            }
    }

    private var tapGesture: some Gesture {
        // Wire real entity hit-testing via SwiftUI's `targetedToAnyEntity()` — the
        // SpatialTapGesture's `value.entity` is the actual RealityKit entity at the
        // tap location, not the scene root. Closes part of #928 (this used to pass
        // `entities.root` unconditionally regardless of where the user tapped, which
        // made the callback useless for picking objects in the scene).
        //
        // Requires iOS 17+ / macOS 14+ / visionOS 1+ (RealityKit 2.x). All current
        // SceneViewSwift platforms already require those baselines.
        SpatialTapGesture()
            .targetedToAnyEntity()
            .onEnded { value in
                onEntityTapped?(value.entity)
            }
    }

    // MARK: - Per-entity gestures (NodeGesture dispatch)

    /// Dispatches a tap to any per-entity `Entity.onTap { }` handler registered
    /// via the NodeGesture system. Fires in addition to `onEntityTapped(_:)`
    /// because both contracts are useful: the modifier callback gets every
    /// entity tap (broad listener), while NodeGesture lets the caller scope
    /// handlers per-entity at construction time.
    private var entityTapGesture: some Gesture {
        SpatialTapGesture()
            .targetedToAnyEntity()
            .onEnded { value in
                NodeGesture.dispatchTap(on: value.entity)
            }
    }

    /// Per-entity drag — translates the gesture's screen-space delta into a
    /// world-space SIMD3 and dispatches to the entity's `onDrag` handler.
    /// `targetedToAnyEntity()` ensures empty-space drags continue to drive the
    /// camera (`dragGesture` above), not the entity dispatch.
    private var entityDragGesture: some Gesture {
        DragGesture(minimumDistance: 1)
            .targetedToAnyEntity()
            .onChanged { value in
                // RealityKit doesn't expose a one-line "screen → world" inverse;
                // we approximate world-space translation by treating the drag's
                // 2D screen delta as a planar XY translation at the entity's
                // depth. Sufficient for sticker-style drag interactions; users
                // who need full 3D-projected drags can compute via the camera
                // ray themselves in the handler.
                let dx = Float(value.translation.width) * 0.001
                let dy = Float(-value.translation.height) * 0.001
                NodeGesture.dispatchDrag(on: value.entity, translation: SIMD3<Float>(dx, dy, 0))
            }
    }

    /// Per-entity pinch-to-scale.
    private var entityMagnifyGesture: some Gesture {
        MagnifyGesture()
            .targetedToAnyEntity()
            .onChanged { value in
                NodeGesture.dispatchScale(on: value.entity, magnification: Float(value.magnification))
            }
    }

    /// Per-entity two-finger rotate.
    private var entityRotateGesture: some Gesture {
        RotateGesture()
            .targetedToAnyEntity()
            .onChanged { value in
                NodeGesture.dispatchRotate(on: value.entity, angle: Float(value.rotation.radians))
            }
    }

    /// Per-entity long press.
    private var entityLongPressGesture: some Gesture {
        LongPressGesture(minimumDuration: 0.5)
            .targetedToAnyEntity()
            .onEnded { value in
                NodeGesture.dispatchLongPress(on: value.entity)
            }
    }
}
#endif // os(iOS) || os(macOS) || os(visionOS)
