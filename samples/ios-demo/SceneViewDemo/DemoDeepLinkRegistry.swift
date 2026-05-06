import SwiftUI

/// Maps a stable demo id (`ar-rerun`, `model-viewer`, …) to the
/// corresponding SwiftUI destination, for the deep-link path.
///
/// Why this exists separately from `SamplesTab.allScenes()` and
/// friends: the iOS demo's catalogue uses human titles (`"AR Debug
/// (Rerun)"`) while the deep-link contract requires a stable, slug-style
/// id matched **byte-for-byte** with the Android `DemoRegistry.kt` —
/// otherwise the same `sceneview://demo/<id>` URL would route to
/// different things on Android and iOS, defeating the cross-platform
/// guarantee of the QR codes generated on the website.
///
/// **Coverage policy** — we only need to map the ids that QR codes
/// actually point at today. The set grows as we add QR codes on
/// website / README / docs. Unknown ids fall through to a fallback
/// "Open in app" placeholder (the parent `DeepLinkRouter` already
/// validates ids against `allowedIds` so the placeholder is only ever
/// reached for ids registered here without a destination).
enum DemoDeepLinkRegistry {

    /// Subset of `DemoRegistry.kt` ids that should be reachable via
    /// `sceneview://demo/<id>` on iOS. Add ids here as new QR codes are
    /// published; new ids should always be a *subset* of the canonical
    /// list (see `DeepLinkRouter` Kotlin).
    static let allowedIds: Set<String> = [
        // 3D Basics
        "model-viewer", "geometry", "animation", "multi-model",
        // Lighting
        "lighting", "fog", "environment", "dynamic-sky",
        // Content
        "text", "lines-paths", "image", "billboard", "video",
        // Interaction
        "camera-controls", "gesture-editing",
        // Advanced
        "physics", "custom-mesh", "shape",
        // AR
        "ar-placement", "ar-image", "ar-face", "ar-rerun",
    ]

    /// Resolve a demo id to its presented `View`. Returns a fallback
    /// "Coming soon" view for ids that pass `allowedIds` but don't yet
    /// have an iOS destination wired up — this keeps the QR / deep-link
    /// path working as soon as a new id is published, even if the iOS
    /// catalogue lags behind.
    @ViewBuilder
    static func destination(for id: String) -> some View {
        switch id {
        // The most important deep-link target today: the AR Rerun debug
        // demo, paired with the hosted Save & Share viewer at
        // sceneview.github.io/rerun/.
        case "ar-rerun":
            #if os(iOS)
            RerunDebugDemo()
            #else
            DeepLinkPlaceholder(id: id, reason: "AR Rerun is iOS-only on this build.")
            #endif

        // 3D basics + content — covered by the Scenes tab gallery already.
        case "geometry":      AllShapesDemo()
        case "custom-mesh":   CustomMeshDemo()
        case "shape":         AllShapesDemo()
        case "text":          TextDemo()
        case "billboard":     BillboardDemo()
        case "lines-paths":   LinesPathsDemo()
        case "image":         ImagePlaneDemo()

        // Lighting + effects.
        case "lighting":      LightTypesDemo()
        case "dynamic-sky":   DynamicSkyDemo()
        case "fog":           FogDemo()
        case "physics":       PhysicsDemo()

        // Interaction.
        case "camera-controls": OrbitCameraDemo()

        default:
            DeepLinkPlaceholder(id: id, reason: "This demo isn't available in the iOS app yet — open it on Android, or browse the Scenes tab for the full iOS catalog.")
        }
    }
}

/// Tiny placeholder shown when a deep-link id is recognised by the
/// router but doesn't yet have a destination wired in
/// `DemoDeepLinkRegistry`. Communicates the gap clearly and offers a
/// way out (close + browse the Scenes tab).
private struct DeepLinkPlaceholder: View {
    let id: String
    let reason: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "qrcode.viewfinder")
                .font(.system(size: 48))
                .foregroundStyle(.secondary)
            Text("Demo: \(id)")
                .font(.headline)
            Text(reason)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Spacer()
            Button("Close") { dismiss() }
                .buttonStyle(.bordered)
                .padding(.bottom, 24)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        #if os(iOS)
        .background(Color(UIColor.systemBackground))
        #endif
    }
}
