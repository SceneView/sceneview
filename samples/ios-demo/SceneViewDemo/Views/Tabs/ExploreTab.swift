import SwiftUI
import RealityKit
import SceneViewSwift

/// Model data for the gallery.
struct ModelItem: Identifiable, Hashable {
    let id: String
    let name: String
    let icon: String
    let asset: String
    let scale: Float
    let category: ModelCategory

    func hash(into hasher: inout Hasher) { hasher.combine(id) }
    static func == (lhs: ModelItem, rhs: ModelItem) -> Bool { lhs.id == rhs.id }

    static let all: [ModelItem] = [
        // Vehicles
        ModelItem(id: "red_car",              name: "Red Car",       icon: "car.fill",         asset: "red_car",              scale: 1.0, category: .vehicles),
        ModelItem(id: "ferrari_f40",          name: "Ferrari F40",   icon: "car.side.fill",    asset: "ferrari_f40",          scale: 0.6, category: .vehicles),
        ModelItem(id: "porsche_911",          name: "Porsche 911",   icon: "car.rear.fill",    asset: "porsche_911",          scale: 0.6, category: .vehicles),
        ModelItem(id: "porsche_911_turbo",    name: "Porsche Turbo", icon: "car.side.fill",    asset: "porsche_911_turbo",    scale: 0.5, category: .vehicles),
        ModelItem(id: "lamborghini_countach", name: "Lamborghini",   icon: "car.fill",         asset: "lamborghini_countach", scale: 0.5, category: .vehicles),
        ModelItem(id: "shelby_cobra",         name: "Shelby Cobra",  icon: "car.rear.fill",    asset: "shelby_cobra",         scale: 0.6, category: .vehicles),
        ModelItem(id: "bmw_m3_e30",           name: "BMW M3 E30",    icon: "car.side.fill",    asset: "bmw_m3_e30",           scale: 0.6, category: .vehicles),
        ModelItem(id: "mercedes_a45_amg",     name: "Mercedes AMG",  icon: "car.fill",         asset: "mercedes_a45_amg",     scale: 0.5, category: .vehicles),
        ModelItem(id: "audi_tt",              name: "Audi TT",       icon: "car.rear.fill",    asset: "audi_tt",              scale: 0.7, category: .vehicles),
        ModelItem(id: "fiat_punto",           name: "Fiat Punto",    icon: "car.side.fill",    asset: "fiat_punto",           scale: 0.7, category: .vehicles),
        ModelItem(id: "tesla_cybertruck",     name: "Cybertruck",    icon: "truck.box.fill",   asset: "tesla_cybertruck",     scale: 0.6, category: .vehicles),
        ModelItem(id: "cyberpunk_car",        name: "Cyberpunk Car", icon: "bolt.car.fill",    asset: "cyberpunk_car",        scale: 0.8, category: .vehicles),
        ModelItem(id: "cyberpunk_hovercar",   name: "Hovercar",      icon: "airplane",         asset: "cyberpunk_hovercar",   scale: 0.6, category: .vehicles),

        // Creatures
        ModelItem(id: "animated_dragon",     name: "Dragon",            icon: "flame.fill",        asset: "animated_dragon",     scale: 0.6, category: .creatures),
        ModelItem(id: "black_dragon",        name: "Black Dragon",      icon: "lizard.fill",       asset: "black_dragon",        scale: 0.5, category: .creatures),
        ModelItem(id: "phoenix_bird",        name: "Phoenix",           icon: "bird.fill",         asset: "phoenix_bird",        scale: 0.8, category: .creatures),
        ModelItem(id: "animated_butterfly",  name: "Butterfly",         icon: "sparkles",          asset: "animated_butterfly",  scale: 0.8, category: .creatures),
        ModelItem(id: "mosquito_amber",      name: "Mosquito in Amber", icon: "ant.fill",          asset: "mosquito_amber",      scale: 1.0, category: .creatures),
        ModelItem(id: "cyberpunk_character", name: "Cyber Guy",         icon: "figure.run",        asset: "cyberpunk_character", scale: 0.7, category: .creatures),

        // Objects
        ModelItem(id: "game_boy_classic", name: "Game Boy",       icon: "gamecontroller.fill",    asset: "game_boy_classic", scale: 0.8, category: .objects),
        ModelItem(id: "nintendo_switch",  name: "Switch",         icon: "square.grid.2x2.fill",  asset: "nintendo_switch",  scale: 0.8, category: .objects),
        ModelItem(id: "ps5_dualsense",    name: "PS5 Controller", icon: "gamecontroller",         asset: "ps5_dualsense",    scale: 0.8, category: .objects),
        ModelItem(id: "nike_air_jordan",  name: "Air Jordan",     icon: "shoe.fill",              asset: "nike_air_jordan",  scale: 0.8, category: .objects),
        ModelItem(id: "retro_piano",      name: "Retro Piano",    icon: "pianokeys",              asset: "retro_piano",      scale: 0.7, category: .objects),
        ModelItem(id: "fantasy_book",     name: "Fantasy Book",   icon: "book.fill",              asset: "fantasy_book",     scale: 0.7, category: .objects),

        // Scenes
        ModelItem(id: "tree_scene",           name: "Tree Scene",  icon: "tree.fill",      asset: "tree_scene",           scale: 0.6, category: .scenes),
        ModelItem(id: "ship_in_clouds",       name: "Ship in Sky", icon: "cloud.fill",     asset: "ship_in_clouds",       scale: 0.5, category: .scenes),
        ModelItem(id: "earthquake_california", name: "Earthquake", icon: "waveform.path",  asset: "earthquake_california", scale: 0.4, category: .scenes),
    ]
}

