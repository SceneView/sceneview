import SwiftUI
import SceneViewSwift

/// Samples tab — curated preset scenes grouped by category, presented as
/// Liquid Glass cards in a scrollable list.
///
/// Tapping a sample opens it in a `.sheet` with `.regularMaterial` background
/// (medium/large detents) — perfect for non-immersive 3D demos. AR demos that
/// need full camera feed open in a `.fullScreenCover` instead.
///
/// A `3D / AR` filter chip at the top filters the visible categories.
struct SamplesTab: View {
    private let scenes: [DemoItem] = Self.allScenes()

    @State private var selectedScene: DemoItem?
    @State private var fullScreenScene: DemoItem?
    @State private var filter: ScopeFilter = .all

    private enum ScopeFilter: String, CaseIterable, Identifiable {
        case all = "All"
        case threeD = "3D"
        case ar = "AR"

        var id: String { rawValue }
        var icon: String {
            switch self {
            case .all: return "square.grid.2x2.fill"
            case .threeD: return "cube.fill"
            case .ar: return "arkit"
            }
        }
    }

    /// Demos that need a full camera feed or heavy 3D viewport must take the
    /// whole screen — a sheet detent would clip the AR camera and gestures.
    private static let fullScreenIDs: Set<String> = [
        "AR Debug (Rerun)",
        "Orbital AR",
    ]

    private static func shouldOpenFullScreen(_ scene: DemoItem) -> Bool {
        // All real AR demos take the whole screen.
        if scene.category == .ar && scene.status.isAvailable { return true }
        return fullScreenIDs.contains(scene.title)
    }

