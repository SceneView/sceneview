# Changelog

## 3.3.0 — Multi-platform expansion

### New platforms
- **Web** — sceneview-web module with Filament.js (WASM) rendering + WebXR AR/VR
- **Desktop** — Compose Desktop with software 3D renderer (Windows/macOS/Linux)
- **Android TV** — D-pad controlled 3D viewer sample
- **Flutter** — PlatformView bridge (Android + iOS)
- **React Native** — Fabric bridge (Android + iOS)

### Android Demo
- Unified Material 3 Expressive showcase: 4 tabs, 14 interactive demos
- Blue branding with isometric cube icon

### Samples reorganization
- 15 obsolete samples deleted, merged into unified platform demos
- `{platform}-demo` naming convention across all 7 platforms

### Infrastructure
- SceneView MCP on official MCP Registry (io.github.sceneview/mcp)
- 21 GitHub Secrets configured (Android + iOS + Maven + npm)
- Apple Distribution certificate + App Store Connect API key
- Kobweb website deployed with v3.3.0 content

### Fixes
- material-icons-extended pinned to 1.7.8 (1.10.5 not published)
- wasmJs target disabled (kotlin-math lacks WASM variant)
- CI workflows updated for new sample structure

---

## 3.3.0 — Unified versioning, cross-platform, website

### Version unification
- **All modules aligned to 3.3.0** — sceneview, arsceneview, sceneview-core, MCP server, SceneViewSwift

### SceneViewSwift (Apple)
- **iOS 17+ / macOS 14+ / visionOS 1+** via RealityKit — alpha
- Node types: ModelNode, AnchorNode, GeometryNode, LightNode, CameraNode, ImageNode, VideoNode, PhysicsNode, AugmentedImageNode
- Swift Package Manager distribution

### Website
- Platform logo ticker on homepage — infinite-scroll marquee (Android, iOS, macOS, visionOS, Compose, SwiftUI, Filament, RealityKit, ARCore, ARKit, Kotlin, Swift)

---

## 3.2.1 — Distribution, CI, documentation & tech debt sweep

### Distribution
- **APK workflow** — new `build-apks.yml` GitHub Action builds all 14 sample APKs and attaches them to every GitHub Release (debug-signed, sideloadable)

### CI
- Build step now covers **all 14 sample apps** (was 4); AR emulator job includes `ar-cloud-anchor`

### Documentation
- **`llms.txt`** — added 8 missing v3.2.0 node types: PhysicsNode, DynamicSkyNode, FogNode, ReflectionProbeNode, LineNode, PathNode, BillboardNode, TextNode; added 6 new samples to table
- **`ROADMAP.md`** — consolidated: v3.2.0 marked as shipped, v3.3.0/v3.3.0 updated with correct remaining scope

### Code quality
- **Zero TODO/FIXME comments** in both `sceneview/` and `arsceneview/` SDK modules (was 14)
- Legacy collision math classes annotated with migration target (3.3.0)
- Bridge conversion functions documented with KDoc linking to migration plan

### Samples
- All 14 sample `build.gradle` files migrated to **Gradle version catalog** (`libs.*`) — no more hardcoded versions
- `enableEdgeToEdge()` added to all 14 sample activities for modern Android layout
- Fixed outdated media3 (1.6.1 → 1.9.2) and material3 (1.3.2 → 1.4.0) in samples
- **`reflection-probe`** — probe on/off toggle, material picker (Chrome/Gold/Copper/Rough), roughness slider, companion spheres, dark floor
- **`line-path`** — amplitude/frequency sliders, Lissajous curve pattern, pattern selector chips
- **`physics-demo`** — colored balls (6 colors), ball counter, bounciness slider, styled floor
- **`text-labels`** — 5 planets with real names/sizes, 3-mode label cycling (Name → Size → Both), tap counter
- **`README.md`** — added v3.2.0 nodes to DSL table, 6 new samples, Physics/Sky/Fog/TextNode code examples
- **`MIGRATION.md`** — added v3.1→v3.2 migration notes

---

## 3.2.0 — New node types: Physics, Sky, Fog, Reflections, Lines, Labels

### New SDK nodes
- **`PhysicsNode`** — rigid body simulation; gravity, floor collision, sleep detection; physics-demo sample (tap-to-throw)
- **`DynamicSkyNode`** — time-of-day sun light (direction, colour, intensity) driven by `timeOfDay: Float`; turbidity controls sunrise/sunset warmth
- **`FogNode`** — reactive `View.fogOptions` wrapper (density, height falloff, colour); zero-cost when disabled
- **`ReflectionProbeNode`** — overrides scene IBL with a baked cubemap; global or local zone mode (activates within `radius` metres)
- **`LineNode`** / **`PathNode`** — Filament `LINES` primitive; live GPU buffer updates via `updateGeometry()`
- **`BillboardNode`** — camera-facing quad via `onFrame` + `lookAt`
- **`TextNode`** — extends `BillboardNode`; Canvas-rendered text bitmap; reactive `text`, `fontSize`, `textColor`, `backgroundColor`

