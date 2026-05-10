import SwiftUI
import SceneViewSwift

/// Scenes tab -- curated preset scenes grouped by category.
struct SamplesTab: View {
    private let scenes: [DemoItem] = Self.allScenes()

    var body: some View {
        NavigationStack {
            List {
                let grouped = Dictionary(grouping: scenes) { $0.category }
                let sortedCategories = grouped.keys.sorted()
                ForEach(sortedCategories, id: \.self) { category in
                    Section(category.rawValue) {
                        ForEach(grouped[category]!) { scene in
                            NavigationLink {
                                destinationView(for: scene)
                            } label: {
                                SceneRow(scene: scene)
                            }
                            .accessibilityLabel(accessibilityLabel(for: scene))
                        }
                    }
                }
            }
            .navigationTitle("Scenes")
        }
    }

    @ViewBuilder
    private func destinationView(for scene: DemoItem) -> some View {
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

            DemoItem(title: "All Shapes", icon: "cube.fill", subtitle: "Cube, sphere, cylinder, cone, plane", category: .geometry) {
                AllShapesDemo()
            },
            DemoItem(title: "Materials", icon: "paintpalette.fill", subtitle: "PBR metallic & roughness spectrum", category: .geometry) {
                MaterialsDemo()
            },
            DemoItem(title: "Custom Mesh", icon: "diamond.fill", subtitle: "Vertices, normals & triangle indices", category: .geometry) {
                CustomMeshDemo()
            },
            DemoItem(
                comingSoonTitle: "Animated Model",
                icon: "figure.run",
                subtitle: "Skinning, rig-driven animation playback",
                category: .geometry
            ),

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
                ImagePlaneDemo()
            },
            DemoItem(
                comingSoonTitle: "Video Texture",
                icon: "video.fill",
                subtitle: "Play video on a 3D surface",
                category: .content
            ),

            // MARK: Lighting

            DemoItem(title: "Light Types", icon: "lightbulb.fill", subtitle: "Directional, point & spot lights", category: .lighting) {
                LightTypesDemo()
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

            // MARK: Interaction (mostly coming soon — Android-only today)

            DemoItem(
                comingSoonTitle: "Camera Controls",
                icon: "camera.aperture",
                subtitle: "Orbit, pan, fly & first-person modes",
                category: .interaction
            ),
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

            DemoItem(title: "Orbit Camera", icon: "camera.fill", subtitle: "Interactive orbit with grid reference", category: .advanced) {
                OrbitCameraDemo()
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
        ]

        // MARK: AR -- iOS only platform-wise; most features still ported from Android
        #if os(iOS)
        items.append(contentsOf: [
            DemoItem(title: "AR Debug (Rerun)", icon: "antenna.radiowaves.left.and.right", subtitle: "Stream camera pose & planes to the Rerun viewer", category: .ar) {
                RerunDebugDemo()
            },
            DemoItem(
                comingSoonTitle: "AR Plane Placement",
                icon: "arkit",
                subtitle: "Tap a detected plane to place a model",
                category: .ar
            ),
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
            DemoItem(
                comingSoonTitle: "AR Instant Placement",
                icon: "bolt.fill",
                subtitle: "Place models without plane detection",
                category: .ar
            ),
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

// MARK: - Scene row view

private struct SceneRow: View {
    let scene: DemoItem

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: scene.icon)
                .font(.title2)
                .foregroundStyle(scene.status.isAvailable ? Color.blue : Color.secondary)
                .frame(width: 40, height: 40)
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 8) {
                    Text(scene.title)
                        .font(.headline)
                        .foregroundStyle(scene.status.isAvailable ? Color.primary : Color.secondary)

                    if let version = scene.status.comingSoonVersion {
                        Text("Coming v\(version)")
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
        }
        .padding(.vertical, 4)
    }
}
