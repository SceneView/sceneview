import SwiftUI
import RealityKit
import SceneViewSwift

/// Interactive orbit camera with grid reference and instructions.
/// Named `CameraControlsDemo` to mirror the Android demo of the same name.
struct CameraControlsDemo: View {
    var body: some View {
        ZStack {
            SceneView { root in
                // Central object
                let cube = GeometryNode.cube(
                    size: 0.35,
                    material: .pbr(color: .systemOrange, metallic: 0.7, roughness: 0.2),
                    cornerRadius: 0.025
                )
                cube.entity.position = .init(x: 0, y: 0, z: -2)
                root.addChild(cube.entity)

                // Reference grid
                let grid = PathNode.grid(
                    size: 2.0,
                    divisions: 10,
                    thickness: 0.002,
                    color: .init(white: 0.2, alpha: 1)
                ).position(.init(x: 0, y: -0.3, z: -2))
                root.addChild(grid.entity)

                // Axis gizmo
                let gizmo = LineNode.axisGizmo(
                    at: .init(x: -0.8, y: -0.29, z: -1.2),
                    length: 0.2,
                    thickness: 0.004
                )
                for line in gizmo {
                    root.addChild(line.entity)
                }

                // Axis labels
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
            .cameraControls(.orbit)
            .ignoresSafeArea()

            VStack {
                Spacer()
                VStack(spacing: 6) {
                    instructionRow(icon: "hand.draw.fill", text: "1 finger -- Orbit")
                    instructionRow(icon: "hand.pinch.fill", text: "Pinch -- Zoom")
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
