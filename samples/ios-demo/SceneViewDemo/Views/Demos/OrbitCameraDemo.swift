import SwiftUI
import RealityKit
import SceneViewSwift

/// Interactive camera controls demo — mirrors Android `CameraControlsDemo.kt`.
///
/// Users can switch between ``CameraControlMode/orbit``, ``CameraControlMode/pan``,
/// and ``CameraControlMode/firstPerson`` to feel the difference in gesture
/// handling. Closes #1034 (last #928 silent-stub item).
struct CameraControlsDemo: View {
    @State private var mode: CameraControlMode = .orbit

    var body: some View {
        ZStack {
            SceneView { root in
                // Central object — orange PBR cube
                let cube = GeometryNode.cube(
                    size: 0.35,
                    material: .pbr(color: .systemOrange, metallic: 0.7, roughness: 0.2),
                    cornerRadius: 0.025
                )
                cube.entity.position = .init(x: 0, y: 0, z: -2)
                root.addChild(cube.entity)

                // Reference grid so .pan and .firstPerson translations are visible.
                let grid = PathNode.grid(
                    size: 2.0,
                    divisions: 10,
                    thickness: 0.002,
                    color: .init(white: 0.2, alpha: 1)
                ).position(.init(x: 0, y: -0.3, z: -2))
                root.addChild(grid.entity)

                // Axis gizmo to anchor user orientation when panning/looking around.
                let gizmo = LineNode.axisGizmo(
                    at: .init(x: -0.8, y: -0.29, z: -1.2),
                    length: 0.2,
                    thickness: 0.004
                )
                for line in gizmo {
                    root.addChild(line.entity)
                }

                let xLabel = TextNode(text: "X", fontSize: 0.04, color: .red, depth: 0.005)
                    .position(.init(x: -0.55, y: -0.27, z: -1.2))
                root.addChild(xLabel.entity)
                let yLabel = TextNode(text: "Y", fontSize: 0.04, color: .green, depth: 0.005)
                    .position(.init(x: -0.8, y: -0.05, z: -1.2))
                root.addChild(yLabel.entity)
                let zLabel = TextNode(text: "Z", fontSize: 0.04, color: .systemBlue, depth: 0.005)
                    .position(.init(x: -0.8, y: -0.27, z: -0.95))
                root.addChild(zLabel.entity)
            }
            .cameraControls(mode)
            .ignoresSafeArea()

            VStack {
                Spacer()

                // Mode picker — segmented so users feel the live mode switch.
                Picker("Camera mode", selection: $mode) {
                    Text("Orbit").tag(CameraControlMode.orbit)
                    Text("Pan").tag(CameraControlMode.pan)
                    Text("Look").tag(CameraControlMode.firstPerson)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 24)
                .padding(.bottom, 6)

                VStack(spacing: 6) {
                    instructionRow(icon: gestureIconForMode, text: dragHintForMode)
                    instructionRow(icon: "hand.pinch.fill", text: pinchHintForMode)
                }
                .padding()
                .background(.ultraThinMaterial)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .padding(.bottom, 30)
                .padding(.horizontal, 40)
            }
        }
        .background(Color.black)
    }

    // MARK: - Per-mode hints

    private var gestureIconForMode: String {
        switch mode {
        case .orbit:       return "arrow.triangle.2.circlepath"
        case .pan:         return "hand.draw.fill"
        case .firstPerson: return "eye.fill"
        }
    }

    private var dragHintForMode: String {
        switch mode {
        case .orbit:       return "Drag — orbit around object"
        case .pan:         return "Drag — translate scene"
        case .firstPerson: return "Drag — look around"
        }
    }

    private var pinchHintForMode: String {
        switch mode {
        case .orbit, .pan: return "Pinch — zoom in / out"
        case .firstPerson: return "Pinch — zoom field of view"
        }
    }

    private func instructionRow(icon: String, text: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.body)
                .foregroundStyle(.white.opacity(0.8))
                .frame(width: 24)
                .accessibilityHidden(true)
            Text(text)
                .font(.caption)
                .foregroundStyle(.white)
        }
    }
}
