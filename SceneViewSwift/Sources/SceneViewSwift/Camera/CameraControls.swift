#if os(iOS) || os(macOS) || os(visionOS)
import SwiftUI
import RealityKit

/// Camera interaction mode for the 3D scene.
///
/// Mirrors SceneView Android's camera manipulator modes
/// (`CameraGestureDetector` + `FovZoomCameraManipulator`).
public enum CameraControlMode: Sendable {
    /// Orbit around a target point. Drag rotates the scene around the orbit
    /// pivot; pinch dollies in/out by scaling the scene root.
    case orbit

    /// Pan the camera in the view plane. Drag translates the orbit target
    /// laterally (the scene appears to slide), pinch keeps dollying in/out.
    /// Rotation is preserved so callers can pan first then orbit.
    case pan

    /// First-person look-around. Drag rotates the scene with no orbit
    /// translation (camera stays put visually), pinch adjusts the
    /// perspective camera's field of view — mirrors Android's
    /// `FovZoomCameraManipulator`.
    case firstPerson
}

/// Manages camera orbit, pan, and zoom via SwiftUI gestures.
///
/// Mirrors SceneView Android's `CameraManipulator` — handles touch-to-orbit
/// conversion with inertia and smooth damping.
///
/// ```swift
/// SceneView { content in
///     // ...
/// }
/// .cameraControls(.orbit)
/// ```
public struct CameraControls: Sendable {
    /// Current control mode.
    public var mode: CameraControlMode

    /// Target point the camera orbits around (orbit mode).
    public var target: SIMD3<Float> = .zero

    /// Distance from camera to target (orbit mode).
    public var orbitRadius: Float = 5.0

    /// Horizontal orbit angle in radians.
    public var azimuth: Float = 0.0

    /// Vertical orbit angle in radians, clamped to avoid gimbal lock.
    public var elevation: Float = Float.pi / 6  // 30 degrees

    /// Minimum orbit radius (zoom-in limit).
    public var minRadius: Float = 0.5

    /// Maximum orbit radius (zoom-out limit).
    public var maxRadius: Float = 50.0

    /// Orbit drag sensitivity (radians per screen point).
    public var sensitivity: Float = 0.005

    /// Whether auto-rotation is active.
    public var isAutoRotating: Bool = false

    /// Auto-rotation speed in radians per second.
    public var autoRotateSpeed: Float = 0.3

    /// Inertia velocity for smooth deceleration after drag ends.
    public var inertiaVelocity: CGSize = .zero

    /// Inertia damping factor (0 = instant stop, 0.99 = very slow deceleration).
    public var inertiaDamping: Float = 0.92

    /// Minimum azimuth angle in radians. Nil means unconstrained.
    public var minAzimuth: Float? = nil

    /// Maximum azimuth angle in radians. Nil means unconstrained.
    public var maxAzimuth: Float? = nil

    /// Minimum elevation angle in radians. Default ~-85 degrees.
    public var minElevation: Float = -(Float.pi / 2 - 0.087)

    /// Maximum elevation angle in radians. Default ~85 degrees.
    public var maxElevation: Float = Float.pi / 2 - 0.087

    /// Pan speed multiplier (pan mode only).
    public var panSpeed: Float = 0.01

    /// First-person move speed (first-person mode only).
    public var moveSpeed: Float = 0.1

    /// Field-of-view in degrees for ``CameraControlMode/firstPerson`` (pinch zoom).
    /// Mirrors Android's `FovZoomCameraManipulator` (default range 10°..120°).
    public var fov: Float = 60.0

    /// Minimum FOV in degrees (firstPerson pinch-in limit).
    public var minFov: Float = 10.0

    /// Maximum FOV in degrees (firstPerson pinch-out limit).
    public var maxFov: Float = 120.0

    /// Per-pixel FOV delta in degrees (firstPerson pinch sensitivity).
    /// Mirrors Android `FovZoomCameraManipulator.DEFAULT_PINCH_FOV_SPEED`.
    public var pinchFovSpeed: Float = 0.05

    /// Whether touch/drag interaction is enabled.
    public var isEnabled: Bool = true

    public init(mode: CameraControlMode = .orbit) {
        self.mode = mode
    }

    /// Creates camera controls with customized orbit limits.
    ///
    /// - Parameters:
    ///   - mode: Camera control mode.
    ///   - minRadius: Minimum zoom distance.
    ///   - maxRadius: Maximum zoom distance.
    ///   - minElevation: Minimum vertical angle in radians.
    ///   - maxElevation: Maximum vertical angle in radians.
    ///   - sensitivity: Drag sensitivity.
    public init(
        mode: CameraControlMode = .orbit,
        minRadius: Float = 0.5,
        maxRadius: Float = 50.0,
        minElevation: Float = -(Float.pi / 2 - 0.087),
        maxElevation: Float = Float.pi / 2 - 0.087,
        sensitivity: Float = 0.005
    ) {
        self.mode = mode
        self.minRadius = minRadius
        self.maxRadius = maxRadius
        self.minElevation = minElevation
        self.maxElevation = maxElevation
        self.sensitivity = sensitivity
    }

    // MARK: - Computed Camera Position

    /// Computes the camera position from current orbit parameters.
    ///
    /// Converts spherical coordinates (azimuth, elevation, radius) to
    /// a Cartesian position relative to the target point.
    ///
    /// - Returns: World-space camera position.
    public func cameraPosition() -> SIMD3<Float> {
        let cosElev = cos(elevation)
        let x = target.x + orbitRadius * cosElev * sin(azimuth)
        let y = target.y + orbitRadius * sin(elevation)
        let z = target.z + orbitRadius * cosElev * cos(azimuth)
        return SIMD3<Float>(x, y, z)
    }

