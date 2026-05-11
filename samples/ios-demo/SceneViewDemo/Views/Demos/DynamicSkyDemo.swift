import SwiftUI
import RealityKit
import SceneViewSwift

/// Dynamic sky -- slider to control time of day from midnight to midnight.
struct DynamicSkyDemo: View {
    @State private var timeOfDay: Float = 12

    private var timeLabel: String {
        let hour = Int(timeOfDay) % 24
        let minute = Int((timeOfDay - Float(Int(timeOfDay))) * 60)
        return String(format: "%02d:%02d", hour, minute)
    }

    private var periodLabel: String {
        if timeOfDay < 5 || timeOfDay > 20 { return "Night" }
        if timeOfDay < 7 { return "Dawn" }
        if timeOfDay < 10 { return "Morning" }
        if timeOfDay < 14 { return "Noon" }
        if timeOfDay < 17 { return "Afternoon" }
        if timeOfDay < 19 { return "Sunset" }
        return "Dusk"
    }

    /// Pick an HDR environment that visually matches the current time of day so the
    /// background isn't a flat black box when the sun is up. Mirrors the Android fix
    /// in commit `15bcaf8c`. Three buckets is coarse — the tint jumps rather than
    /// fading smoothly — but it covers the obvious user expectations.
    private var skyEnvironment: SceneEnvironment {
        switch timeOfDay {
        case ..<6, 19...: return .night
        case ..<9, 17...: return .sunset
        default: return .outdoor
        }
    }

    var body: some View {
        ZStack {
            SceneView { root in
                // Ground plane
                let ground = GeometryNode.plane(width: 5, depth: 5, color: .init(white: 0.15, alpha: 1))
                ground.entity.position = .init(x: 0, y: -0.3, z: -2)
                root.addChild(ground.entity)

                // Buildings (cubes of varying heights)
                let buildings: [(Float, Float, Float, UIColor)] = [
                    (-0.6, 0.4, -2.5, .systemGray),
                    (-0.2, 0.6, -2.8, .systemGray2),
                    (0.2, 0.3, -2.3, .systemGray3),
                    (0.5, 0.5, -2.6, .systemGray),
                    (0.8, 0.35, -2.4, .systemGray2),
                ]
                for (x, h, z, color) in buildings {
                    let building = GeometryNode.cube(size: 0.2, color: color, cornerRadius: 0.01)
                    building.entity.scale = .init(x: 1, y: h / 0.2, z: 1)
                    building.entity.position = .init(x: x, y: h / 2 - 0.3, z: z)
                    root.addChild(building.entity)
                }

                // Dynamic sky light
                let sky = DynamicSkyNode(timeOfDay: timeOfDay, turbidity: 3, sunIntensity: 1500)
                root.addChild(sky.entity)
            }
            .environment(skyEnvironment)
            .cameraControls(.orbit)
            .id("sky-\(Int(timeOfDay * 10))-\(skyEnvironment.name)")
            .ignoresSafeArea()

            VStack {
                Spacer()

                VStack(spacing: 8) {
                    HStack {
                        Image(systemName: timeIcon)
                            .foregroundStyle(.yellow)
                        Text(timeLabel)
                            .font(.title2).bold()
                            .foregroundStyle(.white)
                            .monospacedDigit()
                        Text(periodLabel)
                            .font(.caption)
                            .foregroundStyle(.white.opacity(0.6))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(.white.opacity(0.1))
                            .clipShape(Capsule())
                    }

                    Slider(value: $timeOfDay, in: 0...24, step: 0.25)
                        .tint(.orange)
                        .accessibilityLabel("Time of day slider")
                        .accessibilityValue("\(timeLabel), \(periodLabel)")

                    HStack {
                        Text("00:00").font(.caption2).foregroundStyle(.white.opacity(0.4))
                        Spacer()
                        Text("12:00").font(.caption2).foregroundStyle(.white.opacity(0.4))
                        Spacer()
                        Text("24:00").font(.caption2).foregroundStyle(.white.opacity(0.4))
                    }
                }
                .padding()
                .background(.ultraThinMaterial)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .padding()
            }
        }
        .background(Color.black)
    }

    private var timeIcon: String {
        if timeOfDay < 5 || timeOfDay > 20 { return "moon.stars.fill" }
        if timeOfDay < 7 || timeOfDay > 18 { return "sun.horizon.fill" }
        return "sun.max.fill"
    }
}
