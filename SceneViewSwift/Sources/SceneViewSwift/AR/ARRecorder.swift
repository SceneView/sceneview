#if os(iOS)
import Foundation
import ReplayKit
import SwiftUI

/// Records the screen (and optionally microphone) during an AR session via
/// ReplayKit's `RPScreenRecorder`. iOS port of Android's
/// `io.github.sceneview.ar.recording.ARRecorder` — record-only because
/// ARKit, unlike ARCore, does not expose a deterministic playback dataset.
///
/// ### What this records
///
/// `RPScreenRecorder.shared()` captures the screen pixels at native
/// resolution into an MP4. It is NOT an ARSession dataset (no IMU, no
/// depth, no per-frame anchors) — the resulting clip is replayable in
/// the Photos app but cannot be fed back into `ARSession` for
/// deterministic replay. See `docs/docs/cheatsheet-ios.md` parity table
/// (#1036) for the rationale.
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

    /// `true` while ReplayKit reports it is actively recording. Distinct
    /// from `state == .recording` because `state` follows the call
    /// site's intent while this property mirrors the underlying recorder.
    public var isRecording: Bool {
        recorder.isRecording
    }

    /// `true` if ReplayKit is available on this device (always true on
    /// iOS hardware, `false` in the simulator until iOS 17.4+ when
    /// `RPScreenRecorder` finally became simulator-supported for the
    /// non-microphone code paths).
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
                        continuation.resume()
                        return
                    }
                    if let error {
                        self.state = .error(error.localizedDescription)
                        continuation.resume(throwing: error)
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
                            continuation.resume(throwing: error)
                        } else {
                            continuation.resume(returning: destination)
                        }
                        return
                    }
                    if let error {
                        self.state = .error(error.localizedDescription)
                        continuation.resume(throwing: error)
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
    /// passed to ``stopRecording(outputURL:)``. Lives under
    /// `NSTemporaryDirectory()` so the OS reclaims the file
    /// automatically — the caller is expected to move it to a
    /// persistent location (e.g. via `PHPhotoLibrary`) if they want to
    /// keep it.
    public static func defaultOutputURL() -> URL {
        let filename = "ARRecording-\(UUID().uuidString).mov"
        return URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent(filename)
    }
}

/// Errors surfaced by ``ARRecorder``.
public enum ARRecorderError: LocalizedError, Equatable {
    case unavailable(String)
    case alreadyRecording(String)
    case notRecording(String)

    public var errorDescription: String? {
        switch self {
        case .unavailable(let msg), .alreadyRecording(let msg), .notRecording(let msg):
            return msg
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
