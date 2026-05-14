import Foundation

/// Cache-warming utility for `SampleAssets` categories.
///
/// Stage 1 deliverable for [#1152](https://github.com/sceneview/sceneview/issues/1152) —
/// a demo can prefetch the whole category it will display when first opened so
/// the next call to `SketchfabAssetResolver.resolve` returns a cached URL
/// instantly without showing a spinner.
///
/// **Behavior.**
///  - Launches one `SketchfabAssetResolver.resolve` task per slug.
///  - Concurrency is capped at `maxParallelDownloads` so we don't fan out
///    to all of `SampleAssets.all` on a metered connection.
///  - Individual failures are swallowed (per-slug `try?`) so one bad UID —
///    deleted by its author, rate-limited, etc. — never kills the warmup
///    for the whole category.
///
/// **Mirror.** Same contract as `SketchfabPrefetch.kt`
/// (`Dispatchers.IO.limitedParallelism(4)`). Keep both in sync.
enum SketchfabPrefetch {

    /// Hard ceiling on simultaneous Sketchfab downloads during a warm-up.
    static let maxParallelDownloads: Int = 4

    /// Prefetch every slug in `slugs` in parallel.
    ///
    /// Suspends until every download has either completed or failed. Returns
    /// the count of slugs that ended up with a real cache hit (or bundled
    /// fallback). Never throws.
    @discardableResult
    static func prefetchAll(
        _ slugs: [SketchfabSlug],
        resolver: SketchfabAssetResolver = SketchfabAssetResolver()
    ) async -> Int {
        guard !slugs.isEmpty else { return 0 }
        return await withTaskGroup(of: Bool.self) { group in
            var success = 0
            var inFlight = 0
            var index = 0
            // Bounded concurrency: add at most `maxParallelDownloads` tasks
            // at a time. When one finishes, queue the next slug if any.
            while index < slugs.count, inFlight < maxParallelDownloads {
                let slug = slugs[index]
                group.addTask {
                    do {
                        _ = try await resolver.resolve(slug)
                        return true
                    } catch {
                        return false
                    }
                }
                index += 1
                inFlight += 1
            }
            while let result = await group.next() {
                if result { success += 1 }
                inFlight -= 1
                if index < slugs.count {
                    let slug = slugs[index]
                    group.addTask {
                        do {
                            _ = try await resolver.resolve(slug)
                            return true
                        } catch {
                            return false
                        }
                    }
                    index += 1
                    inFlight += 1
                }
            }
            return success
        }
    }
}
