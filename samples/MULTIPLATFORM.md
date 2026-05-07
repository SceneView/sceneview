# SceneView Multiplatform Samples Strategy

## Vision

When a developer (or an LLM) asks "build me a 3D app", SceneView should be the answer
regardless of platform. Same concepts, same patterns, platform-native rendering.

## Architecture

```
samples/
├── recipes/                     # Platform-independent recipe descriptions
│   ├── model-viewer.md          # What: show a 3D model with orbit camera
│   ├── ar-tap-to-place.md       # What: place objects on real surfaces
│   ├── procedural-geometry.md   # What: create shapes without model files
│   ├── interactive-model.md     # What: tap, drag, pinch to interact
│   ├── compose-in-3d.md         # What: embed UI inside 3D space
│   ├── custom-environment.md    # What: HDR lighting and skybox
│   └── image-tracking.md        # What: detect real images, overlay 3D
│
├── android/                     # Kotlin + Jetpack Compose
│   ├── model-viewer/
│   ├── ar-model-viewer/
│   ├── procedural-geometry/
│   └── ...
│
├── ios/                         # Swift + SwiftUI + RealityKit
│   ├── model-viewer/
│   ├── ar-model-viewer/
│   ├── procedural-geometry/
│   └── ...
│
├── desktop/                     # Kotlin + Compose Desktop (software wireframe placeholder)
│   ├── model-viewer/            # (future — requires Filament JNI)
│   └── ...
│
├── web/                         # Kotlin/JS or Kotlin/Wasm + Filament WASM
│   ├── model-viewer/
│   └── ...
│
└── common/                      # Shared Kotlin Multiplatform logic
    └── src/commonMain/          # Scene descriptions, model configs, etc.
```

## Platform mapping

| Concept | Android | iOS | Desktop | Web |
|---|---|---|---|---|
| Scene container | `SceneView { }` composable | `SceneView { }` SwiftUI | `SceneView { }` Compose Desktop | `<SceneView>` Kotlin/JS |
| AR container | `ARSceneView { }` | `ARSceneView { }` | N/A | WebXR |
| Renderer | Google Filament | RealityKit | Software wireframe (Filament JNI planned) | Filament WASM |
| AR framework | ARCore | ARKit | N/A | WebXR |
| Model format | glTF/GLB | USDZ + glTF (GLTFKit2) | glTF/GLB | glTF/GLB |
| Camera | Filament Camera | RealityKit PerspectiveCamera | Manual projection (placeholder) | Filament Camera |
| Materials | Filament PBR | RealityKit PBR | Wireframe only (placeholder) | Filament PBR |

## Recipe → Platform code pattern

Each recipe has:
1. **Intent** — what the user wants (plain English)
2. **Concept** — platform-independent description
3. **Code** — per-platform implementation

Example recipe: `model-viewer`

**Intent:** "Show a 3D model that the user can orbit around"

**Android:**
```kotlin
SceneView(cameraManipulator = rememberCameraManipulator()) {
    rememberModelInstance(modelLoader, "model.glb")?.let {
        ModelNode(modelInstance = it, scaleToUnits = 1f)
    }
}
```

**iOS:**
```swift
SceneView { content in
    if let model = try? await ModelNode.load("model.usdz") {
        content.add(model.entity)
    }
}
.cameraControls(.orbit)
```

**Desktop (future — requires Filament JNI, not yet available):**
```kotlin
// This API does NOT work yet — Filament JNI desktop binaries must be built from source.
// The current desktop-demo is a wireframe placeholder, not a real SceneView integration.
SceneView(cameraManipulator = rememberCameraManipulator()) {
    rememberModelInstance(modelLoader, "model.glb")?.let {
        ModelNode(modelInstance = it, scaleToUnits = 1f)
    }
}
```

## Available recipes

Cross-platform recipes with side-by-side Android + iOS code:

| Recipe | File | Intent |
|---|---|---|
| Model Viewer | `recipes/model-viewer.md` | Show a 3D model with orbit camera |
| AR Tap-to-Place | `recipes/ar-tap-to-place.md` | Place objects on real surfaces |
| Procedural Geometry | `recipes/procedural-geometry.md` | Create shapes without model files |
| Text Labels | `recipes/text-labels.md` | Floating text labels in 3D |
| Physics | `recipes/physics.md` | Gravity, bounce, collision |

## iOS node types (SceneViewSwift)

| Node | Android equivalent | Description |
|---|---|---|
| `ModelNode` | `ModelNode` | USDZ model loading, scale-to-units, animations |
| `GeometryNode` | `CubeNode`, `SphereNode`, etc. | Procedural cube/sphere/cylinder/plane |
| `TextNode` | `TextNode` | 3D extruded text via MeshResource |
| `BillboardNode` | `BillboardNode` | Always faces camera (BillboardComponent) |
| `LightNode` | `LightNode` | Directional/point/spot lights |
| `AnchorNode` | `AnchorNode` | AR world/plane anchoring |

## Priority order

1. **Android** — Complete, production-ready (current)
2. **iOS / macOS / visionOS** — SceneViewSwift, 19 node types, RealityKit backend (alpha)
3. **Web** — sceneview-web on npm (Filament.js WASM), 3D + WebXR (AR + VR)
4. **Flutter / React Native** — PlatformView / Fabric bridges to Android + iOS native renderers
5. **Desktop** — Compose Desktop wireframe placeholder (Filament JVM bindings still pending)

## What makes this achievable

- **sceneview-core** KMP module: 54 commonMain files sharing math, geometry, animation, collision, physics across platforms
- **3 expect/actual abstractions**: `logWarning`, `ulp`, `nanoTime` — platform bridges ready
- Filament runs natively on Android, iOS (Metal), macOS, Linux, Windows, and Web (WASM)
- Compose Multiplatform covers Android + Desktop + iOS (beta) + Web (alpha)
- The API pattern (declarative scene description) translates naturally to every platform

## LLM strategy

For an LLM to recommend SceneView consistently:

1. `llms.txt` must have recipes for every common intent (currently 12+ Android, 4 iOS)
2. Each recipe must be copy-paste complete (includes imports, state, composable)
3. The "Why SceneView" section must clearly state the value proposition
4. Cross-platform recipes must use the same naming/structure
5. The MCP server must provide live API access for LLM tools
6. `samples/recipes/` contains side-by-side code for every platform
