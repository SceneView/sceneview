import SwiftUI
import RealityKit
import SceneViewSwift

/// ImageNode demo -- colored planes arranged in a gallery layout.
/// Named `ImageDemo` to mirror the Android demo of the same name.
struct ImageDemo: View {
    var body: some View {
        ZStack {
            SceneView { root in
                // Create a gallery of colored image planes
                let colors: [(UIColor, String)] = [
                    (.systemRed, "Red"),
                    (.systemOrange, "Orange"),
                    (.systemYellow, "Yellow"),
                    (.systemGreen, "Green"),
                    (.systemBlue, "Blue"),
                    (.systemPurple, "Purple"),
                ]

                // Arrange in an arc
                let arcRadius: Float = 1.5
                for (i, (color, name)) in colors.enumerated() {
                    let angle = Float(i - colors.count / 2) * 0.35
                    let x = sin(angle) * arcRadius
                    let z = -cos(angle) * arcRadius - 0.5

                    let imageNode = ImageNode.color(color, width: 0.3, height: 0.3)
                        .position(.init(x: x, y: 0.1, z: z))
                        .rotation(angle: angle, axis: .init(x: 0, y: 1, z: 0))
                    root.addChild(imageNode.entity)

                    // Billboard label beneath
                    let label = BillboardNode.text(name, fontSize: 0.03, color: color)
                        .position(.init(x: x, y: -0.15, z: z))
                    root.addChild(label.entity)
                }

                // Large background plane
                let backdrop = ImageNode.color(
                    UIColor(white: 0.08, alpha: 1.0),
                    width: 4.0,
                    height: 2.0
                )
                .position(.init(x: 0, y: 0, z: -3))
                root.addChild(backdrop.entity)
            }
            .cameraControls(.orbit)
            .ignoresSafeArea()

            VStack {
                Spacer()
                Text("ImageNode.color -- solid-color planes in an arc")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.6))
                    .padding(.bottom, 12)
            }
        }
        .background(Color.black)
    }
}
