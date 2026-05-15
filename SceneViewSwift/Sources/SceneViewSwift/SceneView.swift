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

    // Auto-centre user content the first frame the bounds are non-empty so
    // demos placing objects at e.g. `z = -2` show up visually centred in
    // the viewport. Mirrors Android's per-demo `ModelNode(centerOrigin: …)`
    // pattern but at the library level. Default `true`; opt out via
    // `.autoCenterContent(false)`. Closes #1026.
    var autoCenterContentEnabled: Bool = true

    // visionOS only: set by `.immersiveSpace(true)` when the caller embeds
    // this `SceneView` inside an `ImmersiveSpace` with the `.full` style. In
    // that surface the HDR `showSkybox` environment is rendered via an
    // inverted-sphere skybox under a `WorldComponent` root, since
    // `RealityViewContent.environment` is `@available(visionOS, unavailable)`.
    // Default `false` — windowed / volumetric visionOS scenes keep
    // passthrough. No-op on iOS / macOS (those use the `.skybox(_:)` path).
    // Closes #1235.
    var immersiveSpace: Bool = false

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
            renderQualityPreset: renderQualityPreset,
            autoCenterContentEnabled: autoCenterContentEnabled,
            immersiveSpace: immersiveSpace
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

    /// Controls whether the library translates the user-content root entity
    /// so its centroid lands at the orbit pivot on the first frame the
    /// scene's `visualBounds` is non-empty. Default `true`. Closes #1026.
    ///
    /// Pass `false` for scenes that rely on intentional off-centre
    /// placement (e.g. carousels, narrative dioramas, story-mode demos
    /// where the camera is meant to look down on content placed below).
    /// All existing demos and code that previously called
    /// `ModelNode(centerOrigin: …)` work either way — the centring is
    /// idempotent and reverses an off-centre placement to origin.
    ///
    /// **iOS-only behaviour vs Android (#1026)**: Android achieves the
    /// same effect per-demo via `ModelNode(centerOrigin = Position.ZERO)`
    /// and there is no library-level auto-centre on Android. Cross-platform
    /// code that ports Android scenes verbatim will see iOS implicitly
    /// re-centre content that was laid out with explicit positions —
    /// pass `false` here to disable the library-level pass and restore
    /// strict Android-parity placement semantics.
    ///
    /// ```swift
    /// SceneView { root in
    ///     // Hero piece placed deliberately off-centre for a horizon-line shot
    ///     root.addChild(model.entity)
    /// }
    /// .autoCenterContent(false)
    /// ```
    public func autoCenterContent(_ enabled: Bool) -> SceneView {
        var copy = self
        copy.autoCenterContentEnabled = enabled
        return copy
    }

    /// Declares that this `SceneView` is hosted inside a fully immersive
    /// visionOS `ImmersiveSpace` (the `.full` style).
    ///
    /// A windowed or volumetric `RealityView` on visionOS composites over
    /// passthrough and ignores the RealityKit environment setter, so the
    /// `.environment(_:)` HDR only lights the scene — its `showSkybox` flag
    /// is silently a no-op on visionOS (see #1215). A fully immersive scene
    /// *can* host an HDR background: opt in with this modifier and the
    /// `showSkybox` environment is rendered as an inverted-sphere skybox
    /// (parented under a `WorldComponent` root, since
    /// `RealityViewContent.environment` is unavailable on visionOS).
    ///
    /// No effect on iOS / macOS — those always use the windowed
    /// `.skybox(_:)` path. visionOS does not expose a runtime "am I in an
    /// `ImmersiveSpace`?" query, so this opt-in is required. Closes #1235.
    ///
    /// ```swift
    /// ImmersiveSpace(id: "scene") {
    ///     SceneView { root in /* ... */ }
    ///         .environment(.nightSky)   // showSkybox == true
    ///         .immersiveSpace()         // render the HDR as a skybox
    /// }
    /// .immersionStyle(selection: .constant(.full), in: .full)
    /// ```
    public func immersiveSpace(_ enabled: Bool = true) -> SceneView {
        var copy = self
        copy.immersiveSpace = enabled
        return copy
    }
}

