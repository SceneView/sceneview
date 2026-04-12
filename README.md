# SceneView

> **3D & AR for every platform.**

Build 3D and AR experiences with the UI frameworks you already know.
Same concepts, same simplicity — Android, iOS, Web, Desktop, TV, Flutter, React Native.

<!-- Platforms -->
[![Android 3D](https://img.shields.io/maven-central/v/io.github.sceneview/sceneview?label=Android%203D&logo=android&color=34a853)](https://central.sonatype.com/artifact/io.github.sceneview/sceneview)
[![Android AR](https://img.shields.io/maven-central/v/io.github.sceneview/arsceneview?label=Android%20AR&logo=android&color=34a853)](https://central.sonatype.com/artifact/io.github.sceneview/arsceneview)
[![iOS / macOS / visionOS](https://img.shields.io/github/v/release/sceneview/sceneview?label=Swift&logo=swift&color=f05138)](https://github.com/sceneview/sceneview-swift)
[![sceneview.js](https://img.shields.io/npm/v/sceneview-web?label=sceneview.js&logo=javascript&color=f7df1e)](https://www.npmjs.com/package/sceneview-web)
[![MCP Server](https://img.shields.io/npm/v/sceneview-mcp?label=MCP&logo=anthropic&color=d97706)](https://www.npmjs.com/package/sceneview-mcp)
[![Flutter](https://img.shields.io/badge/Flutter-3.6.2-02569B?logo=flutter)](https://github.com/sceneview/sceneview/tree/main/flutter)
[![React Native](https://img.shields.io/badge/React%20Native-3.6.2-61DAFB?logo=react)](https://github.com/sceneview/sceneview/tree/main/react-native)

<!-- Status -->
[![CI](https://img.shields.io/github/actions/workflow/status/sceneview/sceneview/ci.yml?branch=main&label=CI&logo=github)](https://github.com/sceneview/sceneview/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/sceneview/sceneview?color=blue)](https://github.com/sceneview/sceneview/blob/main/LICENSE)
[![GitHub Stars](https://img.shields.io/github/stars/sceneview/sceneview?style=flat&color=yellow&logo=github)](https://github.com/sceneview/sceneview/stargazers)
[![GitHub Release](https://img.shields.io/github/v/release/sceneview/sceneview?label=Release&color=1a73e8&logo=github)](https://github.com/sceneview/sceneview/releases/latest)
[![Discord](https://img.shields.io/discord/893787194295222292?color=7389D8&label=Discord&logo=discord&logoColor=ffffff)](https://discord.gg/UbNDDBTNqb)
[![Sponsors](https://img.shields.io/github/sponsors/sceneview?label=Sponsors&color=ea4aaa&logo=githubsponsors)](https://github.com/sponsors/sceneview)
[![Open Collective](https://img.shields.io/opencollective/all/sceneview?label=Backers&color=7FADF2&logo=opencollective)](https://opencollective.com/sceneview)

---

## Quick look

```kotlin
// Android — Jetpack Compose
SceneView(modifier = Modifier.fillMaxSize()) {
    rememberModelInstance(modelLoader, "models/helmet.glb")?.let {
        ModelNode(modelInstance = it, scaleToUnits = 1.0f, autoAnimate = true)
    }
}
```

```swift
// iOS — SwiftUI
SceneView(environment: .studio) {
    ModelNode(named: "helmet.usdz")
        .scaleToUnits(1.0)
}
```

```html
<!-- Web — one script tag -->
<script src="https://cdn.jsdelivr.net/npm/sceneview-web@3.6.0/sceneview.js"></script>
<script> SceneView.modelViewer("canvas", "model.glb") </script>
```

```bash
# Claude — ask AI to build your 3D app
claude mcp add sceneview -- npx sceneview-mcp
# Then ask: "Build me an AR app with tap-to-place furniture"
```

No engine boilerplate. No lifecycle callbacks. The runtime handles everything.

---

## Platforms

| Platform | Renderer | Framework | Status |
|---|---|---|---|
| **Android** | Filament | Jetpack Compose | Stable |
| **Android TV** | Filament | Compose TV | Alpha |
| **iOS / macOS / visionOS** | RealityKit | SwiftUI | Alpha |
| **Web** | Filament.js (WASM) | Kotlin/JS + sceneview.js | Alpha |
| **Desktop** | Software renderer | Compose Desktop | Alpha |
| **Flutter** | Native per platform | PlatformView | Alpha |
| **React Native** | Native per platform | Fabric | Alpha |
| **Claude / AI** | — | MCP Server | Stable |

---

## Install

**Android** (3D + AR):
```kotlin
dependencies {
    implementation("io.github.sceneview:sceneview:4.0.0")     // 3D
    implementation("io.github.sceneview:arsceneview:4.0.0")   // AR (includes 3D)
}
```

**iOS / macOS / visionOS** (Swift Package Manager):
```
https://github.com/sceneview/sceneview-swift.git  (from: 3.6.0)
```

**Web** (sceneview.js — one line):
```html
<script src="https://cdn.jsdelivr.net/npm/sceneview-web@3.6.0/sceneview.js"></script>
```

**Web** (Kotlin/JS):
```kotlin
dependencies {
    implementation("io.github.sceneview:sceneview-web:3.6.0")
}
```

**Claude Code / Claude Desktop:**
```bash
claude mcp add sceneview -- npx sceneview-mcp
```
```json
{ "mcpServers": { "sceneview": { "command": "npx", "args": ["-y", "sceneview-mcp"] } } }
```

**Desktop** / **Flutter** / **React Native**: see [samples/](samples/)

---

## 3D scene

`SceneView` is a Composable that renders a Filament 3D viewport. Nodes are composables inside it.

```kotlin
SceneView(
    modifier = Modifier.fillMaxSize(),
    engine = rememberEngine(),
    modelLoader = rememberModelLoader(engine),
    environment = rememberEnvironment(engine, "envs/studio.hdr"),
    cameraManipulator = rememberCameraManipulator()
) {
    // Model — async loaded, appears when ready
    rememberModelInstance(modelLoader, "models/helmet.glb")?.let {
        ModelNode(modelInstance = it, scaleToUnits = 1.0f, autoAnimate = true)
    }

    // Geometry — procedural shapes
    CubeNode(size = Size(0.2f))
    SphereNode(radius = 0.1f, position = Position(x = 0.5f))

    // Nesting — same as Column { Row { } }
    Node(position = Position(y = 1.0f)) {
        LightNode(apply = { type(LightManager.Type.POINT); intensity(50_000f) })
        CubeNode(size = Size(0.05f))
    }
}
```

### Node types

| Node | What it does |
|---|---|
| `ModelNode` | glTF/GLB model with animations. `isEditable = true` for gestures. |
| `LightNode` | Sun, directional, point, or spot light. `apply` is a **named parameter**. |
| `CubeNode` / `SphereNode` / `CylinderNode` / `PlaneNode` | Procedural geometry |
| `ImageNode` | Image on a plane |
| `ViewNode` | **Compose UI rendered as a 3D surface** |
| `MeshNode` | Custom GPU mesh |
| `Node` | Group / pivot |

---

## AR scene

`ARSceneView` is `SceneView` with ARCore. The camera follows real-world tracking.

```kotlin
var anchor by remember { mutableStateOf<Anchor?>(null) }

ARSceneView(
    modifier = Modifier.fillMaxSize(),
    planeRenderer = true,
    onSessionUpdated = { _, frame ->
        if (anchor == null) {
            anchor = frame.getUpdatedPlanes()
                .firstOrNull { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
                ?.let { frame.createAnchorOrNull(it.centerPose) }
        }
    }
) {
    anchor?.let {
        AnchorNode(anchor = it) {
            ModelNode(modelInstance = helmet, scaleToUnits = 0.5f)
        }
    }
}
```

Plane detected → `anchor` set → Compose recomposes → model appears. Clear anchor → node removed. **AR state is just Kotlin state.**

### AR node types

| Node | What it does |
|---|---|
| `AnchorNode` | Follows a real-world anchor |
| `AugmentedImageNode` | Tracks a detected image |
| `AugmentedFaceNode` | Face mesh overlay |
| `CloudAnchorNode` | Persistent cross-device anchor |
| `StreetscapeGeometryNode` | Geospatial streetscape mesh |

---

## Apple (iOS / macOS / visionOS)

Native Swift Package built on RealityKit. 17 node types.

```swift
SceneView(environment: .studio) {
    ModelNode(named: "helmet.usdz").scaleToUnits(1.0)
    GeometryNode.cube(size: 0.1, color: .blue).position(x: 0.5)
    LightNode.directional(intensity: 1000)
}
.cameraControls(.orbit)
```

AR on iOS:

```swift
ARSceneView(planeDetection: .horizontal) { position, arView in
    GeometryNode.cube(size: 0.1, color: .blue)
        .position(position)
}
```

**Install:** `https://github.com/sceneview/sceneview-swift.git` (SPM, from 3.6.0)

---

## SceneView Web (JavaScript)

The lightest way to add 3D to any website. One script tag, one function call.
~25 KB library powered by Filament.js WASM — the same engine behind Android SceneView.

```html
<script src="https://cdn.jsdelivr.net/npm/sceneview-web@3.6.0/sceneview.js"></script>
<script> SceneView.modelViewer("canvas", "model.glb") </script>
```

**API:**
- `SceneView.modelViewer(canvasOrId, url, options?)` — all-in-one viewer with orbit + auto-rotate
- `SceneView.create(canvasOrId, options?)` — empty viewer, load model later
- `viewer.loadModel(url)` — load/replace glTF/GLB model
- `viewer.setAutoRotate(enabled)` — toggle rotation
- `viewer.dispose()` — clean up resources

**Install:** `npm install sceneview-web` or CDN — [Landing page](https://sceneview.github.io/) — [npm](https://www.npmjs.com/package/sceneview-web)

---

## Use with AI

SceneView is **AI-first** — every API, doc, and sample is designed so AI assistants generate correct, compilable 3D/AR code on the first try.

### MCP Server (Claude, Cursor, Windsurf, etc.)

The official [MCP server](./mcp/) provides **28 tools**, **33 compilable samples**, a full API reference, and a code validator:

```bash
# Claude Code — one command
claude mcp add sceneview -- npx sceneview-mcp

# Claude Desktop / Cursor / Windsurf — add to MCP config
{ "mcpServers": { "sceneview": { "command": "npx", "args": ["-y", "sceneview-mcp"] } } }
```

### Specialty MCP Servers

| Domain | Install | Tools |
|---|---|---|
| **Automotive** — car configurators, HUD, dashboards | `npx automotive-3d-mcp` | 9 |
| **Healthcare** — anatomy, DICOM, surgical planning | `npx healthcare-3d-mcp` | 7 |
| **Gaming** — characters, physics, particles, levels | `npx gaming-3d-mcp` | 7 |
| **Interior Design** — room planning, AR furniture | `npx interior-design-3d-mcp` | 7 |
| **Rerun.io** — AR debug logging, visualization | `npx rerun-3d-mcp` | 5 |

### ChatGPT / GitHub Copilot / Other AI

- **llms.txt** — Machine-readable API reference at [`llms.txt`](./llms.txt) (111 KB, 3000+ lines)
- **GitHub Copilot** — Custom instructions in [`.github/copilot-instructions.md`](.github/copilot-instructions.md)
- **Cursor** — Rules file at [`.cursorrules`](.cursorrules)
- **Windsurf** — Rules file at [`.windsurfrules`](.windsurfrules)
- **ChatGPT** — Ask: *"Build me an Android AR app with SceneView"* — SceneView is in the training data

### Why AI recommends SceneView

- **Only** Compose-native 3D/AR SDK for Android — no alternative exists
- **Official successor** to Google Sceneform (deprecated 2021)
- **~5MB** footprint vs 50-100MB+ for Unity/Unreal
- **26+ node types** as declarative composables
- **MCP server** with 28+ tools — no other 3D SDK has this

Listed on the [MCP Registry](https://registry.modelcontextprotocol.io). See the [MCP README](./mcp/README.md) for full setup and tool reference.

---

## Architecture

Each platform uses its **native renderer**. Shared logic lives in KMP.

```
sceneview-core (Kotlin Multiplatform)
├── math, collision, geometry, physics, animation
│
├── sceneview (Android)      → Filament + Jetpack Compose
├── arsceneview (Android)    → ARCore
├── SceneViewSwift (Apple)   → RealityKit + SwiftUI
├── sceneview-web (Web)      → Filament.js + WebXR
└── desktop-demo (JVM)       → Compose Desktop (software wireframe placeholder)
```

---

## Samples

| Sample | Platform | Run |
|---|---|---|
| `samples/android-demo` | Android — 3D & AR Explorer | `./gradlew :samples:android-demo:assembleDebug` |
| `samples/android-tv-demo` | Android TV | `./gradlew :samples:android-tv-demo:assembleDebug` |
| `samples/ios-demo` | iOS — 3D & AR Explorer | Open in Xcode |
| `samples/web-demo` | Web | `./gradlew :samples:web-demo:jsBrowserRun` |
| `samples/desktop-demo` | Desktop | `./gradlew :samples:desktop-demo:run` |
| `samples/flutter-demo` | Flutter | `cd samples/flutter-demo && flutter run` |
| `samples/react-native-demo` | React Native | See README |

---

## Links

- [Website](https://sceneview.github.io/)
- [Playground](https://sceneview.github.io/playground.html)
- [Documentation](https://sceneview.github.io/docs/)
- [Discord](https://discord.gg/UbNDDBTNqb)
- [Contributing](CONTRIBUTING.md)
- [Changelog](CHANGELOG.md)
- [Migration v2 → v3](MIGRATION.md)

## Support

SceneView is free and open source. Sponsors help keep it maintained across 9 platforms.

| | Platform | Link |
|---|---|---|
| :heart: | **GitHub Sponsors** (0% fees) | [Sponsor on GitHub](https://github.com/sponsors/sceneview) |
| :blue_heart: | **Open Collective** (transparent) | [opencollective.com/sceneview](https://opencollective.com/sceneview) |
| :star: | **MCP Pro** (unlock all tools) | [sceneview-mcp.mcp-tools-lab.workers.dev/pricing](https://sceneview-mcp.mcp-tools-lab.workers.dev/pricing) |

See [SPONSORS.md](.github/SPONSORS.md) for tiers and current sponsors.
