#if os(iOS)
import SwiftUI
import RealityKit
import ARKit
import SceneViewSwift

/// Personal-solar-system AR demo — bundled USDZ models orbit around the user.
///
/// On session start, anchors a world-space `AnchorNode` at the camera's initial
/// position. Loads 8 bundled USDZ assets at equirepartie angles (45° apart) on a
/// ~1.5 m radius circle around the origin, each at a different height (between
/// -0.5 m and +0.5 m relative to the user). A `Timer` ticks at 60 Hz and updates
/// per-model orbital angles (planetary speeds, between 0.05 and 0.30 rad/s) and
/// local spin (between 0.5 and 2.0 rad/s).
///
/// The user stays fixed in AR — turning the phone reveals each model as it
/// passes through view.
struct OrbitalARDemo: View {
    /// Bundled USDZ assets + per-model orbital/spin tuning.
    ///
    /// Speeds are deliberately varied so the formation looks like a real solar
    /// system rather than a single rigid ring — slow at 0.05 rad/s (~125 s per
    /// lap), fast at 0.30 rad/s (~21 s per lap). Heights step ±0.5 m so models
    /// pass at different elevations as the user turns.
    ///
    /// `scale` is a hand-tuned absolute scale (NOT `scaleToUnits` — the goal is
    /// for each model to feel realistic-sized at 1.5 m, so e.g. a Ferrari at
    /// 0.15 m total length reads as a toy car on your kitchen table).
    private struct Planet {
        let asset: String
        let scale: Float
        let initialAngle: Float
        let orbitSpeed: Float       // rad/s around the user
        let spinSpeed: Float        // rad/s local Y spin
        let height: Float           // y offset relative to user
    }

    private static let planets: [Planet] = [
        Planet(asset: "red_car",          scale: 0.18, initialAngle: 0,                orbitSpeed: 0.08, spinSpeed: 0.7,  height:  0.0),
        Planet(asset: "ferrari_f40",      scale: 0.15, initialAngle: .pi * 2 / 8 * 1,  orbitSpeed: 0.12, spinSpeed: 0.5,  height:  0.3),
        Planet(asset: "game_boy_classic", scale: 0.12, initialAngle: .pi * 2 / 8 * 2,  orbitSpeed: 0.20, spinSpeed: 1.6,  height: -0.2),
        Planet(asset: "retro_piano",      scale: 0.20, initialAngle: .pi * 2 / 8 * 3,  orbitSpeed: 0.06, spinSpeed: 0.9,  height:  0.4),
        Planet(asset: "animated_dragon",  scale: 0.15, initialAngle: .pi * 2 / 8 * 4,  orbitSpeed: 0.15, spinSpeed: 1.2,  height: -0.4),
        Planet(asset: "nintendo_switch",  scale: 0.18, initialAngle: .pi * 2 / 8 * 5,  orbitSpeed: 0.10, spinSpeed: 2.0,  height:  0.2),
        Planet(asset: "tree_scene",       scale: 0.30, initialAngle: .pi * 2 / 8 * 6,  orbitSpeed: 0.05, spinSpeed: 0.6,  height: -0.5),
        Planet(asset: "phoenix_bird",     scale: 0.20, initialAngle: .pi * 2 / 8 * 7,  orbitSpeed: 0.30, spinSpeed: 1.8,  height:  0.5),
    ]

    private static let orbitRadius: Float = 1.5

    @State private var loadedNodes: [Int: ModelNode] = [:]
    @State private var anchorAdded = false
    @State private var orbitTimer: Timer?

    var body: some View {
        ZStack {
            #if !targetEnvironment(simulator)
            arSceneView
                .ignoresSafeArea()
            #else
            simulatorPlaceholder
            #endif

            VStack {
                statusPill
                Spacer()
            }
        }
        .navigationTitle("Orbital AR")
        .navigationBarTitleDisplayMode(.inline)
        .onDisappear {
            orbitTimer?.invalidate()
            orbitTimer = nil
        }
    }

    // MARK: - AR Scene

    #if !targetEnvironment(simulator)
    private var arSceneView: some View {
        ARSceneView(
            planeDetection: .none,
            showPlaneOverlay: false,
            showCoachingOverlay: false
        )
        .onSessionStarted { arView in
            // Anchor at world origin — ARKit world origin is the camera's pose
            // when tracking begins, so this places the formation exactly around
            // where the user is standing. The user can turn around to see each
            // planet pass through view.
            guard !anchorAdded else { return }
            anchorAdded = true

            let anchor = AnchorNode.world(position: .init(0, 0, 0))
            arView.scene.addAnchor(anchor.entity)

            // Kick off model loads — order them so the closest-to-front planet
            // (index 0, angle 0) lands first for the most visible drop-in.
            for (index, planet) in Self.planets.enumerated() {
                Task { @MainActor in
                    do {
                        let node = try await ModelNode.load(planet.asset)
                        _ = node.scaleToUnits(planet.scale)
                        node.entity.position = position(for: planet, time: 0)
                        // Auto-play any baked animation (dragon, phoenix, etc.)
                        if node.animationCount > 0 {
                            node.playAllAnimations()
                        }
                        anchor.add(node.entity)
                        loadedNodes[index] = node
                    } catch {
                        print("[OrbitalAR] Failed to load \(planet.asset): \(error)")
                    }
                }
            }

            startTicker()
        }
    }
    #endif

    // MARK: - Animation tick

    private func startTicker() {
        // Invalidate any prior timer (e.g. session re-start) to avoid stacking ticks.
        orbitTimer?.invalidate()
        let start = Date()
        let timer = Timer.scheduledTimer(withTimeInterval: 1.0 / 60.0, repeats: true) { _ in
            let now = Date().timeIntervalSince(start)
            for (index, planet) in Self.planets.enumerated() {
                guard let node = loadedNodes[index] else { continue }
                node.entity.position = position(for: planet, time: Float(now))
                // Local spin on Y — accumulates each tick so it loops naturally.
                let spinAngle = planet.spinSpeed * Float(now)
                node.entity.orientation = simd_quatf(angle: spinAngle, axis: .init(0, 1, 0))
            }
        }
        RunLoop.main.add(timer, forMode: .common)
        orbitTimer = timer
    }

    private func position(for planet: Planet, time: Float) -> SIMD3<Float> {
        let angle = planet.initialAngle + planet.orbitSpeed * time
        return SIMD3<Float>(
            x: cos(angle) * Self.orbitRadius,
            y: planet.height,
            z: sin(angle) * Self.orbitRadius
        )
    }

    // MARK: - Simulator placeholder

    private var simulatorPlaceholder: some View {
        VStack(spacing: 16) {
            Image(systemName: "circle.dotted")
                .font(.system(size: 60))
                .foregroundStyle(.secondary)
                .accessibilityHidden(true)
            Text("AR requires a physical device")
                .font(.headline)
            Text("Run on iPhone or iPad to see 8 models orbit around you.")
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemGroupedBackground))
    }

    // MARK: - Status pill

    private var statusPill: some View {
        Text("Turn around — \(loadedNodes.count) of \(Self.planets.count) models orbiting")
            .font(.caption)
            .fontWeight(.medium)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(.ultraThinMaterial)
            .clipShape(Capsule())
            .padding(.top, 8)
    }
}
#endif