// MARK: - Scene entities holder

/// Holds the persistent RealityKit entity references for a scene.
/// Using a class ensures the Entity objects are never accidentally copied
/// or recreated across SwiftUI re-renders — critical because @State on a
/// reference type does not guarantee identity stability in all SwiftUI versions.
private final class SceneEntities: ObservableObject {
    /// The orbit / scale pivot. Receives the camera-controls rotation and
    /// scale every frame so the scene appears to orbit / zoom around its
    /// origin. Lights are added here so the auto-centering translation
    /// applied to ``contentRoot`` does not move them.
    let root = Entity()
    /// Holds user-supplied content. Translated by the auto-center helper
    /// on the first frame where ``Entity/visualBounds(relativeTo:)`` is
    /// non-empty, so demos placing objects at e.g. `z = -2` show up
    /// visually centred in the viewport without each demo having to
    /// `centerOrigin` itself. Closes #1026.
    let contentRoot = Entity()
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
    /// When true (default), the first frame where the user content's
    /// `visualBounds` is non-empty triggers a one-time translation of
    /// `entities.contentRoot` so the scene's centroid lands at the orbit
    /// pivot. Closes #1026.
    let autoCenterContentEnabled: Bool
    /// visionOS only: when true, the `showSkybox` HDR environment is
    /// rendered as an inverted-sphere skybox under a `WorldComponent` root
    /// for fully immersive (`ImmersiveSpace`) consumers. Closes #1235.
    let immersiveSpace: Bool

    /// Default `CameraControls(mode: .orbit)` — uses the struct's own
    /// `orbitRadius = 2.0` public default, which matches the camera-
    /// to-target distance of the pre-v4.4.0 fake-orbit `[0, 0.3, 2]`
    /// so existing demos retain their on-screen framing. Direct
    /// constructors of `CameraControls` see the same `2.0` value, so
    /// there is no internal / external default split.
    @State private var camera = CameraControls(mode: .orbit)
    @StateObject private var entities = SceneEntities()
    @State private var lastDragTranslation: CGSize = .zero
    @State private var initialPinchRadius: Float? = nil
    /// Snapshotted FOV when a pinch begins in ``CameraControlMode/firstPerson``
    /// — kept separate from `initialPinchRadius` so the orbit/pan dolly path
    /// stays untouched. Closes #1034.
    @State private var initialPinchFov: Float? = nil
    @State private var isDragging = false

    /// Set to `true` once ``refreshContentCentering()`` has translated
    /// `entities.contentRoot` so subsequent frames are a cheap no-op.
    /// Closes #1026.
    @State private var didCenterContent = false

    // Reactive light-slot caches — compared in `update:` closure to detect when the
    // caller mutated `.mainLight(_:)` / `.fillLight(_:)` so the corresponding entity
    // can be swapped in-place without a full RealityView teardown. Closes #1017.
    @State private var appliedMainSlot: LightSlot? = nil
    @State private var appliedFillSlot: LightSlot? = nil

    /// Loaded HDR resource cached for the `RealityView.update:` closure so
    /// it can apply `content.environment = .skybox(resource)` every frame
    /// when the scene environment has `showSkybox == true`. Stays `nil`
    /// when the IBL should light the scene but not render as a background.
    /// Ported from Eliott Radcliffe's sceneview-swift PR #1.
    @State private var loadedSkyboxResource: EnvironmentResource? = nil

    /// Identity of the environment resource currently bound on the
    /// `RealityViewContent.environment` setter — compared against
    /// `loadedSkyboxResource` in `update:` so we only assign when the
    /// value actually changes. Without the diff, every frame would
    /// re-write the descriptor and bump the resource's reference count
    /// (~10-50 µs/frame + ARC churn). Mirrors the `appliedMainSlot` /
    /// `appliedFillSlot` cache discipline above. Also fixes the
    /// cross-environment stale-skybox flash: clearing this on the new
    /// task tick triggers a single `.default` write while the next IBL
    /// is loading, instead of letting the OLD skybox show under the
    /// NEW IBL for the load duration.
    @State private var appliedSkyboxResource: EnvironmentResource? = nil

