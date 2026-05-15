#if os(iOS)
import Foundation
import Photos
import ReplayKit
import SwiftUI

/// Records the screen during an AR session via ReplayKit's
/// `RPScreenRecorder`. iOS port of Android's
/// `io.github.sceneview.ar.recording.ARRecorder` — record-only because
/// ARKit, unlike ARCore, does not expose a deterministic playback dataset.
///
/// ### What this records
///
/// `RPScreenRecorder.shared()` captures the screen pixels at native
/// resolution into a QuickTime `.mov` file. It is NOT an ARSession
/// dataset (no IMU, no depth, no per-frame anchors) — the resulting
/// clip is replayable in the Photos app but cannot be fed back into
/// `ARSession` for deterministic replay. See `docs/docs/cheatsheet-ios.md`
/// parity table (#1036) for the rationale.
///
/// **Microphone capture** is not exposed in this initial v4.3.0 port —
/// `RPScreenRecorder.isMicrophoneEnabled` would require the host app to
/// declare `NSMicrophoneUsageDescription` in `Info.plist` and surface a
/// permission gate. Callers who need it can mutate
/// `RPScreenRecorder.shared().isMicrophoneEnabled = true` before
/// `startRecording()`; tracked as a v4.4.0 follow-up.
///
/// ### Usage
///
/// ```swift
/// @StateObject var recorder = ARRecorder()
///
/// ARSceneView(/* ... */)
/// HStack {
///     if recorder.isRecording {
///         Button("Stop") {
///             Task { try await recorder.stopRecording() }
///         }
///     } else {
///         Button("Record") {
///             Task { try await recorder.startRecording() }
///         }
///     }
/// }
/// ```
///
/// ### Threading
///
/// `startRecording` and `stopRecording` are `@MainActor async` because
/// ReplayKit's bridge requires the recorder to be mutated on the main
/// thread. `isRecording` is `nonisolated` so SwiftUI can read it from
/// any context.
///
/// ### Lineage
///
/// - `arsceneview/src/main/java/io/github/sceneview/ar/recording/ARRecorder.kt`
///   — full Android implementation with record + ARCore replay
/// - GitHub issue [#1032](https://github.com/sceneview/sceneview/issues/1032)
///   — deferred from v4.2.0 iOS parity sprint
/// - Agent 4 of the v4.1.0 5-agent post-ship audit — "drop playback, ship record only"
@MainActor
public final class ARRecorder: ObservableObject {

    /// Lifecycle states for the recorder.
    public enum State: Equatable {
        /// No recording in progress.
        case idle
        /// `RPScreenRecorder` is actively writing frames.
        case recording
        /// The last `startRecording` / `stopRecording` call failed; the
        /// associated value carries the human-readable error message.
        case error(String)
    }

    @Published public private(set) var state: State = .idle
    @Published public private(set) var lastOutputURL: URL? = nil

    /// `true` between successful `startRecording()` and `stopRecording(_:)`.
    /// `@Published` so SwiftUI views observing this `ObservableObject`
    /// repaint when recording starts/stops. Equivalent to checking
    /// `state == .recording` — kept as a separate property for the
    /// common case of binding a button label to it.
    public var isRecording: Bool { state == .recording }

    /// `true` if ReplayKit reports the device can record at all.
    /// Returns `false` on Mac Catalyst / older simulators where
    /// `RPScreenRecorder` is not wired up. Checked at every
    /// `startRecording()` so callers may also gate their UI off it.
    public var isAvailable: Bool {
        recorder.isAvailable
    }

    private let recorder: RPScreenRecorder

    public init(recorder: RPScreenRecorder = .shared()) {
        self.recorder = recorder
    }

