# SceneView Platform Overview and Setup Guide

## What is SceneView?

SceneView is a declarative 3D and AR SDK for Android (Jetpack Compose, Filament, ARCore) and Apple platforms -- iOS, macOS, visionOS (SwiftUI, RealityKit, ARKit) -- with shared core logic via Kotlin Multiplatform. It lets developers build 3D and augmented reality experiences using familiar UI patterns: composable functions on Android, SwiftUI views on Apple. Each platform uses its native high-performance renderer -- Filament on Android/Web, RealityKit on Apple -- while a shared KMP core provides portable math, collision, geometry, and animation logic.

---

## Platform Support

| Platform | Renderer | Framework | Status | Version |
|---|---|---|---|---|
| Android | Filament | Jetpack Compose | Stable | 3.6.2 |
| Android TV | Filament | Compose TV | Alpha | 3.6.2 |
| Android XR | Jetpack XR SceneCore | Compose XR | Planned | -- |
| iOS | RealityKit | SwiftUI | Alpha | 3.6.2 |
| macOS | RealityKit | SwiftUI | Alpha | 3.6.2 |
| visionOS | RealityKit | SwiftUI | Alpha | 3.6.2 |
| Web | Filament.js (WASM) | Kotlin/JS | Alpha | 3.6.2 |
| Desktop | Wireframe placeholder | Compose Desktop | Placeholder | -- |
| Flutter | Filament / RealityKit | PlatformView | Alpha | 3.6.2 |
| React Native | Filament / RealityKit | Fabric | Alpha | 3.6.2 |

---

## Architecture

SceneView follows a "native renderer per platform" architecture. Kotlin Multiplatform (KMP) shares logic, not rendering. Each platform uses its native 3D engine.

```
+---------------------------------------------+
|           sceneview-core (KMP)               |
|   math, collision, geometry, animations,     |
|   physics, scene graph, triangulation        |
|       commonMain -> XCFramework              |
+-----------+-----------------+----------------+
            |                 |
   +--------v--------+ +-----v----------+
   |   sceneview     | | SceneViewSwift |
   |   (Android)     | |    (Apple)     |
   |   Filament      | |   RealityKit   |
   +--------+--------+ +-----+----------+
            |                 |
      Compose UI        SwiftUI (native)
                        Flutter (PlatformView)
                        React Native (Fabric)
                        KMP Compose (UIKitView)
```

**Why native renderers?**

- RealityKit is the only path to visionOS spatial computing
- Swift Package integration (1 line SPM) is far simpler than KMP XCFramework
- SceneViewSwift is consumable by Flutter, React Native, and KMP Compose on iOS
- No Filament dependency on Apple means smaller binary, native debugging, native tooling

### Module Map

| Module | Purpose |
|---|---|
| `sceneview-core/` | KMP: portable collision, math, geometry, animation, physics |
| `sceneview/` | Android 3D library: Scene, SceneScope, all node types (Filament) |
| `arsceneview/` | Android AR layer: ARScene, ARSceneScope, ARCore integration |
| `sceneview-web/` | Web 3D library: Kotlin/JS + Filament.js (WebGL2/WASM) |
| `SceneViewSwift/` | Apple 3D+AR library: SceneView, ARSceneView (RealityKit) |
| `flutter/` | Flutter plugin: PlatformView bridge (Android + iOS) |
| `react-native/` | React Native module: Fabric/Turbo bridge (Android + iOS) |

---

## Setup Instructions

### Android (Gradle)

**Minimum requirements:** Min SDK 24 | Target SDK 36 | Kotlin 2.3.20 | Compose BOM compatible

**build.gradle.kts (app module):**

```kotlin
dependencies {
    // 3D only
    implementation("io.github.sceneview:sceneview:4.1.0")

    // AR + 3D (includes sceneview)
    implementation("io.github.sceneview:arsceneview:4.1.0")
}
```

**AndroidManifest.xml (AR apps only):**

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera.ar" android:required="true" />
<application>
    <meta-data android:name="com.google.ar.core" android:value="required" />