    #if os(visionOS)
    /// visionOS-only: the equirectangular HDR texture loaded for the
    /// immersive-space skybox. `RealityViewContent.environment` is
    /// `@available(visionOS, unavailable)`, so the windowed `.skybox(_:)`
    /// path above cannot be used — instead this texture is mapped onto an
    /// inverted sphere by ``VisionOSSkybox``. Loaded in the same `.task(id:)`
    /// closure as the IBL; `nil` while loading, on failure, or when the
    /// environment has `showSkybox == false`. Closes #1235.
    @State private var loadedImmersiveSkyboxTexture: TextureResource? = nil

    /// HDR resource name of the immersive skybox host currently attached to
    /// the scene — compared against the loaded environment in `update:` so
    /// the inverted-sphere host is rebuilt only when the resource changes.
    /// Same diff-write discipline as `appliedSkyboxResource` / the light
    /// slots. Closes #1235.
    @State private var appliedImmersiveSkyboxResource: String? = nil
    #endif

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
            .task(id: "\(sceneEnvironment?.name ?? "")|\(sceneEnvironment?.showSkybox == true)") {
                // Load and apply IBL environment when it changes. The id
                // also tracks `showSkybox` so toggling the skybox flag on
                // the same environment re-runs the loader and updates
                // `loadedSkyboxResource` for the update: closure.
                //
                // Clear the cached resource BEFORE the await so the
                // `update:` closure's diff falls back to `.default`
                // during the load window — without this, switching
                // from `.studio` → `.night` would keep the studio
                // skybox visible under the night IBL for the 50-300 ms
                // it takes the new HDR to decode (~30 MB at 4K). The
                // next frame after `loadEnvironment` completes will
                // diff the new resource in.
                loadedSkyboxResource = nil
                #if os(visionOS)
                loadedImmersiveSkyboxTexture = nil
                #endif
                guard let env = sceneEnvironment else { return }
                await loadEnvironment(env)
            }
    }

    @ViewBuilder
    private var realityViewContent: some View {
        #if os(iOS) || os(visionOS) || os(macOS)
        RealityView { realityContent in
            setupScene(&realityContent)
        } update: { content in
            applyCamera()
            // Diff light slots and swap entities when the caller's modifier value
            // changed since last frame. Closes #1017 (Android `prevFillLightRef`
            // pattern in `Scene.kt:287-305` ported to iOS).
            refreshLightSlot(.main, slot: mainLightSlot)
            refreshLightSlot(.fill, slot: fillLightSlot)
            // Auto-center user content the first frame where `visualBounds`
            // becomes non-empty (i.e. async-loaded models have populated).
            // No-op once centered; opt-out via `.autoCenterContent(false)`.
            // Closes #1026.
            if autoCenterContentEnabled {
                refreshContentCentering()
            }
            // Apply the HDR resource as a skybox background when the scene
            // environment has `showSkybox = true`. Without this the IBL
            // affects lighting but the background stays default — visible
            // as the same neutral void regardless of environment chosen.
            // visionOS uses passthrough by default and ignores the
            // RealityKit `environment` API; gate to iOS + macOS only.
            // Ported from Eliott Radcliffe's sceneview-swift PR #1.
            //
            // Diff-write: only assign when the resource identity changed
            // since the last applied value. Same `applied*` cache pattern
            // as the light-slot reactive plumbing above. Saves an ARC
            // bump + descriptor re-validation per frame (`content.environment`
            // is not a free no-op — internally re-wraps the resource
            // even when assigned an equal value).
            #if os(iOS) || os(macOS)
            if loadedSkyboxResource !== appliedSkyboxResource {
                if let resource = loadedSkyboxResource {
                    content.environment = .skybox(resource)
                } else {
                    content.environment = .default
                }
                appliedSkyboxResource = loadedSkyboxResource
            }
            #endif

            // visionOS immersive-space skybox. `RealityViewContent.environment`
            // is unavailable on visionOS, so a fully immersive consumer that
            // opted in via `.immersiveSpace()` gets the HDR rendered as an
            // inverted-sphere skybox under a `WorldComponent` root instead.
            // Diff-write keyed on the HDR resource name — same discipline as
            // the iOS / macOS branch above. Closes #1235.
            #if os(visionOS)
            refreshImmersiveSkybox()
            #endif
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
        // Intermediate contentRoot — user content goes here so the
        // auto-center pass (refreshContentCentering, called from the
        // RealityView.update: closure) can translate it independently of
        // lights, IBL, and camera. Closes #1026.
        entities.root.addChild(entities.contentRoot)

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

        // Populate user content. Goes into `contentRoot` (a child of root)
        // instead of `root` directly so the auto-center translation in
        // `refreshContentCentering()` only affects user content, not the
        // lights provisioned just above.
        content(entities.contentRoot)

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

    // MARK: - visionOS immersive skybox (#1235)

    #if os(visionOS)
    /// Diffs the loaded immersive-skybox HDR against the host currently
    /// attached to the scene and rebuilds the inverted-sphere skybox
    /// in-place when they differ. Called from the `RealityView.update:`
    /// closure on every render.
    ///
    /// `RealityViewContent.environment` is `@available(visionOS, unavailable)`,
    /// so the iOS / macOS `.skybox(_:)` write cannot be used here. The skybox
    /// is instead a `WorldComponent`-tagged entity (placed in absolute
    /// immersive-space world coordinates) carrying an inverted HDR sphere.
    /// Same diff-write discipline as ``refreshLightSlot(_:slot:)``, keyed on
    /// the HDR resource name. Closes #1235.
    @MainActor
    private func refreshImmersiveSkybox() {
        // Identity of the skybox that should be on screen this frame: the
        // HDR resource name when a texture is loaded and the caller opted
        // into immersive mode, otherwise `nil` (no skybox).
        let desired: String? = (immersiveSpace && loadedImmersiveSkyboxTexture != nil)
            ? sceneEnvironment?.hdrResource
            : nil
        guard desired != appliedImmersiveSkyboxResource else { return }
        // Remove any existing skybox host.
        let existing = entities.root.children.first { entity in
            entity.components[VisionOSSkybox.Marker.self] != nil
        }
        existing?.removeFromParent()
        // Build + attach the new host, if a texture is available.
        if let desired, let texture = loadedImmersiveSkyboxTexture {
            let host = VisionOSSkybox.makeHost(texture: texture, hdrResource: desired)
            entities.root.addChild(host)
        }
        appliedImmersiveSkyboxResource = desired
    }
    #endif

    // MARK: - Auto-center content (#1026)

    /// Translates ``SceneEntities/contentRoot`` so the centroid of its
    /// `visualBounds` lands at the parent (`entities.root`) origin. Runs
    /// once on the first frame the bounds are non-empty — most async
    /// model loads complete within a handful of frames after `setupScene`.
    ///
    /// Without this, iOS demos placing content at e.g. `z = -2` render
    /// in the bottom-third of the viewport: the default perspective
    /// camera at `[0, 0.3, 2]` looks at world origin, but content is far
    /// from origin. Android achieves the same effect via the per-demo
    /// `ModelNode(centerOrigin = …)` parameter; iOS now does it at the
    /// library level so every demo benefits without per-demo plumbing.
    ///
    /// Opt-out via ``SceneView/autoCenterContent(_:)`` for scenes that
    /// rely on intentional off-centre placement.
    /// Smallest mesh extent (in meters) the auto-centre pass will accept
    /// as "content has loaded enough to be centred". Below this threshold
    /// the bounds are assumed to come from an async load that hasn't
    /// populated yet, and the pass is deferred to the next frame.
    private static let minVisualExtent: Float = 0.001

    @MainActor
    private func refreshContentCentering() {
        guard !didCenterContent else { return }
        // Query bounds in contentRoot-local space so they're invariant of
        // `entities.root`'s rotation + scale (applied every frame by
        // `applyCamera()`). Sampling `relativeTo: entities.root` would
        // rescale extents with pinch-zoom, opening a race where an
        // early-pinch + slow-loading model flips the `minVisualExtent`
        // gate true/false between frames and centres at the wrong moment.
        let bounds = entities.contentRoot.visualBounds(relativeTo: entities.contentRoot)
        let extents = bounds.extents
        // Skip empty / degenerate bounds — async loads not done yet.
        // RealityKit's empty `BoundingBox` returns `min = +∞`, `max = -∞`
        // so the `max - min` extents are non-finite (or zero for a
        // zero-extent placeholder); both cases are caught here.
        guard extents.x.isFinite, extents.y.isFinite, extents.z.isFinite else { return }
        let extentMax = max(extents.x, extents.y, extents.z)
        guard extentMax > Self.minVisualExtent else { return }
        entities.contentRoot.position = -bounds.center
        didCenterContent = true
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
            // Cache the resource for the update: closure when the caller
            // asked for the IBL to also render as a skybox background. Set
            // to `nil` when `showSkybox = false` so the update: closure
            // reverts to `.default` and the background goes neutral again.
            // Ported from Eliott Radcliffe's sceneview-swift PR #1.
            loadedSkyboxResource = env.showSkybox ? resource : nil
            #endif

            // visionOS immersive-space skybox: also load the equirectangular
            // HDR as a `TextureResource` for the inverted-sphere skybox.
            // `EnvironmentResource.skybox` is a cubemap and cannot UV-map
            // onto a sphere, so the HDR file itself is loaded. Only when the
            // caller opted into `.immersiveSpace()` and `showSkybox` is set —
            // skipped entirely for windowed visionOS scenes. Closes #1235.
            #if os(visionOS)
            if immersiveSpace, env.showSkybox, let hdr = env.hdrResource {
                loadedImmersiveSkyboxTexture =
                    await VisionOSSkybox.loadEquirectangularTexture(named: hdr)
            } else {
                loadedImmersiveSkyboxTexture = nil
            }
            #endif
        } catch {
            // Environment loading failed — scene continues with default lighting
            print("[SceneViewSwift] Failed to load environment '\(env.name)': \(error)")
            loadedSkyboxResource = nil
            #if os(visionOS)
            loadedImmersiveSkyboxTexture = nil
            #endif
        }
    }

    // MARK: - Camera

    /// Default field-of-view when not in `.firstPerson` mode. Used by
    /// `applyCamera()` to keep orbit / pan FOV stable while `.firstPerson`
    /// remains free to mutate `camera.fov` via pinch.
    private static let baselineFov: Float = 60

    @MainActor
    private func applyCamera() {
        // Sync the camera state's mode with the modifier-provided value. Done
        // every frame because the modifier propagates as a `let` while the
        // camera state is `@State` — without this, calling
        // `.cameraControls(.pan)` would be silently ignored. Closes #1034.
        if camera.mode != cameraControlMode {
            camera.mode = cameraControlMode
            // Reset the pinched FOV back to baseline when LEAVING firstPerson,
            // so the next firstPerson entry starts at 60° rather than at the
            // last clamped value. Without this guard, a user who pinches FOV
            // down to 30° in firstPerson then switches to orbit sees the
            // 30° stick around (visible as a stuck zoom-in); switching back
            // to firstPerson would then resume at 30° instead of resetting.
            // R3 MAJOR finding.
            if camera.mode != .firstPerson && camera.fov != Self.baselineFov {
                camera.fov = Self.baselineFov
            }
        }

        // Per-mode application of orientation, zoom, and translation.
        // Mirrors Android's `CameraGestureDetector` modes
        // (ORBIT / PAN / FREE_FLIGHT) — closes #1034 (last #928 silent-stub
        // item).
        //
        // History (Eliott Radcliffe, sceneview-swift PR #1): orbit + pan
        // used to "fake" the camera move by rotating + scaling the scene
        // root while the perspective camera stayed pinned at `[0, 0.3, 2]`.
        // That made a globally-applied skybox appear stationary while the
        // content visually orbited around the user — wrong from the
        // camera's POV. The fix moves the actual perspective camera in
        // world-space via `cameraPosition()` so the skybox correctly wraps
        // and content stays still. visionOS has no manual camera entity
        // (the headset is the camera), so it retains the rotate-root path.
        switch camera.mode {
        case .orbit, .pan:
            // True orbit / pan: position the camera in world-space; the
            // scene root stays at origin with identity transform so the
            // skybox wraps correctly. `cameraPosition()` already encodes
            // `camera.target`, so pan's lateral target drift propagates
            // directly into the camera position. Pinch in/out changes
            // `camera.orbitRadius`, which is now the camera's literal
            // distance to the target (vs. the previous `5.0 / radius`
            // scene-scale hack).
            entities.root.orientation = simd_quatf(angle: 0, axis: [0, 1, 0])
            entities.root.scale = SIMD3<Float>(repeating: 1)
            entities.root.position = .zero
            #if os(iOS) || os(macOS)
            let pos = camera.cameraPosition()
            entities.perspCamera.look(at: camera.target, from: pos, relativeTo: nil)
            // Use the static baseline FOV for orbit / pan rather than
            // mirroring `camera.fov`, which the firstPerson pinch mutates.
            // Without this, switching firstPerson → orbit kept the 30°
            // pinched FOV on the perspective camera (R3 MAJOR — "FOV
            // bleed"). The static-baseline write is a no-op on identical
            // values so the property doesn't churn.
            if entities.perspCamera.camera.fieldOfViewInDegrees != Self.baselineFov {
                entities.perspCamera.camera.fieldOfViewInDegrees = Self.baselineFov
            }
            #else
            // visionOS fallback: rotate the scene root since there's no
            // manual camera entity to move. Skybox wrap correctness is
            // moot under passthrough.
            let yaw = simd_quatf(angle: -camera.azimuth, axis: [0, 1, 0])
            let pitch = simd_quatf(angle: -camera.elevation, axis: [1, 0, 0])
            entities.root.orientation = yaw * pitch
            let zoomScale = 2.0 / max(camera.orbitRadius, camera.minRadius)
            entities.root.scale = SIMD3<Float>(repeating: zoomScale)
            entities.root.position = camera.mode == .pan ? -camera.target * zoomScale : .zero
            #endif

        case .firstPerson:
            // First-person inspection: pinch mutates FOV (see pinchGesture)
            // rather than the orbit radius. Currently rotates the scene
            // root around world origin while the camera stays at a fixed
            // eye — visually a tight orbit at radius `2`. NOT a true
            // "stand still and look around" camera-orientation rotation;
            // tracked as a v4.4.0 follow-up in ``CameraControlMode/firstPerson``.
            //
            // KNOWN LIMITATION (R3 MAJOR, deferred to v4.4.0): switching
            // orbit → firstPerson teleports the perspective camera from
            // its last orbit position to `[0, 0.3, 2]` without
            // interpolation. The "true look-around" rewrite tracked under
            // #1034 will resolve this by rotating the camera entity in
            // place instead of repositioning it on mode entry.
            let yaw = simd_quatf(angle: -camera.azimuth, axis: [0, 1, 0])
            let pitch = simd_quatf(angle: -camera.elevation, axis: [1, 0, 0])
            entities.root.orientation = yaw * pitch
            entities.root.scale = SIMD3<Float>(repeating: 1)
            entities.root.position = .zero
            #if os(iOS) || os(macOS)
            // Pin the camera at a fixed eye and reset to look at origin
            // so a mode switch from orbit doesn't strand the camera at
            // the last orbit position. Sync FOV to the controls state —
            // this is the ONLY mode that mirrors `camera.fov` so pinch
            // can dolly without affecting orbit's baseline (see orbit/pan
            // case above for the corresponding non-write).
            entities.perspCamera.look(at: .zero, from: [0, 0.3, 2], relativeTo: nil)
            if entities.perspCamera.camera.fieldOfViewInDegrees != camera.fov {
                entities.perspCamera.camera.fieldOfViewInDegrees = camera.fov
            }
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