    /// Starts an in-app screen recording. Throws if the recorder is
    /// unavailable (no AR support / no screen-record permission / app
    /// foregrounded a recording-blocking system view).
    ///
    /// Resolves once recording is actually live — the caller can flip a
    /// SwiftUI binding on completion and trust subsequent frames are
    /// being captured.
    @MainActor
    public func startRecording() async throws {
        guard isAvailable else {
            let msg = "RPScreenRecorder reports unavailable — device may not support screen capture, or a previous recording session is still tearing down"
            state = .error(msg)
            throw ARRecorderError.unavailable(msg)
        }
        if recorder.isRecording {
            // Calling startRecording twice is a programmer error; surface
            // it as an Error instead of silently no-opping so the UI can
            // show a "recording already in progress" indicator if needed.
            let msg = "ARRecorder.startRecording called while a recording is already in progress"
            state = .error(msg)
            throw ARRecorderError.alreadyRecording(msg)
        }
        // Bridge ReplayKit's completion-handler API into async/await.
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            recorder.startRecording { [weak self] error in
                Task { @MainActor in
                    guard let self else {
                        if let error {
                            continuation.resume(throwing: ARRecorderError.from(error))
                        } else {
                            continuation.resume()
                        }
                        return
                    }
                    if let error {
                        let mapped = ARRecorderError.from(error)
                        self.state = .error(mapped.errorDescription ?? error.localizedDescription)
                        continuation.resume(throwing: mapped)
                    } else {
                        self.state = .recording
                        continuation.resume()
                    }
                }
            }
        }
    }

    /// Stops the in-progress recording and writes the captured MP4 to
    /// `outputURL` (defaults to a freshly-generated temp file). Returns
    /// the file URL so the caller can share it / save it to Photos /
    /// upload it.
    ///
    /// - Parameter outputURL: Destination for the MP4. The parent
    ///   directory is created if it does not already exist. Pass `nil`
    ///   to write to `NSTemporaryDirectory()/ARRecording-<uuid>.mov`.
    @MainActor
    @discardableResult
    public func stopRecording(outputURL: URL? = nil) async throws -> URL {
        guard recorder.isRecording else {
            let msg = "ARRecorder.stopRecording called while no recording is in progress"
            state = .error(msg)
            throw ARRecorderError.notRecording(msg)
        }
        let destination = outputURL ?? Self.defaultOutputURL()
        // Ensure the parent directory exists before ReplayKit tries to
        // write into it (RPScreenRecorder will fail if it can't create
        // the file).
        try? FileManager.default.createDirectory(
            at: destination.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        return try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<URL, Error>) in
            recorder.stopRecording(withOutput: destination) { [weak self] error in
                Task { @MainActor in
                    guard let self else {
                        if let error {
                            continuation.resume(throwing: ARRecorderError.from(error))
                        } else {
                            continuation.resume(returning: destination)
                        }
                        return
                    }
                    if let error {
                        let mapped = ARRecorderError.from(error)
                        self.state = .error(mapped.errorDescription ?? error.localizedDescription)
                        continuation.resume(throwing: mapped)
                    } else {
                        self.state = .idle
                        self.lastOutputURL = destination
                        continuation.resume(returning: destination)
                    }
                }
            }
        }
    }

    /// Default destination for a recording when no `outputURL` is
    /// passed to ``stopRecording(outputURL:)``. Lives under the
    /// app's caches directory (`URLs(for: .cachesDirectory)`) — the OS
    /// may reclaim it under memory pressure but it survives normal app
    /// launches, giving the user time to share / preview / save to
    /// `PHPhotoLibrary` before disposal.
    ///
    /// Returns a path under `<caches>/ARRecorder/ARRecording-<uuid>.mov`.
    public static func defaultOutputURL() -> URL {
        let caches = FileManager.default
            .urls(for: .cachesDirectory, in: .userDomainMask)
            .first ?? URL(fileURLWithPath: NSTemporaryDirectory())
        let folder = caches.appendingPathComponent("ARRecorder", isDirectory: true)
        return folder.appendingPathComponent("ARRecording-\(UUID().uuidString).mov")
    }

    /// Saves a recorded `.mov` to the user's Photos library via
    /// `PHPhotoLibrary.performChanges`. Mirrors Android's
    /// `ARRecorder.exportToDownloads()` — the user-facing "make this
    /// recording permanent" gesture. Closes #1043 (item 2).
    ///
    /// Requires the host app's `Info.plist` to declare
    /// `NSPhotoLibraryAddUsageDescription`; the first call triggers the
    /// system permission sheet. On denial throws
    /// ``ARRecorderError/photoLibraryDenied(_:)``. On success the video
    /// appears in the user's Photos app under "Recents" with the
    /// "Recently Saved" smart-album link.
    ///
    /// - Parameter url: An existing `.mov` URL (typically from
    ///   ``stopRecording(outputURL:)``). The file must exist on disk;
    ///   `PHPhotoLibrary` copies it into the system-managed photo
    ///   library and the original may be cleaned up afterwards.
    /// - Throws: ``ARRecorderError/photoLibraryDenied(_:)`` if the user
    ///   denied access, ``ARRecorderError/photoLibrarySaveFailed(_:)``
    ///   if the underlying `performChanges` call returned an error.
    /// - Returns: The saved asset's `PHAsset.localIdentifier`, or `nil`
    ///   if `PHPhotoLibrary` did not vend a placeholder for the created
    ///   asset. Callers can later resolve the asset via
    ///   `PHAsset.fetchAssets(withLocalIdentifiers:options:)` or deep-link
    ///   to it. Mirrors Android `ARRecorder.exportToDownloads()` which
    ///   returns the saved `Uri?`.
    @MainActor
    @discardableResult
    public static func saveToPhotoLibrary(_ url: URL) async throws -> String? {
        // Ensure the file is on disk before we even ask for permission —
        // a missing-file error is more actionable than a generic
        // "save failed" after the consent sheet.
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw ARRecorderError.photoLibrarySaveFailed("file not found: \(url.lastPathComponent)")
        }
        // `PHAccessLevel.addOnly` is the minimum-privilege scope for
        // writing — does not grant read/list access to existing photos.
        let status = PHPhotoLibrary.authorizationStatus(for: .addOnly)
        let granted: PHAuthorizationStatus
        switch status {
        case .notDetermined:
            granted = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
        default:
            granted = status
        }
        guard granted == .authorized || granted == .limited else {
            throw ARRecorderError.photoLibraryDenied("Photos add-only permission denied (status=\(granted.rawValue))")
        }
        // Bridge PHPhotoLibrary's completion-handler API into async/await.
        return try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<String?, Error>) in
            // Captured inside the change block, read in the completion
            // handler. `placeholderForCreatedAsset` is only valid to read
            // synchronously within `performChanges`; its `localIdentifier`
            // remains stable and resolvable after the asset is committed.
            var localIdentifier: String?
            PHPhotoLibrary.shared().performChanges {
                let request = PHAssetCreationRequest.forAsset()
                // `shouldMoveFile = true`: Photos moves the source out of our
                // caches directory into the system-managed photo library
                // (atomically — if the save fails, Photos preserves the
                // original per Apple docs). Without this, the default
                // behaviour COPIES the file and the caches copy keeps
                // taking disk space until the OS reclaims it, which for
                // AR session recordings of hundreds of MB is a real
                // problem across multi-recording sessions.
                let options = PHAssetResourceCreationOptions()
                options.shouldMoveFile = true
                request.addResource(with: .video, fileURL: url, options: options)
                localIdentifier = request.placeholderForCreatedAsset?.localIdentifier
            } completionHandler: { success, error in
                if success {
                    continuation.resume(returning: localIdentifier)
                } else if let error {
                    continuation.resume(throwing: ARRecorderError.photoLibrarySaveFailed(error.localizedDescription))
                } else {
                    continuation.resume(throwing: ARRecorderError.photoLibrarySaveFailed("PHPhotoLibrary.performChanges returned !success without an error"))
                }
            }
        }
    }
}

