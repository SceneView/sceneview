// ─── AR logger code generator ─────────────────────────────────────────────────
//
// Emits a ready-to-paste helper (Kotlin or Swift) that logs SceneView AR
// session data to Rerun using the JSON-lines wire format.
export const LOGGER_LANGUAGES = ["kotlin", "swift"];
export const LOGGER_DATA_TYPES = [
    "pose",
    "planes",
    "point_cloud",
    "anchors",
    "hit_results",
];
/**
 * Returns a Kotlin or Swift snippet the developer can drop into their project
 * to stream ARCore/ARKit frames to Rerun via the JSON-lines wire format.
 */
export function generateArLogger(options) {
    const { language, dataTypes, rateHz = 10, host = "127.0.0.1", port = 9876, } = options;
    if (dataTypes.length === 0) {
        throw new Error("At least one dataType is required");
    }
    for (const dt of dataTypes) {
        if (!LOGGER_DATA_TYPES.includes(dt)) {
            throw new Error(`Invalid dataType "${dt}". Valid: ${LOGGER_DATA_TYPES.join(", ")}`);
        }
    }
    return language === "kotlin"
        ? generateKotlinLogger(dataTypes, rateHz, host, port)
        : generateSwiftLogger(dataTypes, rateHz, host, port);
}
// ─── Kotlin (Android / ARCore) ────────────────────────────────────────────────
function generateKotlinLogger(dataTypes, rateHz, host, port) {
    const logBlocks = [];
    if (dataTypes.includes("pose")) {
        logBlocks.push(`            // Camera pose
            val camPose = frame.camera.pose
            bridge.logCameraPose(camPose, nowNanos)`);
    }
    if (dataTypes.includes("planes")) {
        logBlocks.push(`            // Detected planes
            bridge.logPlanes(frame.getUpdatedPlanes(), nowNanos)`);
    }
    if (dataTypes.includes("point_cloud")) {
        logBlocks.push(`            // Point cloud — IMPORTANT: call release() on the acquired cloud
            frame.acquirePointCloud().use { cloud ->
                bridge.logPointCloud(cloud, nowNanos)
            }`);
    }
    if (dataTypes.includes("anchors")) {
        logBlocks.push(`            // Session anchors
            bridge.logAnchors(session.allAnchors, nowNanos)`);
    }
    if (dataTypes.includes("hit_results")) {
        logBlocks.push(`            // Last hit result (update from your gesture handler)
            lastHit?.let { bridge.logHitResult(it, nowNanos) }`);
    }
    return `package com.example.rerun

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.rerun.rememberRerunBridge

/**
 * AR scene that logs ${dataTypes.join(", ")} to Rerun at ${rateHz} Hz.
 *
 * Wire format: JSON lines over TCP, one object per event. Drops frames on
 * backpressure so the main thread is never blocked.
 */
@Composable
fun ARWithRerunLogger() {
    val bridge = rememberRerunBridge(
        host = "${host}",
        port = ${port},
        rateHz = ${rateHz},
        enabled = true,
    )

    ARSceneView(
        modifier = Modifier.fillMaxSize(),
        onSessionUpdated = { session, frame ->
            val nowNanos = frame.timestamp
${logBlocks.join("\n")}
        },
    )
}
`;
}
// ─── Swift (iOS / ARKit) ──────────────────────────────────────────────────────
function generateSwiftLogger(dataTypes, rateHz, host, port) {
    const logBlocks = [];
    if (dataTypes.includes("pose")) {
        logBlocks.push(`            // Camera pose
            bridge.logCameraPose(frame.camera.transform, timestamp: nowNanos)`);
    }
    if (dataTypes.includes("planes")) {
        logBlocks.push(`            // Detected plane anchors
            let planes = frame.anchors.compactMap { $0 as? ARPlaneAnchor }
            bridge.logPlanes(planes, timestamp: nowNanos)`);
    }
    if (dataTypes.includes("point_cloud")) {
        logBlocks.push(`            // Raw feature points
            if let raw = frame.rawFeaturePoints {
                bridge.logPointCloud(raw, timestamp: nowNanos)
            }`);
    }
    if (dataTypes.includes("anchors")) {
        logBlocks.push(`            // All non-plane anchors
            let anchors = frame.anchors.filter { !($0 is ARPlaneAnchor) }
            bridge.logAnchors(anchors, timestamp: nowNanos)`);
    }
    if (dataTypes.includes("hit_results")) {
        logBlocks.push(`            // Last hit result (update from your tap handler)
            if let hit = self.lastHit {
                bridge.logHitResult(hit, timestamp: nowNanos)
            }`);
    }
    return `import SwiftUI
import SceneViewSwift
import ARKit

/// AR view that logs ${dataTypes.join(", ")} to Rerun at ${rateHz} Hz.
///
/// Wire format: JSON lines over TCP, one object per event. Drops frames on
/// backpressure so the main thread is never blocked.
struct ARWithRerunLoggerView: View {
    @StateObject private var bridge = RerunBridge(
        host: "${host}",
        port: ${port},
        rateHz: ${rateHz}
    )
    @State private var lastHit: ARRaycastResult? = nil

    var body: some View {
        ARSceneView(
            onFrame: { frame in
                let nowNanos = Int64(frame.timestamp * 1_000_000_000)
${logBlocks.join("\n")}
            }
        )
        .ignoresSafeArea()
        .onAppear { bridge.connect() }
        .onDisappear { bridge.disconnect() }
    }
}
`;
}
