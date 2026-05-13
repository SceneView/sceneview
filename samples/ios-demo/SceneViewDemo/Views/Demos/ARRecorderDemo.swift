#if os(iOS)
import SwiftUI
import RealityKit
import ARKit
import SceneViewSwift

/// AR session recording demo (v4.3.0+) — mirrors Android `ARRecordPlaybackDemo.kt`.
///
/// Records the screen via ReplayKit (`RPScreenRecorder`) while an AR
/// session is live. iOS is record-only because ARKit, unlike ARCore,
/// does not expose a deterministic playback dataset — the resulting
/// MP4 plays back in the Photos app but cannot be fed back into
/// `ARSession` for replay. See `docs/docs/cheatsheet-ios.md` parity
/// table (#1036) for the rationale.
struct ARRecorderDemo: View {
    @StateObject private var recorder = ARRecorder()
    @State private var statusMessage: String? = nil
    @State private var lastFileSize: Int? = nil
    /// Holds the currently-running record/stop Task so the view can
    /// cancel it on disappear and avoid mutating `statusMessage` on a
    /// disposed view. Closes Agent A MAJOR finding on PR #1042.
    @State private var activeTask: Task<Void, Never>? = nil

    var body: some View {
        ZStack {
            #if !targetEnvironment(simulator)
            arSceneView
                .ignoresSafeArea()
            #else
            simulatorPlaceholder
            #endif

            VStack {
                Spacer()
                statusBanner
                controlsPanel
                    .padding(.bottom, 30)
                    .padding(.horizontal, 24)
            }
        }
        .background(Color.black)
        .onDisappear {
            // Cancel any in-flight Task on disappear so awaited
            // continuations don't try to mutate `statusMessage` /
            // `lastFileSize` on a destroyed view. The recorder itself
            // continues; `ARRecorder.stopRecording()` must be called
            // explicitly to stop capture (closes Agent A MAJOR).
            activeTask?.cancel()
        }
    }

    // MARK: - AR view

    #if !targetEnvironment(simulator)
    private var arSceneView: some View {
        ARSceneView(
            planeDetection: .horizontal,
            showPlaneOverlay: true,
            showCoachingOverlay: true,
            onTapOnPlane: { position, arView in
                // Drop a small unlit cube where the user taps so the
                // recording has something tracking.
                let marker = GeometryNode.cube(
                    size: 0.08,
                    material: .pbr(color: .systemTeal, metallic: 0.0, roughness: 0.4),
                    cornerRadius: 0.01
                )
                let anchor = AnchorNode.world(position: position)
                anchor.add(marker.entity)
                arView.scene.addAnchor(anchor.entity)
            }
        )
    }
    #endif

    private var simulatorPlaceholder: some View {
        VStack(spacing: 12) {
            Image(systemName: "arkit")
                .font(.system(size: 60))
                .foregroundStyle(.white.opacity(0.5))
            Text("AR recording is device-only")
                .font(.headline)
                .foregroundStyle(.white)
            Text("RPScreenRecorder needs ARKit hardware and screen-record permission. Run on a real iPhone or iPad to try this demo.")
                .font(.caption)
                .multilineTextAlignment(.center)
                .foregroundStyle(.white.opacity(0.7))
                .padding(.horizontal, 40)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - UI

    @ViewBuilder
    private var statusBanner: some View {
        if let statusMessage {
            Text(statusMessage)
                .font(.caption)
                .foregroundStyle(.white)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(.ultraThinMaterial)
                .clipShape(Capsule())
                .padding(.bottom, 10)
                .transition(.opacity)
        }
    }

    private var controlsPanel: some View {
        VStack(spacing: 10) {
            HStack(spacing: 16) {
                Button(action: toggleRecording) {
                    HStack(spacing: 8) {
                        Image(systemName: recorder.isRecording ? "stop.circle.fill" : "record.circle.fill")
                            .font(.title2)
                        Text(recorder.isRecording ? "Stop" : "Record")
                            .font(.body.weight(.semibold))
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, 22)
                    .padding(.vertical, 12)
                    .background(recorder.isRecording ? Color.red : Color.accentColor)
                    .clipShape(Capsule())
                }
                .disabled(!recorder.isAvailable && !recorder.isRecording)
            }

            if let url = recorder.lastOutputURL {
                HStack(spacing: 10) {
                    Text(url.lastPathComponent)
                        .font(.caption2.monospaced())
                        .foregroundStyle(.white.opacity(0.7))
                        .lineLimit(1)
                        .truncationMode(.middle)
                    ShareLink(item: url) {
                        Image(systemName: "square.and.arrow.up")
                            .font(.caption)
                            .foregroundStyle(.white)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(.white.opacity(0.15))
                            .clipShape(Capsule())
                    }
                }
            }

            Text("iOS records the screen only (no deterministic playback). The MP4 opens in Photos.")
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.6))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 16)
        }
        .padding()
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    // MARK: - Actions

    private func toggleRecording() {
        // Cancel any prior in-flight task so we don't end up with two
        // overlapping start/stop hops mutating state on a disposed view.
        activeTask?.cancel()
        activeTask = Task {
            if recorder.isRecording {
                do {
                    let url = try await recorder.stopRecording()
                    if Task.isCancelled { return }
                    let size = (try? FileManager.default.attributesOfItem(atPath: url.path)[.size] as? Int) ?? 0
                    lastFileSize = size
                    statusMessage = "Recording saved (\(humanFileSize(size)))"
                } catch {
                    if Task.isCancelled { return }
                    statusMessage = "Stop failed: \(error.localizedDescription)"
                }
            } else {
                statusMessage = "Recording…"
                do {
                    try await recorder.startRecording()
                } catch {
                    if Task.isCancelled { return }
                    statusMessage = "Start failed: \(error.localizedDescription)"
                }
            }
        }
    }

    private func humanFileSize(_ bytes: Int) -> String {
        ByteCountFormatter.string(fromByteCount: Int64(bytes), countStyle: .file)
    }
}
#endif