enum ModelCategory: String, CaseIterable {
    case vehicles = "Vehicles"
    case creatures = "Creatures"
    case objects = "Objects"
    case scenes = "Scenes"
    case favorites = "Favorites"

    var gradientColors: [Color] {
        switch self {
        case .vehicles:  return [Color.blue.opacity(0.3), Color.cyan.opacity(0.15)]
        case .creatures: return [Color.orange.opacity(0.3), Color.yellow.opacity(0.15)]
        case .objects:   return [Color.purple.opacity(0.3), Color.pink.opacity(0.15)]
        case .scenes:    return [Color.green.opacity(0.3), Color.teal.opacity(0.15)]
        case .favorites: return [Color.red.opacity(0.3), Color.pink.opacity(0.15)]
        }
    }

    var iconColor: Color {
        switch self {
        case .vehicles:  return .blue
        case .creatures: return .orange
        case .objects:   return .purple
        case .scenes:    return .green
        case .favorites: return .red
        }
    }
}

/// Sketchfab-style discovery categories shown as chips on the Explore tab.
///
/// The display name is what the user sees on the chip; `searchQuery` is the term sent to
/// `SketchfabService.search(query:)` when the user taps the category.
enum SketchfabCategory: String, CaseIterable, Identifiable {
    case vehicles, characters, architecture, nature, sciFi, abstract, furniture, weapons

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .vehicles:     return "Vehicles"
        case .characters:   return "Characters"
        case .architecture: return "Architecture"
        case .nature:       return "Nature"
        case .sciFi:        return "Sci-Fi"
        case .abstract:     return "Abstract"
        case .furniture:    return "Furniture"
        case .weapons:      return "Weapons"
        }
    }

    var searchQuery: String {
        switch self {
        case .vehicles:     return "vehicle car"
        case .characters:   return "character"
        case .architecture: return "architecture building"
        case .nature:       return "nature plant"
        case .sciFi:        return "sci-fi spaceship"
        case .abstract:     return "abstract"
        case .furniture:    return "furniture"
        case .weapons:      return "weapon"
        }
    }

    var icon: String {
        switch self {
        case .vehicles:     return "car.side.fill"
        case .characters:   return "figure.stand"
        case .architecture: return "building.2.fill"
        case .nature:       return "leaf.fill"
        case .sciFi:        return "sparkles"
        case .abstract:     return "scribble.variable"
        case .furniture:    return "sofa.fill"
        case .weapons:      return "shield.fill"
        }
    }
}

/// User defaults–backed list of the last 5 search queries, surfaced under "Recent searches".
@MainActor
@Observable
final class RecentSearches {
    private let storageKey = "io.github.sceneview.demo.recentSearches"
    private let maxItems = 5

    private(set) var items: [String] = []

    init() { load() }

