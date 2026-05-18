<!-- category: Fixed -->
`SceneView` no longer triggers "Modifying state during view update" Xcode runtime warnings — `appliedMainSlot`, `appliedFillSlot`, and `appliedSkyboxResource` are now held in a private reference-type cache class rather than individual `@State` properties, so mutations inside `RealityView.update:` are invisible to SwiftUI's state-change detection.
