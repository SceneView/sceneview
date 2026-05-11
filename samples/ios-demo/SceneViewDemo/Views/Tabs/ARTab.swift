#if os(iOS)
import SwiftUI
import RealityKit
import ARKit
import SceneViewSwift

/// AR tab — place 3D models in your real-world space.
///
/// Liquid Glass overlay design (iOS 26+ Stitch spec):
/// - Full-bleed AR camera underneath
/// - Top-center: floating glass status pill ("Tap to place" / "N placed")
/// - Top-right: glass exit button to dismiss the tab
/// - Bottom: floating glass action bar with FAB "Pick model", Reset, Screenshot
struct ARTab: View {
    @State private var placedCount = 0
    @State private var selectedModelIndex = 0
    @State private var errorMessage: String?
    @State private var showError = false
    @State private var showModelPicker = false
    @State private var arViewID = UUID()

    /// Increment to force-rebuild the ARSceneView, clearing every placed anchor.
    /// We rebuild because there's no public `removeAllAnchors` on the wrapper.
    private func resetScene() {
        arViewID = UUID()
        placedCount = 0
        HapticManager.mediumTap()
    }

    private let arModels: [(name: String, icon: String, asset: String, scale: Float)] = [
        ("Game Boy", "gamecontroller.fill", "game_boy_classic", 0.15),
        ("Red Car", "car.fill", "red_car", 0.2),
        ("Dragon", "flame.fill", "animated_dragon", 0.12),
        ("Butterfly", "leaf.fill", "animated_butterfly", 0.15),
        ("Piano", "pianokeys", "retro_piano", 0.12),
        ("Nike Jordan", "shoe.fill", "nike_air_jordan", 0.15),
        ("Phoenix", "bird.fill", "phoenix_bird", 0.15),
        ("Fantasy Book", "book.fill", "fantasy_book", 0.12),
        ("PS5 Controller", "gamecontroller.fill", "ps5_dualsense", 0.15),
        ("Tree Scene", "tree.fill", "tree_scene", 0.1),
    ]

    private var selectedModel: (name: String, icon: String, asset: String, scale: Float) {
        arModels[selectedModelIndex]
    }