    func push(_ query: String) {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        items.removeAll { $0.caseInsensitiveCompare(trimmed) == .orderedSame }
        items.insert(trimmed, at: 0)
        if items.count > maxItems { items = Array(items.prefix(maxItems)) }
        save()
    }

    func remove(_ query: String) {
        items.removeAll { $0 == query }
        save()
    }

    func clear() {
        items.removeAll()
        save()
    }

    private func load() {
        items = UserDefaults.standard.stringArray(forKey: storageKey) ?? []
    }

    private func save() {
        UserDefaults.standard.set(items, forKey: storageKey)
    }
}

/// The main Explore tab — Liquid Glass discovery hub for 3D models.
///
/// Layout follows the Stitch mockup (iOS Liquid Glass design system):
/// - Featured carousel of curated models (currently from the bundled `ModelItem.all` set;
///   V1.1 will pull from `SketchfabService.featured()` when an API key is configured).
/// - Categories chips that filter / search by topic.
/// - Recent searches list, persisted across launches.
/// - Native `.searchable` search bar that queries Sketchfab when an API key is set.
struct ExploreTab: View {
    @State private var searchText = ""
    @State private var selectedModel: ModelItem?
    @State private var selectedCategory: SketchfabCategory?
    @State private var recentSearches = RecentSearches()
    private let favoritesManager = FavoritesManager.shared

    /// Curated featured set — first 6 bundled models, picked for visual variety.
    private var featuredModels: [ModelItem] {
        let ids = ["ferrari_f40", "animated_dragon", "cyberpunk_character",
                   "game_boy_classic", "fantasy_book", "tree_scene"]
        return ids.compactMap { id in ModelItem.all.first { $0.id == id } }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 28) {
                    featuredSection
                    categoriesSection
                    if !recentSearches.items.isEmpty {
                        recentSearchesSection
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 24)
            }
            .navigationTitle("Explore")
            .searchable(text: $searchText, prompt: "Search 3D models on Sketchfab")
            .onSubmit(of: .search) {
                let q = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
                if !q.isEmpty {
                    recentSearches.push(q)
                    #if os(iOS)
                    HapticManager.lightTap()
                    #endif
                }
            }
            .navigationDestination(item: $selectedModel) { model in
                ModelViewerScreen(model: model)
            }
            .sheet(item: $selectedCategory) { category in
                CategorySheet(category: category) { query in
                    searchText = query
                    recentSearches.push(query)
                }
                .presentationDetents([.medium, .large])
                #if os(iOS)
                .presentationBackground(.regularMaterial)
                .presentationCornerRadius(28)
                #endif
            }
        }
    }

    // MARK: - Featured section (horizontal carousel)

    private var featuredSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Featured")
                    .font(.title2.weight(.bold))
                Spacer()
                Button("See all") {
                    // V1.1: navigate to a paged listing of featured Sketchfab models
                }
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.tint)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 14) {
                    ForEach(featuredModels) { model in
                        FeaturedCard(model: model) {
                            selectedModel = model
                            #if os(iOS)
                            HapticManager.lightTap()
                            #endif
                        }
                    }
                }
                .padding(.bottom, 4)
            }
            .scrollClipDisabled()
        }
    }

    // MARK: - Categories section (chips grid)

    private var categoriesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Categories")
                .font(.title2.weight(.bold))
            // FlowLayout-style category chips. Uses LazyVGrid for portable wrapping.
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 100), spacing: 8)],
                      alignment: .leading, spacing: 8) {
                ForEach(SketchfabCategory.allCases) { category in
                    CategoryChip(category: category) {
                        selectedCategory = category
                        #if os(iOS)
                        HapticManager.selectionChanged()
                        #endif
                    }
                }
            }
        }
    }

    // MARK: - Recent searches

    private var recentSearchesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Recent searches")
                    .font(.title2.weight(.bold))
                Spacer()
                Button("Clear") {
                    recentSearches.clear()
                }
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.tint)
            }
            VStack(spacing: 6) {
                ForEach(recentSearches.items, id: \.self) { query in
                    RecentSearchRow(query: query) {
                        searchText = query
                    } onRemove: {
                        recentSearches.remove(query)
                    }
                }
            }
        }
    }
}

// MARK: - Featured card (large image-tile in the horizontal carousel)

