#if os(iOS)
import SwiftUI
import RealityKit
import ARKit
import CoreImage

/// A SwiftUI view for augmented reality using ARKit + RealityKit.
///
/// Mirrors SceneView Android's `ARSceneView { }` composable — place content
/// relative to real-world surfaces, images, and anchors.
///
/// Uses `ARView` (UIKit) wrapped in `UIViewRepresentable` for full ARKit
/// support on iPhone. Provides plane detection, tap-to-place hit testing,
/// and coaching overlay.
///
/// ```swift
/// ARSceneView(
///     planeDetection: .horizontal,
///     onTapOnPlane: { position in
///         // Place a 10 cm cube at the tapped surface
///         let cube = GeometryNode.cube(size: 0.1, color: .blue)
///         let anchor = AnchorNode.world(position: position)
///         anchor.add(cube.entity)
///         // arView.scene.addAnchor(anchor.entity) — done automatically
///     }
/// )
/// ```
public struct ARSceneView: UIViewRepresentable {
    private var planeDetection: PlaneDetectionMode
    private var showPlaneOverlay: Bool
    private var showCoachingOverlay: Bool
    private var cameraExposure: Float?
    private var onTapOnPlane: ((SIMD3<Float>, ARView) -> Void)?
    private var onSessionStarted: ((ARView) -> Void)?
    private var imageTrackingDatabase: Set<ARReferenceImage>?
    private var onImageDetected: ((String, AnchorNode, ARView) -> Void)?
    private var onFrame: ((ARFrame, ARView) -> Void)?

    /// Plane detection modes matching Android's ARCore config.
    public enum PlaneDetectionMode: Sendable {
        case none
        case horizontal
        case vertical
        case both

        var arPlaneDetection: ARWorldTrackingConfiguration.PlaneDetection {
            switch self {
            case .none: return []
            case .horizontal: return .horizontal
            case .vertical: return .vertical
            case .both: return [.horizontal, .vertical]
            }
        }
    }

    /// Creates an AR scene with plane detection, image tracking, and tap-to-place.
    ///
    /// - Parameters:
    ///   - planeDetection: Which plane orientations to detect. Default horizontal.
    ///   - showPlaneOverlay: Whether to visualize detected planes. Default true.
    ///   - showCoachingOverlay: Whether to show coaching when tracking limited. Default true.
    ///   - imageTrackingDatabase: Set of reference images to detect. Use
    ///     `AugmentedImageNode.createImageDatabase()` or
    ///     `AugmentedImageNode.referenceImages(inGroupNamed:)` to create.
    ///   - cameraExposure: Optional exposure compensation for the camera feed, in EV
    ///     (exposure value) stops. When non-nil, a post-processing brightness adjustment
    ///     is applied to the rendered frame via `ARView.renderCallbacks.postProcess`.
    ///     Positive values brighten the scene; negative values darken it. A value of `0.0`
    ///     leaves the camera feed unchanged. Pass `nil` (the default) to skip any
    ///     exposure override and rely on ARKit's built-in auto-exposure.
    ///
    ///     Mirrors Android's `ARSceneView(cameraExposure: Float?)` parameter, which
    ///     overrides Filament's camera aperture/shutter/ISO when ARCore's auto-exposure
    ///     does not match the Camera2 output on a given device.
    ///
    ///     Requires iOS 15.0+. On earlier OS versions the value is stored but has no effect.
    ///   - onTapOnPlane: Called with (worldPosition, arView) when user taps on a plane.
    ///   - onImageDetected: Called with (imageName, anchorNode, arView) when a reference
    ///     image is detected. Add content to the anchor and call
    ///     `arView.scene.addAnchor(anchor.entity)`.
    public init(
        planeDetection: PlaneDetectionMode = .horizontal,
        showPlaneOverlay: Bool = true,
        showCoachingOverlay: Bool = true,
        cameraExposure: Float? = nil,
        imageTrackingDatabase: Set<ARReferenceImage>? = nil,
        onTapOnPlane: ((SIMD3<Float>, ARView) -> Void)? = nil,
        onImageDetected: ((String, AnchorNode, ARView) -> Void)? = nil,
        onFrame: ((ARFrame, ARView) -> Void)? = nil
    ) {
        self.planeDetection = planeDetection
        self.showPlaneOverlay = showPlaneOverlay
        self.showCoachingOverlay = showCoachingOverlay
        self.cameraExposure = cameraExposure
        self.imageTrackingDatabase = imageTrackingDatabase
        self.onTapOnPlane = onTapOnPlane
        self.onImageDetected = onImageDetected
        self.onFrame = onFrame
    }