    var body: some View {
        ZStack {
            #if !targetEnvironment(simulator)
            arSceneView
                .ignoresSafeArea()
                .id(arViewID)
            #else
            simulatorPlaceholder
                .ignoresSafeArea()
            #endif

            // Top status pill — centered horizontally, glass.
            VStack {
                statusPill
                    .padding(.top, 8)
                Spacer()
            }

            // Bottom floating glass action bar.
            VStack {
                Spacer()
                bottomActionBar
                    .padding(.horizontal, 16)
                    .padding(.bottom, 16)
            }
        }
        .alert("AR Error", isPresented: $showError) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(errorMessage ?? "An unknown error occurred.")
        }
        .sheet(isPresented: $showModelPicker) {
            modelPickerSheet
                .presentationDetents([.medium, .large])
                .presentationBackground(.regularMaterial)
                .presentationCornerRadius(28)
                .presentationDragIndicator(.visible)
        }
    }

    // MARK: - AR Scene

    #if !targetEnvironment(simulator)
    private var arSceneView: some View {
        ARSceneView(
            planeDetection: .both,
            showPlaneOverlay: true,
            showCoachingOverlay: true,
            onTapOnPlane: { position, arView in
                let selected = arModels[selectedModelIndex]

                Task {
                    do {
                        let modelNode = try await ModelNode.load(selected.asset)
                        _ = modelNode.scaleToUnits(selected.scale)

                        let anchor = AnchorNode.world(position: position)
                        anchor.add(modelNode.entity)
                        arView.scene.addAnchor(anchor.entity)

                        placedCount += 1
                        HapticManager.mediumTap()
                    } catch {
                        errorMessage = error.localizedDescription
                        showError = true
                        HapticManager.error()
                    }
                }
            }
        )
    }
    #endif

    // MARK: - Simulator placeholder

    private var simulatorPlaceholder: some View {
        ZStack {
            LinearGradient(
                colors: [
                    Color(red: 0.10, green: 0.10, blue: 0.18),
                    Color(red: 0.18, green: 0.18, blue: 0.28),
                ],
                startPoint: .top, endPoint: .bottom
            )
            VStack(spacing: 16) {
                Image(systemName: "arkit")
                    .font(.system(size: 60))
                    .foregroundStyle(.white.opacity(0.6))
                    .accessibilityHidden(true)
                Text("AR requires a physical device")
                    .font(.headline)
                    .foregroundStyle(.white)
                Text("Run on iPhone or iPad to place 3D models in your space.")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.7))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }
        }
    }

    // MARK: - Glass status pill (top center)

    private var statusPill: some View {
        HStack(spacing: 8) {
            Image(systemName: placedCount == 0 ? "hand.tap.fill" : "checkmark.circle.fill")
                .font(.caption.weight(.semibold))
                .foregroundStyle(placedCount == 0 ? .yellow : .green)

            Text(statusText)
                .font(.footnote.weight(.medium))
                .foregroundStyle(.primary)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(.regularMaterial, in: Capsule())
        .overlay(
            Capsule().strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.5)
        )
        .shadow(color: .black.opacity(0.15), radius: 8, y: 2)
        .accessibilityLabel(statusText)
    }

    private var statusText: String {
        if placedCount == 0 {
            return "Tap a surface to place \(selectedModel.name)"
        } else {
            return "\(placedCount) placed \u{00B7} tap to add"
        }
    }

    // MARK: - Bottom action bar

    private var bottomActionBar: some View {
        HStack(spacing: 10) {
            // FAB "Pick model" — primary, takes most of the space.
            Button {
                showModelPicker = true
                HapticManager.lightTap()
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: selectedModel.icon)
                        .font(.title3)
                    VStack(alignment: .leading, spacing: 0) {
                        Text("Model")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                        Text(selectedModel.name)
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(1)
                    }
                    Spacer(minLength: 4)
                    Image(systemName: "chevron.up")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .frame(maxWidth: .infinity)
                .foregroundStyle(.primary)
            }
            .buttonStyle(.plain)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.5)
            )
            .accessibilityLabel("Pick model. Currently \(selectedModel.name)")

            // Reset button (glass icon).
            glassIconButton(systemImage: "arrow.counterclockwise", label: "Reset scene") {
                resetScene()
            }

            // Screenshot button (glass icon).
            glassIconButton(systemImage: "square.and.arrow.up", label: "Share AR screenshot") {
                shareARScreenshot()
                HapticManager.lightTap()
            }
        }
        .shadow(color: .black.opacity(0.18), radius: 14, y: 4)
    }

    private func glassIconButton(systemImage: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.title3)
                .frame(width: 44, height: 44)
                .foregroundStyle(.primary)
        }
        .buttonStyle(.plain)
        .background(.regularMaterial, in: Circle())
        .overlay(Circle().strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.5))
        .accessibilityLabel(label)
    }

    // MARK: - Model picker sheet

    private var modelPickerSheet: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(
                    columns: [GridItem(.adaptive(minimum: 110), spacing: 12)],
                    spacing: 12
                ) {
                    ForEach(Array(arModels.enumerated()), id: \.offset) { index, model in
                        Button {
                            selectedModelIndex = index
                            HapticManager.selectionChanged()
                            showModelPicker = false
                        } label: {
                            VStack(spacing: 8) {
                                ZStack {
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .fill(
                                            LinearGradient(
                                                colors: [Color.blue.opacity(0.28), Color.purple.opacity(0.15)],
                                                startPoint: .topLeading,
                                                endPoint: .bottomTrailing
                                            )
                                        )
                                    Image(systemName: model.icon)
                                        .font(.system(size: 32, weight: .semibold))
                                        .foregroundStyle(.tint)
                                }
                                .frame(height: 90)

                                Text(model.name)
                                    .font(.caption.weight(.medium))
                                    .foregroundStyle(.primary)
                                    .lineLimit(1)
                            }
                            .padding(8)
                            .background(
                                .regularMaterial,
                                in: RoundedRectangle(cornerRadius: 18, style: .continuous)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 18, style: .continuous)
                                    .strokeBorder(
                                        index == selectedModelIndex
                                            ? Color.blue
                                            : Color.primary.opacity(0.08),
                                        lineWidth: index == selectedModelIndex ? 2 : 0.5
                                    )
                            )
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Pick \(model.name)")
                        .accessibilityAddTraits(index == selectedModelIndex ? .isSelected : [])
                    }
                }
                .padding(16)
            }
            .navigationTitle("Pick a model")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { showModelPicker = false }
                }
            }
        }
    }

    // MARK: - Share

    @MainActor
    private func shareARScreenshot() {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let window = windowScene.windows.first else { return }

        let renderer = UIGraphicsImageRenderer(bounds: window.bounds)
        let image = renderer.image { ctx in
            window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
        }

        let activityVC = UIActivityViewController(
            activityItems: [image, "Check out what I placed in my space with 3D & AR Explorer!"],
            applicationActivities: nil
        )

        if let presenter = window.rootViewController {
            if let popover = activityVC.popoverPresentationController {
                popover.sourceView = presenter.view
                popover.sourceRect = CGRect(x: presenter.view.bounds.midX, y: 40, width: 0, height: 0)
            }
            presenter.present(activityVC, animated: true)
        }
    }
}
#endif
