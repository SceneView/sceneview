import SwiftUI
import RealityKit
import SceneViewSwift

/// Animation showcase -- metallic torus-like arrangement that auto-rotates continuously.
/// Named `AnimationDemo` to mirror the Android demo of the same name (auto-rotation
/// is the V1 form of "animation" on iOS until skinning/rig playback lands).
struct AnimationDemo: View {
    var body: some View {
        ZStack {
            SceneView { root in
                // Ring of metallic spheres forming a torus shape
                let ringCount = 16
                let majorRadius: Float = 0.5
                let minorRadius: Float = 0.06

                for i in 0..<ringCount {
                    let angle = Float(i) / Float(ringCount) * 2 * .pi
                    let hue = CGFloat(i) / CGFloat(ringCount)
                    let color = UIColor(hue: hue, saturation: 0.8, brightness: 1.0, alpha: 1.0)

                    let sphere = GeometryNode.sphere(
                        radius: minorRadius,
                        material: .pbr(color: color, metallic: 0.9, roughness: 0.1)
                    )
                    sphere.entity.position = .init(
                        x: cos(angle) * majorRadius,
                        y: 0,
                        z: sin(angle) * majorRadius - 2.0
                    )
                    root.addChild(sphere.entity)
                }

                // Central metallic sphere
                let center = GeometryNode.sphere(
                    radius: 0.15,
                    material: .pbr(color: .white, metallic: 1.0, roughness: 0.0)
                )
                center.entity.position = .init(x: 0, y: 0, z: -2)
                root.addChild(center.entity)

                // Vertical ring
                for i in 0..<12 {
                    let angle = Float(i) / 12.0 * 2 * .pi
                    let sphere = GeometryNode.sphere(
                        radius: 0.03,
                        material: .pbr(color: .lightGray, metallic: 0.8, roughness: 0.2)
                    )
                    sphere.entity.position = .init(
                        x: 0,
                        y: sin(angle) * 0.35,
                        z: cos(angle) * 0.35 - 2.0
                    )
                    root.addChild(sphere.entity)
                }
            }
            .cameraControls(.orbit)
            .autoRotate(speed: 0.5)
            .ignoresSafeArea()

            VStack {
                Spacer()
                Text("Auto-rotating at 0.5 rad/s")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.6))
                    .padding(.bottom, 12)
            }
        }
        .background(Color.black)
    }
}