/// Errors surfaced by ``ARRecorder``.
public enum ARRecorderError: LocalizedError, Equatable {
    case unavailable(String)
    case alreadyRecording(String)
    case notRecording(String)
    /// The user dismissed the system "Allow screen recording?" sheet on
    /// `startRecording()`. Maps to `RPRecordingErrorCode.userDeclined`.
    case permissionDenied(String)
    /// `RPRecordingErrorCode.disabled` — the device administrator (e.g.
    /// MDM profile, Screen Time restriction, parental controls) has
    /// disabled screen recording.
    case disabled(String)
    /// Any other `RPRecordingError` not matched above. The underlying
    /// `NSError.code` is preserved in the message for support reports.
    case other(String, code: Int)
    /// The user dismissed the system "Allow access to Photos?" sheet,
    /// or a parental control disabled write access. Thrown by
    /// ``ARRecorder/saveToPhotoLibrary(_:)``. The host app must declare
    /// `NSPhotoLibraryAddUsageDescription` in `Info.plist`.
    case photoLibraryDenied(String)
    /// `PHPhotoLibrary.performChanges` returned an error (or `success=false`
    /// without an error). Thrown by ``ARRecorder/saveToPhotoLibrary(_:)``.
    case photoLibrarySaveFailed(String)

    public var errorDescription: String? {
        switch self {
        case .unavailable(let msg),
             .alreadyRecording(let msg),
             .notRecording(let msg),
             .permissionDenied(let msg),
             .disabled(let msg),
             .other(let msg, _),
             .photoLibraryDenied(let msg),
             .photoLibrarySaveFailed(let msg):
            return msg
        }
    }

    /// Maps an `NSError` from `RPScreenRecorder`'s completion handler
    /// to a typed `ARRecorderError` so callers can switch on the case
    /// instead of string-matching `errorDescription`.
    static func from(_ error: Error) -> ARRecorderError {
        let nsErr = error as NSError
        // `RPRecordingErrorCode` values are defined in `<ReplayKit/RPError.h>`.
        // We can't `case let .userDeclined` switch because the type is
        // not exposed as a Swift enum in all SDK levels; match by raw int.
        switch nsErr.code {
        case -5803: return .permissionDenied(error.localizedDescription)
        case -5801: return .disabled(error.localizedDescription)
        default:    return .other(error.localizedDescription, code: nsErr.code)
        }
    }
}

/// SwiftUI helper that returns a ``ARRecorder`` instance scoped to the
/// surrounding view's lifetime. Mirrors Android's `rememberARRecorder()`
/// composable — wraps `@StateObject` so the same recorder instance
/// survives recompositions.
///
/// ```swift
/// struct MyARView: View {
///     @StateObject private var recorder = ARRecorder()
///     // ...
/// }
/// ```
///
/// The Android version is a top-level `@Composable fun rememberARRecorder()`;
/// SwiftUI's `@StateObject` property wrapper provides the same lifetime
/// guarantee, so `rememberARRecorder()` ships as a thin convenience
/// initialiser rather than a global function. Use either pattern —
/// `@StateObject` is the idiomatic SwiftUI choice.
public extension ARRecorder {
    /// Convenience initialiser that returns a fresh recorder bound to
    /// the shared `RPScreenRecorder`. Equivalent to
    /// `ARRecorder()` — provided so the call site reads symmetrically
    /// with the Android factory `rememberARRecorder()`.
    static func remembered() -> ARRecorder {
        ARRecorder()
    }
}
#endif // os(iOS)