    /// Computes a look-at transform matrix from the camera to the target.
    ///
    /// - Returns: A 4x4 view matrix positioning the camera at the orbit
    ///   location and looking towards the target.
    public func cameraTransform() -> simd_float4x4 {
        let eye = cameraPosition()
        let up = SIMD3<Float>(0, 1, 0)

        let forward = simd_normalize(target - eye)
        let right = simd_normalize(simd_cross(forward, up))
        let correctedUp = simd_cross(right, forward)

        return simd_float4x4(columns: (
            SIMD4<Float>(right.x, correctedUp.x, -forward.x, 0),
            SIMD4<Float>(right.y, correctedUp.y, -forward.y, 0),
            SIMD4<Float>(right.z, correctedUp.z, -forward.z, 0),
            SIMD4<Float>(eye.x, eye.y, eye.z, 1)
        ))
    }

    /// Returns the inverse rotation to apply to the scene root entity
    /// (rotating content is equivalent to orbiting the camera).
    public func sceneRotation() -> simd_quatf {
        let yaw = simd_quatf(angle: -azimuth, axis: [0, 1, 0])
        let pitch = simd_quatf(angle: -elevation, axis: [1, 0, 0])
        return yaw * pitch
    }

    // MARK: - Gesture Handling

    /// Updates orbit angles from a drag gesture delta.
    ///
    /// In orbit mode, rotates around the target. In pan mode, translates the target.
    /// In first-person mode, adjusts look direction.
    ///
    /// - Parameter delta: The drag delta in screen points (incremental, not total).
    public mutating func handleDrag(_ delta: CGSize) {
        guard isEnabled else { return }

        switch mode {
        case .orbit:
            azimuth -= Float(delta.width) * sensitivity
            elevation += Float(delta.height) * sensitivity
            clampElevation()
            clampAzimuth()

        case .pan:
            let right = SIMD3<Float>(cos(azimuth), 0, -sin(azimuth))
            let up = SIMD3<Float>(0, 1, 0)
            target += right * Float(delta.width) * panSpeed
            target += up * Float(-delta.height) * panSpeed

        case .firstPerson:
            azimuth -= Float(delta.width) * sensitivity
            elevation += Float(delta.height) * sensitivity
            clampElevation()
        }

        // Store velocity for inertia
        inertiaVelocity = delta
    }

    /// Updates orbit radius from a magnification gesture.
    ///
    /// In ``CameraControlMode/orbit`` and ``CameraControlMode/pan`` this scales
    /// the dolly radius (zoom). In ``CameraControlMode/firstPerson`` it
    /// adjusts the perspective camera's field-of-view instead — mirrors
    /// Android's `FovZoomCameraManipulator`.
    ///
    /// - Parameter scale: The pinch gesture magnification factor.
    public mutating func handlePinch(_ scale: CGFloat) {
        guard isEnabled else { return }
        switch mode {
        case .orbit, .pan:
            orbitRadius /= Float(scale)
            orbitRadius = Swift.min(Swift.max(orbitRadius, minRadius), maxRadius)
        case .firstPerson:
            // Pinch out (scale > 1) ⇒ user wants to zoom IN ⇒ smaller FOV.
            fov /= Float(scale)
            fov = Swift.min(Swift.max(fov, minFov), maxFov)
        }
    }

    /// Applies inertia deceleration. Call this on each frame after drag ends.
    ///
    /// - Returns: `true` while inertia is still active.
    @discardableResult
    public mutating func applyInertia() -> Bool {
        let threshold: CGFloat = 0.01
        guard abs(inertiaVelocity.width) > threshold
                || abs(inertiaVelocity.height) > threshold else {
            inertiaVelocity = .zero
            return false
        }

        azimuth -= Float(inertiaVelocity.width) * sensitivity
        elevation += Float(inertiaVelocity.height) * sensitivity
        clampElevation()

        inertiaVelocity.width *= CGFloat(inertiaDamping)
        inertiaVelocity.height *= CGFloat(inertiaDamping)
        return true
    }

    /// Advances auto-rotation by the given time delta.
    ///
    /// - Parameter dt: Time elapsed since last frame in seconds.
    public mutating func applyAutoRotation(dt: Float) {
        guard isAutoRotating else { return }
        azimuth += autoRotateSpeed * dt
    }

    // MARK: - Convenience builders

    /// Returns a copy with the orbit target changed.
    @discardableResult
    public func withTarget(_ target: SIMD3<Float>) -> CameraControls {
        var copy = self
        copy.target = target
        return copy
    }

    /// Returns a copy with the initial orbit radius changed.
    @discardableResult
    public func withRadius(_ radius: Float) -> CameraControls {
        var copy = self
        copy.orbitRadius = radius
        return copy
    }

    /// Returns a copy with azimuth limits set.
    @discardableResult
    public func withAzimuthLimits(min: Float, max: Float) -> CameraControls {
        var copy = self
        copy.minAzimuth = min
        copy.maxAzimuth = max
        return copy
    }

    /// Returns a copy with elevation limits set.
    @discardableResult
    public func withElevationLimits(min: Float, max: Float) -> CameraControls {
        var copy = self
        copy.minElevation = min
        copy.maxElevation = max
        return copy
    }

    // MARK: - Private

    private mutating func clampElevation() {
        elevation = Swift.min(Swift.max(elevation, minElevation), maxElevation)
    }

    private mutating func clampAzimuth() {
        if let minAz = minAzimuth, let maxAz = maxAzimuth {
            azimuth = Swift.min(Swift.max(azimuth, minAz), maxAz)
        }
    }
}

#endif // os(iOS) || os(macOS) || os(visionOS)