    /// Called once when the AR session starts. Use to add initial content.
    public func onSessionStarted(
        _ handler: @escaping (ARView) -> Void
    ) -> ARSceneView {
        var copy = self
        copy.onSessionStarted = handler
        return copy
    }

    /// Sets an exposure compensation override for the AR camera feed.
    ///
    /// Positive values brighten the rendered scene; negative values darken it. One stop
    /// equals a doubling or halving of brightness. Pass `nil` to remove any override and
    /// rely on ARKit's built-in auto-exposure.
    ///
    /// Mirrors Android's `cameraExposure` parameter on `ARSceneView`, which overrides
    /// Filament's camera aperture/shutter/ISO when ARCore's auto-exposure does not match
    /// the Camera2 output on a given device.
    ///
    /// Implemented via `ARView.renderCallbacks.postProcess` (iOS 15.0+) using a
    /// `CIColorControls` brightness filter. On earlier OS versions, the call is a no-op.
    ///
    /// - Parameter ev: Exposure compensation in EV stops. `0.0` = no change, positive
    ///   values brighten, negative values darken.
    /// - Returns: A copy of this view with the exposure override applied.
    public func cameraExposure(_ ev: Float?) -> ARSceneView {
        var copy = self
        copy.cameraExposure = ev
        return copy
    }

    /// Called on every updated AR frame. Use for debug logging (e.g.
    /// streaming to the Rerun viewer via ``RerunBridge``) or custom
    /// per-frame analysis. Mirrors Android's `onSessionUpdated` callback.
    ///
    /// Runs on the ARKit delegate queue — do NOT block here. For I/O,
    /// hand the frame off to a background queue.
    public func onFrame(
        _ handler: @escaping (ARFrame, ARView) -> Void
    ) -> ARSceneView {
        var copy = self
        copy.onFrame = handler
        return copy
    }

    // MARK: - UIViewRepresentable

    public func makeUIView(context: Context) -> ARView {
        let arView = ARView(frame: .zero)
        arView.automaticallyConfigureSession = false

        // Configure AR session
        let config = ARWorldTrackingConfiguration()
        config.planeDetection = planeDetection.arPlaneDetection
        config.environmentTexturing = .automatic

        // Image tracking
        if let images = imageTrackingDatabase, !images.isEmpty {
            config.detectionImages = images
            config.maximumNumberOfTrackedImages = images.count
        }

        if ARWorldTrackingConfiguration.supportsSceneReconstruction(.mesh) {
            config.sceneReconstruction = .mesh
        }

        arView.session.run(config, options: [.resetTracking, .removeExistingAnchors])
        arView.session.delegate = context.coordinator

        // Plane visualization
        if showPlaneOverlay && planeDetection != .none {
            arView.debugOptions.insert(.showAnchorGeometry)
        }

        // Coaching overlay
        if showCoachingOverlay {
            let coaching = ARCoachingOverlayView()
            coaching.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            coaching.session = arView.session
            coaching.goal = coachingGoal
            coaching.activatesAutomatically = true
            arView.addSubview(coaching)
        }

        // Tap gesture
        let tapRecognizer = UITapGestureRecognizer(
            target: context.coordinator,
            action: #selector(Coordinator.handleTap(_:))
        )
        arView.addGestureRecognizer(tapRecognizer)

        // Store reference for coordinator
        context.coordinator.arView = arView

        // Initial content callback
        onSessionStarted?(arView)

        return arView
    }

    public func updateUIView(_ arView: ARView, context: Context) {
        context.coordinator.onTapOnPlane = onTapOnPlane
        context.coordinator.onImageDetected = onImageDetected
        context.coordinator.onFrame = onFrame

        // Apply camera exposure override via post-processing (iOS 15.0+).
        // Converts the EV value to a CIColorControls brightness offset and installs
        // (or removes) a post-process render callback on the ARView.
        applyExposure(cameraExposure, to: arView)
    }

    // MARK: - Exposure helpers

