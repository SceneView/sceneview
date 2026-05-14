import SwiftUI

/// Liquid Glass banner that surfaces a pending App Store update.
///
/// Lives at the top of the app hierarchy so it overlays every tab. Mirrors the
/// Android `UpdateBanner` UX: a rounded card with a primary CTA ("Update") and
/// a soft secondary CTA ("Later" → 7-day snooze).
///
/// The banner is hidden when the updater reports `idle` / `checking` /
/// `upToDate`, or when the user has snoozed the prompt. Tapping "Update"
/// jumps straight to the App Store product page via `itms-apps://`.
struct UpdateBanner: View {
    @EnvironmentObject private var updater: AppStoreUpdater

    var body: some View {
        Group {
            if case let .updateAvailable(version, _) = updater.state, !updater.isSnoozed {
                bannerContent(version: version)
            }
        }
        .animation(.snappy, value: updater.state)
    }

    private func bannerContent(version: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "arrow.down.circle.fill")
                .font(.title2)
                .foregroundStyle(.tint)

            VStack(alignment: .leading, spacing: 2) {
                Text("Update available")
                    .font(.subheadline.weight(.semibold))
                Text("Version \(version) is on the App Store.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer(minLength: 8)

            Button {
                updater.openAppStore()
            } label: {
                Text("Update")
                    .font(.subheadline.weight(.semibold))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.small)

            Button {
                updater.snooze()
            } label: {
                Image(systemName: "xmark")
                    .font(.footnote.weight(.semibold))
                    .padding(8)
            }
            .buttonStyle(.plain)
            .foregroundStyle(.secondary)
            .accessibilityLabel("Dismiss for 7 days")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(.regularMaterial)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .stroke(.white.opacity(0.08), lineWidth: 1)
        )
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .shadow(color: .black.opacity(0.18), radius: 14, y: 4)
        .transition(.move(edge: .top).combined(with: .opacity))
    }
}