</application>
```

### iOS / macOS / visionOS (Swift Package Manager)

**Minimum requirements:** iOS 17+ | macOS 14+ | visionOS 1+

In Xcode: File > Add Package Dependencies, enter:

```
https://github.com/sceneview/sceneview-swift.git
```

Set version rule: from "3.6.0"

Then import in Swift files:

```swift
import SceneViewSwift
```

### Web (npm)

```bash
npm install @sceneview/sceneview-web
```

Requires a WebGL2-capable browser. Uses Filament.js compiled to WebAssembly -- the same rendering engine as Android SceneView.

```kotlin
import io.github.sceneview.web.SceneView
import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement

fun main() {
    val canvas = document.getElementById("scene-canvas") as HTMLCanvasElement
    SceneView.create(
        canvas = canvas,
        configure = {
            camera { eye(0.0, 1.5, 5.0); target(0.0, 0.0, 0.0) }
            light { directional(); intensity(100_000.0) }
            model("models/helmet.glb")
        },
        onReady = { it.startRendering() }
    )
}
```

---

## Key Concepts

### Declarative Node System

SceneView uses a declarative, composable API. Instead of imperatively creating and managing nodes, you declare them inside a content block:

```kotlin
SceneView(modifier = Modifier.fillMaxSize()) {
    // This is a SceneScope -- declare nodes as composables
    ModelNode(modelInstance = instance, scaleToUnits = 1.0f)
    CubeNode(size = Size(0.5f), position = Position(x = 1f))
    LightNode(type = LightManager.Type.POINT, intensity = 50_000f)
}
```

Nodes are added, updated, and removed automatically based on Compose state. When a state variable changes, only the affected nodes recompose.

### Scene Graph

Nodes can be nested in parent-child hierarchies. Child transforms are relative to their parent:

```kotlin
Node(position = Position(y = 1f)) {
    ModelNode(modelInstance = instance, position = Position(x = -1f))
    CubeNode(size = Size(0.1f), position = Position(x = 1f))
}
```

### Resource Loaders

SceneView provides memoized resource loaders that handle lifecycle automatically:

- **`rememberEngine()`** -- root Filament object, one per process
- **`rememberModelLoader(engine)`** -- loads glTF/GLB models
- **`rememberMaterialLoader(engine)`** -- creates PBR material instances
- **`rememberEnvironmentLoader(engine)`** -- loads HDR/KTX environments

Models are loaded asynchronously:

```kotlin
val model = rememberModelInstance(modelLoader, "models/helmet.glb")
// Returns null while loading -- always handle the null case
model?.let { ModelNode(modelInstance = it, scaleToUnits = 1f) }
```

### Available Node Types

**3D Nodes (SceneScope):**

- **ModelNode** -- 3D model from GLB/glTF files
- **CubeNode, SphereNode, CylinderNode, PlaneNode** -- procedural geometry
- **LightNode** -- directional, point, spot, focused spot, sun lights
- **ImageNode** -- images on 3D planes (from assets, resources, or Bitmap)
- **TextNode** -- camera-facing text labels
- **BillboardNode** -- always-facing-camera sprites
- **VideoNode** -- video playback on 3D surfaces
- **ViewNode** -- Jetpack Compose UI embedded in 3D space
- **LineNode, PathNode** -- line segments and polylines
- **ShapeNode** -- triangulated 2D polygons in 3D
- **MeshNode** -- custom vertex/index geometry
- **PhysicsNode** -- simple rigid-body physics
- **DynamicSkyNode** -- time-of-day sun cycle
- **ReflectionProbeNode** -- local IBL override
- **Node** -- empty pivot/group for hierarchy

**AR-Specific Nodes (ARSceneScope):**

- **AnchorNode** -- pin content to real-world positions
- **HitResultNode** -- surface cursor following detected planes
- **AugmentedImageNode** -- image tracking overlay
- **AugmentedFaceNode** -- face mesh tracking
- **CloudAnchorNode** -- cross-device persistent anchors
- **PoseNode** -- position at ARCore Pose
- **TrackableNode** -- generic trackable wrapper

### Threading Rule (Critical)

Filament JNI calls must run on the **main thread**. Never call `modelLoader.createModel*` or `materialLoader.*` from a background coroutine. Use `rememberModelInstance` in composables (it handles threading correctly) or `modelLoader.loadModelInstanceAsync` for imperative code.

---

## Supported 3D Formats

| Platform | Model Formats | Environment Formats |
|---|---|---|
| Android | GLB, glTF | HDR, KTX |
| iOS/macOS/visionOS | USDZ, Reality | IBL (built-in) |
| Web | GLB | KTX (IBL + skybox) |