private struct FeaturedCard: View {
    let model: ModelItem
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 0) {
                ZStack {
                    LinearGradient(
                        colors: model.category.gradientColors,
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    Image(systemName: model.icon)
                        .font(.system(size: 56, weight: .semibold))
                        .foregroundStyle(model.category.iconColor)
                        .shadow(color: model.category.iconColor.opacity(0.3), radius: 12)
                }
                .frame(width: 200, height: 160)
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))

                VStack(alignment: .leading, spacing: 2) {
                    Text(model.name)
                        .font(.headline)
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                    Text(model.category.rawValue)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: 200, alignment: .leading)
                .padding(.top, 8)
                .padding(.horizontal, 4)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(model.name), \(model.category.rawValue), featured")
    }
}

// MARK: - Category chip

private struct CategoryChip: View {
    let category: SketchfabCategory
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 6) {
                Image(systemName: category.icon)
                    .font(.caption.weight(.semibold))
                Text(category.displayName)
                    .font(.subheadline.weight(.medium))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity)
            .background(.tint.opacity(0.12), in: Capsule())
            .foregroundStyle(.tint)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(category.displayName) category")
    }
}

// MARK: - Recent search row

private struct RecentSearchRow: View {
    let query: String
    let onTap: () -> Void
    let onRemove: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Button(action: onTap) {
                Text(query)
                    .font(.subheadline)
                    .foregroundStyle(.primary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
            Button(action: onRemove) {
                Image(systemName: "xmark")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
                    .padding(6)
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Remove \(query) from recent searches")
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

// MARK: - Category sheet (presented as a modal when a chip is tapped)

private struct CategorySheet: View {
    let category: SketchfabCategory
    let onSearchTriggered: (String) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Spacer(minLength: 16)
                Image(systemName: category.icon)
                    .font(.system(size: 48, weight: .semibold))
                    .foregroundStyle(.tint)
                    .padding(20)
                    .background(.tint.opacity(0.15), in: Circle())

                Text(category.displayName)
                    .font(.title.weight(.bold))

                Text("Browse \(category.displayName.lowercased()) models from Sketchfab. Tap the search button to load results for this category.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)

                Button {
                    onSearchTriggered(category.searchQuery)
                    dismiss()
                } label: {
                    Label("Search \(category.displayName)", systemImage: "magnifyingglass")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(.tint, in: Capsule())
                        .foregroundStyle(.white)
                }
                .padding(.horizontal, 24)

                Spacer()
            }
            .navigationTitle("Category")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

// MARK: - Model card

private struct ModelCard: View {
    let model: ModelItem
    let isFavorite: Bool
    let onTap: () -> Void
    let onToggleFavorite: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 8) {
                ZStack(alignment: .topTrailing) {
                    // Model icon preview area
                    RoundedRectangle(cornerRadius: 12)
                        .fill(
                            LinearGradient(
                                colors: model.category.gradientColors,
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(height: 120)
                        .overlay {
                            Image(systemName: model.icon)
                                .font(.system(size: 36))
                                .foregroundStyle(model.category.iconColor)
                        }

                    // Favorite button
                    Button(action: onToggleFavorite) {
                        Image(systemName: isFavorite ? "heart.fill" : "heart")
                            .font(.body)
                            .foregroundStyle(isFavorite ? .red : .secondary)
                            .padding(8)
                            .background(.ultraThinMaterial, in: Circle())
                    }
                    .padding(6)
                }

                VStack(spacing: 2) {
                    Text(model.name)
                        .font(.subheadline.weight(.medium))
                        .lineLimit(1)
                    Text(model.category.rawValue)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 4)
            }
            .padding(8)
            .background(Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(model.name), \(model.category.rawValue)")
        .accessibilityAddTraits(.isButton)
    }
}

// MARK: - Full-screen model viewer

struct ModelViewerScreen: View {
    let model: ModelItem
    @State private var autoRotate = true
    @State private var loadedModel: ModelNode?
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var selectedEnvironment: SceneEnvironment = .studio
    @State private var showShareSheet = false
    private let favoritesManager = FavoritesManager.shared

    init(model: ModelItem) {
        self.model = model
    }

    var body: some View {
        ZStack {
            LinearGradient(
                gradient: Gradient(colors: [
                    Color(red: 0.08, green: 0.08, blue: 0.12),
                    Color(red: 0.15, green: 0.15, blue: 0.22),
                    Color(red: 0.10, green: 0.10, blue: 0.18)
                ]),
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            sceneView
                .ignoresSafeArea()

            if isLoading {
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(.white)
                    .scaleEffect(1.5)
            }

            if let errorMessage {
                VStack(spacing: 8) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.title)
                        .foregroundStyle(.yellow)
                    Text("Failed to load model")
                        .font(.headline)
                        .foregroundStyle(.white)
                    Text(errorMessage)
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.7))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                }
                .padding()
                .background(.ultraThinMaterial)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .padding()
            }

            VStack {
                Spacer()
                controlsOverlay
            }
        }
        .navigationTitle(model.name)
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        #endif
        .toolbar {
            ToolbarItemGroup(placement: .primaryAction) {
                Button {
                    favoritesManager.toggle(model.id)
                    #if os(iOS)
                    HapticManager.lightTap()
                    #endif
                } label: {
                    Image(systemName: favoritesManager.isFavorite(model.id) ? "heart.fill" : "heart")
                        .foregroundStyle(favoritesManager.isFavorite(model.id) ? .red : .white)
                }
                .accessibilityLabel(favoritesManager.isFavorite(model.id) ? "Remove from favorites" : "Add to favorites")

                Button {
                    autoRotate.toggle()
                    #if os(iOS)
                    HapticManager.selectionChanged()
                    #endif
                } label: {
                    Image(systemName: autoRotate ? "rotate.3d.fill" : "rotate.3d")
                        .foregroundStyle(.white)
                }
                .accessibilityLabel(autoRotate ? "Stop rotation" : "Start rotation")

                #if os(iOS)
                Button {
                    shareScreenshot()
                } label: {
                    Image(systemName: "square.and.arrow.up")
                        .foregroundStyle(.white)
                }
                .accessibilityLabel("Share screenshot")
                #endif
            }
        }
        .task {
            await loadModel()
        }
    }

    // MARK: - Model Loading

    private func loadModel() async {
        isLoading = true
        errorMessage = nil
        do {
            let node = try await ModelNode.load(model.asset)
            _ = node.scaleToUnits(model.scale)
            loadedModel = node
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    // MARK: - Scene

    @ViewBuilder
    private var sceneView: some View {
        if autoRotate {
            SceneView { root in
                if let loadedModel {
                    loadedModel.entity.position = .zero
                    root.addChild(loadedModel.entity)
                }
            }
            .environment(selectedEnvironment)
            .cameraControls(.orbit)
            .autoRotate(speed: 0.4)
            .id("viewer-auto-\(loadedModel != nil)-\(selectedEnvironment.name)")
        } else {
            SceneView { root in
                if let loadedModel {
                    loadedModel.entity.position = .zero
                    root.addChild(loadedModel.entity)
                }
            }
            .environment(selectedEnvironment)
            .cameraControls(.orbit)
            .id("viewer-manual-\(loadedModel != nil)-\(selectedEnvironment.name)")
        }
    }

    // MARK: - Controls

    private var controlsOverlay: some View {
        VStack(spacing: 12) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(SceneEnvironment.allPresets, id: \.name) { env in
                        Button {
                            selectedEnvironment = env
                            #if os(iOS)
                            HapticManager.lightTap()
                            #endif
                        } label: {
                            Text(env.name)
                                .font(.caption2)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(
                                    selectedEnvironment.name == env.name
                                        ? AnyShapeStyle(.blue)
                                        : AnyShapeStyle(.white.opacity(0.15))
                                )
                                .clipShape(Capsule())
                                .foregroundStyle(.white)
                        }
                    }
                }
            }

            Text("Pinch to zoom \u{00B7} Drag to orbit")
                .font(.caption)
                .foregroundStyle(.white.opacity(0.5))
        }
        .padding()
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .padding()
    }

    // MARK: - Share

    #if os(iOS)
    @MainActor
    private func shareScreenshot() {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let window = windowScene.windows.first else { return }

        let renderer = UIGraphicsImageRenderer(bounds: window.bounds)
        let image = renderer.image { ctx in
            window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
        }

        let activityVC = UIActivityViewController(
            activityItems: [image, "Check out this 3D model in 3D & AR Explorer!"],
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
    #endif
}