    private var filteredScenes: [DemoItem] {
        switch filter {
        case .all: return scenes
        case .threeD: return scenes.filter { $0.category != .ar }
        case .ar: return scenes.filter { $0.category == .ar }
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 24) {
                    filterBar

                    let grouped = Dictionary(grouping: filteredScenes) { $0.category }
                    let sortedCategories = grouped.keys.sorted()
                    ForEach(sortedCategories, id: \.self) { category in
                        categorySection(category: category, items: grouped[category]!)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 24)
            }
            .navigationTitle("Samples")
            .sheet(item: $selectedScene) { scene in
                sheetDestination(for: scene)
                    #if os(iOS)
                    .presentationDetents([.medium, .large])
                    .presentationBackground(.regularMaterial)
                    .presentationCornerRadius(28)
                    .presentationDragIndicator(.visible)
                    #endif
            }
            #if os(iOS)
            .fullScreenCover(item: $fullScreenScene) { scene in
                NavigationStack {
                    sheetDestination(for: scene)
                }
            }
            #endif
        }
    }

    // MARK: - Filter bar

    private var filterBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(ScopeFilter.allCases) { option in
                    Button {
                        filter = option
                        #if os(iOS)
                        HapticManager.selectionChanged()
                        #endif
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: option.icon)
                                .font(.caption.weight(.semibold))
                            Text(option.rawValue)
                                .font(.subheadline.weight(.medium))
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(
                            filter == option
                                ? AnyShapeStyle(.tint)
                                : AnyShapeStyle(.regularMaterial),
                            in: Capsule()
                        )
                        .foregroundStyle(filter == option ? Color.white : Color.primary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(option.rawValue) filter")
                    .accessibilityAddTraits(filter == option ? .isSelected : [])
                }
            }
        }
        .scrollClipDisabled()
    }

    // MARK: - Category section

    private func categorySection(category: DemoCategory, items: [DemoItem]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(category.rawValue)
                .font(.title3.weight(.bold))
                .padding(.horizontal, 4)

            VStack(spacing: 8) {
                ForEach(items) { scene in
                    Button {
                        handleTap(scene)
                    } label: {
                        SceneRow(scene: scene)
                    }
                    .buttonStyle(.plain)
                    .disabled(!scene.status.isAvailable && scene.status.comingSoonVersion != nil ? false : !scene.status.isAvailable)
                    .accessibilityLabel(accessibilityLabel(for: scene))
                }
            }
        }
    }

    private func handleTap(_ scene: DemoItem) {
        #if os(iOS)
        HapticManager.lightTap()
        if Self.shouldOpenFullScreen(scene) {
            fullScreenScene = scene
        } else {
            selectedScene = scene
        }
        #else
        selectedScene = scene
        #endif
    }

    @ViewBuilder
    private func sheetDestination(for scene: DemoItem) -> some View {
        switch scene.status {
        case .available:
            scene.destination
                .navigationTitle(scene.title)
                #if os(iOS)
                .navigationBarTitleDisplayMode(.inline)
                #endif
        case let .comingSoon(version):
            ComingSoonScreen(
                title: scene.title,
                subtitle: scene.subtitle,
                icon: scene.icon,
                version: version
            )
        }
    }

    private func accessibilityLabel(for scene: DemoItem) -> String {
        switch scene.status {
        case .available:
            return "\(scene.title): \(scene.subtitle)"
        case let .comingSoon(version):
            return "\(scene.title): \(scene.subtitle). Coming soon in version \(version)."
        }
    }

    // MARK: - Scene catalog

    private static func allScenes() -> [DemoItem] {
        var items: [DemoItem] = [
            // MARK: Geometry

            DemoItem(title: "Geometry", icon: "cube.fill", subtitle: "Cube, sphere, cylinder, cone, plane", category: .geometry) {
                GeometryDemo()
            },
            DemoItem(title: "Materials", icon: "paintpalette.fill", subtitle: "PBR metallic & roughness spectrum", category: .geometry) {
                MaterialsDemo()
            },
            DemoItem(title: "Custom Mesh", icon: "diamond.fill", subtitle: "Vertices, normals & triangle indices", category: .geometry) {
                CustomMeshDemo()
            },
            DemoItem(title: "Animation", icon: "figure.run", subtitle: "Skinning, rig-driven animation playback", category: .geometry) {
                AnimationDemo()
            },
            DemoItem(title: "Model Viewer", icon: "cube.transparent.fill", subtitle: "Full-screen hero with a Surprise streamer", category: .geometry) {
                ModelViewerDemo()
            },
            DemoItem(title: "Multi-Model Park", icon: "tree.fill", subtitle: "Themed scene composed of 4 streamed models", category: .geometry) {
                MultiModelDemo()
            },

            // MARK: Content

            DemoItem(title: "3D Text", icon: "textformat", subtitle: "Extruded text with styles & sizes", category: .content) {
                TextDemo()
            },
            DemoItem(title: "Billboard", icon: "person.fill.viewfinder", subtitle: "Labels that face the camera", category: .content) {
                BillboardDemo()
            },
            DemoItem(title: "Lines & Paths", icon: "point.topleft.down.to.point.bottomright.curvepath", subtitle: "Polylines, helix, grids, circles", category: .content) {
                LinesPathsDemo()
            },
            DemoItem(title: "Image Planes", icon: "photo.fill", subtitle: "Colored image planes in 3D", category: .content) {
                ImageDemo()
            },
            DemoItem(
                comingSoonTitle: "Video Texture",
                icon: "video.fill",
                subtitle: "Play video on a 3D surface",
                category: .content
            ),

            // MARK: Lighting

            DemoItem(title: "Light Types", icon: "lightbulb.fill", subtitle: "Directional, point & spot lights", category: .lighting) {
                LightingDemo()
            },
            DemoItem(title: "Movable Light", icon: "sun.dust.fill", subtitle: "Drag to orbit the light around the model", category: .lighting) {
                MovableLightDemo()
            },
            DemoItem(title: "Dynamic Sky", icon: "sun.horizon.fill", subtitle: "Time-of-day sun simulation", category: .lighting) {
                DynamicSkyDemo()
            },

            // MARK: Effects

            DemoItem(title: "Fog", icon: "cloud.fog.fill", subtitle: "Linear, exponential & height fog", category: .effects) {
                FogDemo()
            },
            DemoItem(title: "Physics", icon: "figure.walk", subtitle: "Dynamic, static & kinematic bodies", category: .effects) {
                PhysicsDemo()
            },
            DemoItem(title: "Double Pendulum", icon: "waveform.path", subtitle: "Chaotic two-link physics, shared KMP simulation", category: .effects) {
                DoublePendulumDemo()
            },

            // MARK: Interaction
            // CameraControlsDemo ships in the .advanced section with the full
            // 3-way mode picker (orbit / pan / firstPerson) since v4.3.0 (#1034).
            // Gesture-editing demos below are still pending iOS port.

            DemoItem(
                comingSoonTitle: "Gesture Editing",
                icon: "hand.pinch.fill",
                subtitle: "Move, scale & rotate models with touches",
                category: .interaction
            ),
            DemoItem(
                comingSoonTitle: "Collision Detection",
                icon: "capsule.fill",
                subtitle: "Hit testing & AABB queries",
                category: .interaction
            ),
            DemoItem(
                comingSoonTitle: "ViewNode",
                icon: "rectangle.stack.fill",
                subtitle: "SwiftUI / Compose overlays in 3D",
                category: .interaction
            ),

            // MARK: Advanced

            DemoItem(title: "Camera Controls", icon: "camera.fill", subtitle: "Interactive orbit with grid reference", category: .advanced) {
                CameraControlsDemo()
            },
            DemoItem(title: "Auto Rotate", icon: "rotate.3d.fill", subtitle: "Continuous rotation animation", category: .advanced) {
                AutoRotateDemo()
            },
            DemoItem(title: "Scene Gallery", icon: "square.grid.3x3.fill", subtitle: "Multiple shapes in one scene", category: .advanced) {
                SceneGalleryDemo()
            },
            DemoItem(
                comingSoonTitle: "Post Processing",
                icon: "sparkles",
                subtitle: "SSAO, tone mapping, color grading",
                category: .advanced
            ),
            DemoItem(
                comingSoonTitle: "2D Shape Extrude",
                icon: "scribble.variable",
                subtitle: "Extrude SVG paths into 3D meshes",
                category: .advanced
            ),
            DemoItem(
                comingSoonTitle: "Reflection Probes",
                icon: "circle.lefthalf.filled",
                subtitle: "Local environment reflections",
                category: .advanced
            ),
            DemoItem(
                comingSoonTitle: "Secondary Camera (PiP)",
                icon: "pip.fill",
                subtitle: "Picture-in-picture from a second camera",
                category: .advanced,
                version: "4.4"
            ),
        ]

        // MARK: AR -- iOS only platform-wise; most features still ported from Android
        #if os(iOS)
        items.append(contentsOf: [
            DemoItem(title: "AR Debug (Rerun)", icon: "antenna.radiowaves.left.and.right", subtitle: "Stream camera pose & planes to the Rerun viewer", category: .ar) {
                RerunDebugDemo()
            },
            DemoItem(title: "Orbital AR", icon: "circle.dotted", subtitle: "Models orbit around you in AR", category: .ar) {
                OrbitalARDemo()
            },
            DemoItem(title: "AR Recording", icon: "record.circle", subtitle: "Capture the AR session as a screen video (record-only on iOS)", category: .ar) {
                ARRecorderDemo()
            },
            DemoItem(title: "AR Lighting", icon: "lightbulb.max.fill", subtitle: "Compare .mainLight / .fillLight modifier presets", category: .ar) {
                ARLightingDemo()
            },
            DemoItem(title: "AR Plane Placement", icon: "arkit", subtitle: "Tap a detected plane to place a model", category: .ar) {
                ARPlacementDemo()
            },
            DemoItem(
                comingSoonTitle: "AR Image Tracking",
                icon: "viewfinder.circle.fill",
                subtitle: "Detect and track reference images",
                category: .ar
            ),
            DemoItem(
                comingSoonTitle: "AR Augmented Faces",
                icon: "face.smiling.inverse",
                subtitle: "Face mesh tracking & overlays",
                category: .ar
            ),
            DemoItem(
                comingSoonTitle: "AR Cloud Anchors",
                icon: "icloud.fill",
                subtitle: "Persistent multi-user anchors",
                category: .ar
            ),
            DemoItem(
                comingSoonTitle: "AR Streetscape",
                icon: "map.fill",
                subtitle: "Geospatial building & terrain meshes",
                category: .ar
            ),
            DemoItem(
                comingSoonTitle: "AR Body Pose",
                icon: "figure.stand",
                subtitle: "Real-time body skeleton tracking",
                category: .ar
            ),
            DemoItem(
                comingSoonTitle: "AR Record & Playback",
                icon: "record.circle.fill",
                subtitle: "Capture & replay AR sessions",
                category: .ar
            ),
            DemoItem(
                comingSoonTitle: "AR Depth Occlusion",
                icon: "square.3.layers.3d.down.right",
                subtitle: "Real-world depth masks virtual objects",
                category: .ar
            ),
            DemoItem(title: "AR Instant Placement", icon: "bolt.fill", subtitle: "Place models without plane detection", category: .ar) {
                ARInstantPlacementDemo()
            },
            DemoItem(
                comingSoonTitle: "AR Terrain Anchors",
                icon: "mountain.2.fill",
                subtitle: "Anchor models on geospatial terrain",
                category: .ar
            ),
            DemoItem(
                comingSoonTitle: "AR Rooftop Anchors",
                icon: "house.fill",
                subtitle: "Anchor models on geospatial rooftops",
                category: .ar
            ),
            DemoItem(
                comingSoonTitle: "AR Image Stabilization",
                icon: "camera.metering.matrix",
                subtitle: "EIS for smoother AR camera feed",
                category: .ar
            ),
        ])
        #endif

        return items
    }
}

// MARK: - Scene row view (Liquid Glass card)

private struct SceneRow: View {
    let scene: DemoItem

    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: scene.status.isAvailable
                                ? [Color.blue.opacity(0.25), Color.purple.opacity(0.15)]
                                : [Color.secondary.opacity(0.15), Color.secondary.opacity(0.08)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                Image(systemName: scene.icon)
                    .font(.title3)
                    .foregroundStyle(scene.status.isAvailable ? Color.blue : Color.secondary)
            }
            .frame(width: 44, height: 44)
            .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 8) {
                    Text(scene.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(scene.status.isAvailable ? Color.primary : Color.secondary)
                        .lineLimit(1)

                    if let version = scene.status.comingSoonVersion {
                        Text("v\(version)")
                            .font(.caption2.weight(.semibold))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(.orange.opacity(0.18), in: Capsule())
                            .foregroundStyle(.orange)
                    }
                }

                Text(scene.subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .opacity(scene.status.isAvailable ? 1.0 : 0.7)
            }

            Spacer(minLength: 4)

            Image(systemName: "chevron.right")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.tertiary)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(Color.primary.opacity(0.06), lineWidth: 0.5)
        )
    }
}