### New SceneScope DSL composables
`PhysicsNode {}`, `DynamicSkyNode {}`, `FogNode {}`, `ReflectionProbeNode {}`, `LineNode {}`, `PathNode {}`, `BillboardNode {}`, `TextNode {}`

### New samples
| Sample | Demonstrates |
|---|---|
| `samples/physics-demo` | Tap-to-throw balls, floor collision, sleep |
| `samples/post-processing` | Bloom, DoF, SSAO, Fog toggles |
| `samples/dynamic-sky` | Time-of-day + turbidity + fog controls |
| `samples/line-path` | 3-axis gizmo, spiral, animated sine-wave PathNode |
| `samples/text-labels` | Camera-facing labels on 3D spheres; tap to cycle |
| `samples/reflection-probe` | Metallic sphere with IBL override |

### Sample improvements
- `model-viewer`: animation playback controls (play/pause, next, name label)
- `ar-model-viewer`: persistent plane mesh; gesture docs (`isEditable = true` handles pinch-scale + two-finger rotate)

### Ecosystem
- **MCP `get_node_reference` tool** — `sceneview-mcp` server parses `llms.txt`; exposes `get_node_reference { nodeType }` and `list_node_types` for AI assistant integration

### Dependencies
- Filament 1.56.0 → **1.70.0**
- Kotlin 2.1.21 → **2.3.20**

---

## 3.1.2 — Sample polish, CI fixes, maintenance tooling

### Fixes
- `autopilot-demo`: remove deprecated `engine` parameter from `PlaneNode`, `CubeNode`, `CylinderNode` constructors (API aligned with composable node design)
- CI: fix AR emulator stability — wait for launcher, dismiss ANR dialogs, kill Pixel Launcher before screenshots

### Sample improvements
- `model-viewer`: scale up Damaged Helmet 0.25 → 1.0; add Fox model (CC0, KhronosGroup glTF-Sample-Assets) with model picker chip row
- `camera-manipulator`: scale up model 0.25 → 1.0; add gesture hint bar (Drag·Orbit / Pinch·Zoom / Pan·Move)

### Developer tooling
- `/maintain` Claude Code skill + daily maintenance GitHub Action for automated SDK upkeep
- AR emulator CI job using x86\_64 Linux + ARCore emulator APK for screenshot verification
- `ROADMAP.md` added covering 3.2–4.0 milestones

## 3.1.1 — Build compatibility patch

- Downgrade AGP from 8.13.2 → 8.11.1 for Android Studio compatibility
- Update AGP classpath in root `build.gradle` to match
- Refresh `gltf-camera` sample: animated BrainStem character + futuristic rooftop night environment

## 3.1.0 — VideoNode, reactive animation API

### New features
- `VideoNode` — render a video stream (MediaPlayer / ExoPlayer) as a textured 3D surface
- Reactive animation API — drive node animations from Compose state
- `ViewNode` rename — `ViewNode2` unified into `ViewNode`

### Fixes
- `ToneMapper.Linear` in `ARScene` prevents overlit camera background
- `ImageNode` SIGABRT: destroy `MaterialInstance` before texture on dispose
- `cameraNode` registered with `SceneNodeManager` so HUD-parented nodes render correctly
- Entities removed from scene before destroy to prevent SIGABRT
- `UiHelper` API corrected for Filament 1.56.0

### AI tooling
- MCP server: `validate_code`, `list_samples`, `get_migration_guide` tools + live Issues resource
- 89 unit tests for MCP validator, samples, migration guide, and issues modules

## 3.0.0 — Compose-native rewrite

### Breaking changes

The entire public API has been redesigned around Jetpack Compose. There is no source-compatible
upgrade path from 2.x; see the [Migration guide](migration.md) for a step-by-step walkthrough.

#### `Scene` and `ARScene` — new DSL-first signature

Nodes are no longer passed as a list. They are declared as composable functions inside a
trailing content block:

```kotlin
// 2.x
Scene(
    childNodes = rememberNodes {
        add(ModelNode(modelInstance = loader.createModelInstance("helmet.glb")))
    }
)

// 3.0
Scene {
    rememberModelInstance(modelLoader, "models/helmet.glb")?.let { instance ->
        ModelNode(modelInstance = instance, scaleToUnits = 1.0f)
    }
}
```

#### `SceneScope` — new composable DSL