    /// Applies (or removes) a brightness post-process callback on `arView` to simulate
    /// the `cameraExposure` EV override from Android's ARSceneView.
    ///
    /// An EV of `+1` doubles perceived brightness (maps to +0.5 CIColorControls
    /// brightness), an EV of `-1` halves it (maps to -0.5). The mapping is linear and
    /// intentionally simple — Filament on Android uses physical aperture/shutter/ISO,
    /// but RealityKit does not expose those parameters, so a CIFilter post-process is
    /// the closest available approximation.
    @available(iOS 15.0, *)
    private func applyExposurePostProcess(_ ev: Float, to arView: ARView) {
        arView.renderCallbacks.postProcess = { [ev] context in
            guard
                let filter = CIFilter(name: "CIColorControls"),
                // CIImage(mtlTexture:) is failable — texture format must be supported.
                let ciImage = CIImage(mtlTexture: context.sourceColorTexture, options: nil)
            else { return }
            // Map EV stops to CIColorControls brightness range [-1, 1]:
            // each EV stop equals 0.5 brightness units so that ±2 EV covers the full range.
            let brightness = NSNumber(value: Double(ev) * 0.5)
            filter.setValue(ciImage, forKey: kCIInputImageKey)
            filter.setValue(brightness, forKey: kCIInputBrightnessKey)
            guard let outputImage = filter.outputImage else { return }
            let ciContext = CIContext(mtlDevice: context.device)
            ciContext.render(
                outputImage,
                to: context.targetColorTexture,
                commandBuffer: context.commandBuffer,
                bounds: outputImage.extent,
                colorSpace: CGColorSpaceCreateDeviceRGB()
            )
        }
    }

    private func applyExposure(_ ev: Float?, to arView: ARView) {
        guard let ev = ev else {
            // Remove any previously installed post-process callback.
            if #available(iOS 15.0, *) {
                arView.renderCallbacks.postProcess = nil
            }
            return
        }
        if #available(iOS 15.0, *) {
            applyExposurePostProcess(ev, to: arView)
        }
        // On iOS < 15 the value is stored (via the modifier / init) but silently ignored.
    }

    public func makeCoordinator() -> Coordinator {
        Coordinator(
            onTapOnPlane: onTapOnPlane,
            planeDetection: planeDetection,
            onImageDetected: onImageDetected,
            onFrame: onFrame,
            imageTrackingDatabase: imageTrackingDatabase,
            // Mesh reconstruction is implicitly enabled in makeUIView when
            // supported; mirror the flag here so it's re-applied after an
            // interruption (closes part of #928).
            enableMeshReconstruction: true,
            environmentTexturing: .automatic
        )
    }

    private var coachingGoal: ARCoachingOverlayView.Goal {
        switch planeDetection {
        case .horizontal: return .horizontalPlane
        case .vertical: return .verticalPlane
        case .both, .none: return .anyPlane
        }
    }

    // MARK: - Coordinator

    public class Coordinator: NSObject, ARSessionDelegate {
        var onTapOnPlane: ((SIMD3<Float>, ARView) -> Void)?
        var onImageDetected: ((String, AnchorNode, ARView) -> Void)?
        var onFrame: ((ARFrame, ARView) -> Void)?
        var planeDetection: PlaneDetectionMode
        // Tracking config that must survive an interruption — previously only
        // `planeDetection` was preserved, so any image database / mesh recon /
        // environment-texturing flag was lost the moment the user backgrounded
        // the app and returned. Closes part of #928. The full set is re-applied
        // in `sessionInterruptionEnded(_:)` below.
        var imageTrackingDatabase: Set<ARReferenceImage>?
        var enableMeshReconstruction: Bool = true
        var environmentTexturing: ARWorldTrackingConfiguration.EnvironmentTexturing = .automatic
        weak var arView: ARView?
        private var detectedImageNames: Set<String> = []

        init(
            onTapOnPlane: ((SIMD3<Float>, ARView) -> Void)?,
            planeDetection: PlaneDetectionMode,
            onImageDetected: ((String, AnchorNode, ARView) -> Void)? = nil,
            onFrame: ((ARFrame, ARView) -> Void)? = nil,
            imageTrackingDatabase: Set<ARReferenceImage>? = nil,
            enableMeshReconstruction: Bool = true,
            environmentTexturing: ARWorldTrackingConfiguration.EnvironmentTexturing = .automatic
        ) {
            self.onTapOnPlane = onTapOnPlane
            self.planeDetection = planeDetection
            self.onImageDetected = onImageDetected
            self.onFrame = onFrame
            self.imageTrackingDatabase = imageTrackingDatabase
            self.enableMeshReconstruction = enableMeshReconstruction
            self.environmentTexturing = environmentTexturing
        }

        @objc func handleTap(_ recognizer: UITapGestureRecognizer) {
            guard let arView = recognizer.view as? ARView else { return }
            let location = recognizer.location(in: arView)

            // Raycast against detected planes
            let results = arView.raycast(
                from: location,
                allowing: .estimatedPlane,
                alignment: .any
            )
            if let firstResult = results.first {
                let column = firstResult.worldTransform.columns.3
                let position = SIMD3<Float>(column.x, column.y, column.z)
                onTapOnPlane?(position, arView)
            }
        }

        // MARK: - ARSessionDelegate

        public func session(_ session: ARSession, didUpdate frame: ARFrame) {
            guard let arView = arView, let onFrame = onFrame else { return }
            onFrame(frame, arView)
        }

        public func session(_ session: ARSession, didAdd anchors: [ARAnchor]) {
            guard let arView = arView, let onImageDetected = onImageDetected else { return }

            for anchor in anchors {
                guard let imageAnchor = anchor as? ARImageAnchor,
                      let imageName = imageAnchor.referenceImage.name,
                      !detectedImageNames.contains(imageName) else { continue }

                detectedImageNames.insert(imageName)
                let anchorEntity = AnchorEntity(anchor: imageAnchor)
                let anchorNode = AnchorNode(entity: anchorEntity)
                onImageDetected(imageName, anchorNode, arView)
            }
        }

        public func session(
            _ session: ARSession,
            didFailWithError error: Error
        ) {
            print("[SceneViewSwift] AR session error: \(error.localizedDescription)")
        }

        public func sessionWasInterrupted(_ session: ARSession) {
            print("[SceneViewSwift] AR session interrupted")
        }

        public func sessionInterruptionEnded(_ session: ARSession) {
            print("[SceneViewSwift] AR session interruption ended — resuming with full config")
            // Re-apply the FULL tracking configuration that was active before the
            // interruption — previously only `planeDetection` survived, so consumers
            // doing image tracking / mesh reconstruction / non-default environment
            // texturing lost those features on every background→foreground cycle.
            // Closes part of #928 (ARSceneView.sessionInterruptionEnded silent stub).
            let config = ARWorldTrackingConfiguration()
            config.planeDetection = planeDetection.arPlaneDetection
            config.environmentTexturing = environmentTexturing
            if let images = imageTrackingDatabase, !images.isEmpty {
                config.detectionImages = images
                config.maximumNumberOfTrackedImages = images.count
            }
            if enableMeshReconstruction,
               ARWorldTrackingConfiguration.supportsSceneReconstruction(.mesh) {
                config.sceneReconstruction = .mesh
            }
            // Reset detected images so they can be re-detected after interruption.
            // (The anchors themselves are kept — we don't pass .removeExistingAnchors
            // here because the user's content + AnchorEntity references remain valid
            // across an interruption per Apple's ARKit guide.)
            detectedImageNames.removeAll()
            session.run(config)
        }
    }
}

