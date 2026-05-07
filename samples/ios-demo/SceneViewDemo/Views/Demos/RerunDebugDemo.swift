#if os(iOS)
import SwiftUI
import SceneViewSwift
import ARKit

/// Records an ARKit session (camera pose, plane anchors, raw feature
/// points) and lets the user save it as a `.rrd` they can drop onto
/// https://sceneview.github.io/rerun/ to scrub frame-by-frame in any
/// browser.
///
/// Under the hood the bridge streams JSON-lines over TCP to a Python
/// sidecar (`samples/android-demo/tools/rerun-bridge.py --save`) on the
/// developer's Mac. The sidecar collects events and writes the `.rrd` on
/// `Save & Share`.
///
/// ## Setup (one-time on your Mac)
///
/// 1. `pip install rerun-sdk numpy`
/// 2. Run the Python sidecar in save mode:
///    `python samples/android-demo/tools/rerun-bridge.py --save`
/// 3. The default host below is `127.0.0.1` — works on the simulator
///    and on real devices when the port is tunneled over USB
///    (`iproxy 9876 9876`). For Wi-Fi, change `defaultHost` to your
///    Mac's LAN IP (System Settings → Wi-Fi → Details).
///
/// **Dev-only tool** — production builds should gate it with `#if DEBUG`.
/// Shipping the bridge in release builds is not harmful
/// (`setEnabled(false)` short-circuits the hot path), but the socket
/// attempt alone wastes CPU.
struct RerunDebugDemo: View {
    // Tunneled over USB with `iproxy 9876 9876` by default; swap to a
    // Mac LAN IP for Wi-Fi.
    private static let defaultHost = "127.0.0.1"

    @StateObject private var bridge = RerunBridge(
        host: RerunDebugDemo.defaultHost,
        port: RerunBridge.defaultPort,
        rateHz: 10
    )

    @State private var sharing: Bool = false
    @State private var shareResult: RerunBridge.ShareResult? = nil
    @State private var shareSheetItem: ShareSheetItem? = nil

    var body: some View {
        ZStack {
            #if !targetEnvironment(simulator)
            ARSceneView(
                planeDetection: .both,
                showCoachingOverlay: true
            )
            .onFrame { frame, _ in
                bridge.logFrame(frame)
            }
            .ignoresSafeArea()
            #else
            simulatorPlaceholder
            #endif

            VStack {
                statusPill
                Spacer()
                shareButton
            }
            .padding()
        }
        .onAppear { bridge.connect() }
        .onDisappear { bridge.disconnect() }
        .alert(
            shareResult?.success == true ? "Recording saved" : "Couldn't save",
            isPresented: Binding(
                get: { shareResult != nil },
                set: { if !$0 { shareResult = nil } }
            ),
            presenting: shareResult
        ) { result in
            if result.success, let url = result.viewerUrl {
                Button("Share link") {
                    shareSheetItem = ShareSheetItem(text: url)
                }
                Button("Copy viewer URL") {
                    UIPasteboard.general.string = url
                }
                if let path = result.path {
                    Button("Copy path") {
                        UIPasteboard.general.string = path
                    }
                }
                Button("Done", role: .cancel) {}
            } else {
                Button("Close", role: .cancel) {}
            }
        } message: { result in
            if result.success {
                Text("\(result.events) events recorded. Drop the .rrd onto https://sceneview.github.io/rerun/ to render it in-place, or re-host on a public URL (R2, GitHub release, gist) and open the share link in any browser.")
            } else {
                Text(result.reason ?? "The sidecar didn't acknowledge the save command.")
            }
        }
        .sheet(item: $shareSheetItem) { item in
            ActivityView(activityItems: [item.text])
        }
    }

    private var shareButton: some View {
        Button {
            guard !sharing else { return }
            sharing = true
            bridge.requestSaveAndShare { result in
                DispatchQueue.main.async {
                    sharing = false
                    shareResult = result
                }
            }
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "square.and.arrow.up")
                Text(sharing ? "Saving on dev machine…" : "Save & Share recording")
                    .fontWeight(.semibold)
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity)
            .background(Color.purple.opacity(0.85))
            .clipShape(Capsule())
        }
        .disabled(sharing)
        .padding(.top, 8)
    }

    private var statusPill: some View {
        HStack(spacing: 8) {
            Image(systemName: bridge.isConnected
                ? "antenna.radiowaves.left.and.right"
                : "antenna.radiowaves.left.and.right.slash"
            )
            .foregroundStyle(bridge.isConnected ? .purple : .red)
            Text(bridge.isConnected
                 ? "Recording · \(bridge.eventCount) events"
                 : "Sidecar offline — start rerun-bridge.py"
            )
            .font(.caption)
            .fontWeight(.medium)
            .foregroundStyle(.white)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(.black.opacity(0.65))
        .clipShape(Capsule())
    }

    /// Wraps a String so SwiftUI's `.sheet(item:)` accepts it (the closure
    /// requires `Identifiable`).
    private struct ShareSheetItem: Identifiable {
        let id = UUID()
        let text: String
    }

    /// Tiny `UIActivityViewController` bridge so we can present the system
    /// share sheet from SwiftUI without depending on iOS 16's `ShareLink`.
    private struct ActivityView: UIViewControllerRepresentable {
        let activityItems: [Any]
        func makeUIViewController(context: Context) -> UIActivityViewController {
            UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
        }
        func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
    }

    private var simulatorPlaceholder: some View {
        VStack(spacing: 16) {
            Image(systemName: "iphone.gen3")
                .font(.system(size: 60))
                .foregroundStyle(.secondary)
            Text("ARKit is not available in the simulator.")
                .font(.headline)
            Text("Run this demo on a physical iPhone or iPad with a camera.")
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(32)
    }
}
#endif