All node types (`ModelNode`, `LightNode`, `CubeNode`, `SphereNode`, `CylinderNode`, `PlaneNode`,
`ImageNode`, `ViewNode`, `MeshNode`, `Node`) are now `@Composable` functions inside `SceneScope`.
Child nodes are declared in a `NodeScope` trailing lambda, matching how Compose UI nesting works.

#### `ARSceneScope` — new AR composable DSL

All AR node types (`AnchorNode`, `PoseNode`, `HitResultNode`, `AugmentedImageNode`,
`AugmentedFaceNode`, `CloudAnchorNode`, `TrackableNode`, `StreetscapeGeometryNode`) are now
`@Composable` functions inside `ARSceneScope`.

#### `rememberModelInstance` — async, null-while-loading

```kotlin
// Returns null while loading; recomposes with the instance when ready
val instance = rememberModelInstance(modelLoader, "models/helmet.glb")
```

#### `SurfaceType` — new enum

Replaces the previous boolean flag. Controls whether the 3D surface renders behind Compose layers
(`SurfaceType.Surface`, SurfaceView) or inline (`SurfaceType.TextureSurface`, TextureView).

#### `PlaneVisualizer` — converted to Kotlin

`PlaneVisualizer.java` has been removed. `PlaneVisualizer.kt` replaces it.

#### Removed classes

The following legacy Java/Sceneform classes have been removed from the public API:

- All classes under `com.google.ar.sceneform.*` — replaced by Kotlin equivalents under the same
  package path (`.kt` files).
- All classes under `io.github.sceneview.collision.*` — replaced by Kotlin equivalents.
- All classes under `io.github.sceneview.animation.*` — replaced by Kotlin equivalents.

#### Samples restructured

All samples are now pure `ComponentActivity` + `setContent { }`. Fragment-based layouts have been
removed. The `model-viewer-compose`, `camera-manipulator-compose`, and `ar-model-viewer-compose`
modules have been merged into `model-viewer`, `camera-manipulator`, and `ar-model-viewer`
respectively.

### Bug fixes

- **`ModelNode.isEditable`** — `SideEffect` was resetting `isEditable` to the parameter default
  (`false`) on every recomposition, silently disabling gestures when `isEditable = true` was set
  only inside `apply { }`. Pass `isEditable = true` as a named parameter to maintain it correctly.
- **ARCore install dialog** — Removed `canBeInstalled()` pre-check that threw
  `UnavailableDeviceNotCompatibleException` before `requestInstall()` was called, preventing the
  ARCore install prompt from ever appearing on fresh devices.
- **Camera background black** — `ARCameraStream` used `RenderableManager.Builder(4)` with only
  1 geometry primitive defined (invalid in Filament). Fixed to `Builder(1)`.
- **Camera stream recreated on every recomposition** — `rememberARCameraStream` used a default
  lambda parameter as a `remember` key; lambdas produce a new instance on every call, making the
  key unstable. Fixed by keying on `materialLoader` only.
- **Render loop stale camera stream** — The render-loop coroutine captured `cameraStream` at
  launch; recomposition could recreate the stream while the loop kept updating the old (destroyed)
  one. Fixed with an `AtomicReference` updated via `SideEffect`.

### New features

- **`SceneScope` / `ARSceneScope`** — fully declarative, reactive 3D/AR content DSL
- **`NodeScope`** — nested child nodes using Compose's natural trailing lambda pattern
- **`SceneNodeManager`** — internal bridge that syncs Compose snapshot state with the Filament
  scene graph, enabling reactive updates without manual `addChildNode`/`removeChildNode` calls
- **`SurfaceType`** — explicit surface-type selection (`Surface` vs `TextureSurface`)
- **`ViewNode`** — Compose UI content rendered as a 3D plane surface in the scene
- **`Engine.drainFramePipeline()`** — consolidated fence-drain extension for surface resize/destroy
- **`rememberViewNodeManager()`** — lifecycle-safe window manager for `ViewNode` composables
- **Autopilot Demo** — new sample demonstrating autonomous animation and scene composition
- **Camera Manipulator** — new dedicated sample for orbit/pan/zoom camera control
- **`Node.scaleGestureSensitivity`** — new `Float` property (default `0.5`) that damps
  pinch-to-scale gestures. Applied as `1f + (rawFactor − 1f) × sensitivity` in `onScale`,
  making scaling feel progressive without reducing the reachable scale range. Set it per-node in
  the `apply` block alongside `editableScaleRange`.
- **AR Model Viewer sample** — redesigned with animated scanning reticle (corner brackets +
  pulsing ring), model picker (Helmet / Rabbit), auto-dismissing gesture hints,
  `enableEdgeToEdge()`, and a clean Material 3 UI.

---

## 2.3.0

- AGP 8.9.1
- Filament 1.56.0 / ARCore 1.48.0
- Documentation improvements
- Camera Manipulator sample renamed