// MARK: - AR Anchor helpers (mirrors Android's ARCore anchor API)

/// A wrapper around ARKit anchors for placing content in the real world.
///
/// Mirrors SceneView Android's `AnchorNode`.
public struct AnchorNode: Sendable {
    /// The underlying RealityKit anchor entity.
    public let entity: AnchorEntity

    /// Creates an anchor at a world position.
    public static func world(position: SIMD3<Float>) -> AnchorNode {
        let anchor = AnchorEntity(world: position)
        return AnchorNode(entity: anchor)
    }

    /// Creates an anchor on a detected plane.
    ///
    /// - Parameters:
    ///   - alignment: Horizontal or vertical plane.
    ///   - minimumBounds: Minimum plane size to anchor to.
    public static func plane(
        alignment: PlaneAlignment = .horizontal,
        minimumBounds: SIMD2<Float> = .init(0.1, 0.1)
    ) -> AnchorNode {
        let anchor: AnchorEntity
        switch alignment {
        case .horizontal:
            anchor = AnchorEntity(plane: .horizontal, minimumBounds: minimumBounds)
        case .vertical:
            anchor = AnchorEntity(plane: .vertical, minimumBounds: minimumBounds)
        }
        return AnchorNode(entity: anchor)
    }

    /// Adds a child entity to this anchor.
    public func add(_ child: Entity) {
        entity.addChild(child)
    }

    /// Removes a child entity from this anchor.
    public func remove(_ child: Entity) {
        entity.removeChild(child)
    }

    /// Removes all child entities from this anchor.
    public func removeAll() {
        for child in entity.children {
            entity.removeChild(child)
        }
    }

    /// Plane alignment type matching Android's Plane.Type.
    public enum PlaneAlignment: Sendable {
        case horizontal
        case vertical
    }
}
#endif // os(iOS)
