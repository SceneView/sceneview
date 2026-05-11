## 4.1.1

- Version alignment with SceneView v4.1.1. **Critical hotfix**: native Android Filament binary version mismatch (matVer 71 packages vs 1.70.2 runtime) caused `SIGABRT` on every demo using bundled materials in v4.1.0. Now resolved — all consumers MUST upgrade. See root `CHANGELOG.md`.

## 4.1.0

- Version alignment with SceneView v4.1.0. ⚠️ Native Android renderer defaults changed: main light intensity 100k → 10k lux, shadows on, fill light, SSAO + bloom, neutral exposure. Existing apps embedding `sceneview_flutter` will render with the new RealityKit-equivalent look. See root `CHANGELOG.md`.

## 4.0.9

- Version alignment with SceneView v4.0.7 (Rerun Save & Share, scan-to-open deep-links, Play Store canary CI).

## 4.0.2

- Version alignment with SceneView v4.0.2 (Filament destroy-order crash fixes, BillboardNode mirror, ViewNode reactive props, security bumps).

## 4.0.1

- Version alignment with SceneView v4.0.1

## 3.6.2

- Version alignment with SceneView v3.6.2

## 3.6.1

- Initial public release
- SceneView widget for Android and iOS
- ModelNode with GLB model loading
- Gesture controls (orbit, zoom, pan)
- Environment and lighting configuration

## 3.6.0

- Update SceneView dependency to 3.6.0 (Filament 1.70.1)
- Add `repository`, `issue_tracker`, and `topics` to pubspec.yaml for pub.dev discoverability
- Improve README with badges, detailed setup instructions, full controller API table, and architecture diagram
- Add LICENSE file (Apache-2.0)

## 3.5.0

- Update SceneView dependency to 3.5.0
- Version alignment with SceneView SDK

## 3.4.7

- Update SceneView dependency to 2.3.0 (latest on Maven Central)
- Fix missing `addGeometry` and `addLight` method handlers on Android (no longer crash with `notImplemented`)
- Fix `rememberEnvironment` null safety in Android bridge
- Fix iOS bridge to track per-model scale (not just paths)
- Add proper `dispose()` to SceneViewController
- Add `StateError` when calling controller methods before view is ready
- Add `isAttached` property to SceneViewController
- Update Kotlin to 2.0.21, Compose BOM to 2024.06.00, compileSdk to 35
- Update iOS podspec version to 3.4.7
- Improve Dart documentation on all public APIs

## 0.1.0

- Initial scaffold release
- SceneView widget (3D) with PlatformView on Android and iOS
- ARSceneView widget (AR) with PlatformView on Android and iOS
- SceneViewController with method channel bridge
- ModelNode, GeometryNode, LightNode data classes
- Android: SceneViewPlugin with ComposeView + Scene composable
- iOS: SceneViewPlugin with UIHostingController + SceneViewSwift
- Example app with 3D model viewer
