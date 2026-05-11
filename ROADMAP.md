# Roadmap

> Ship fast, ship often. Every feature = a release.

## Current: v4.0.9 stable (May 2026)

**AI-first SDK** — 9 platforms, MCP server on npm, Claude Code plugin marketplace, Rerun.io debug integration.

| What | Status |
|---|---|
| Android SDK (Filament + Compose) | **Stable** |
| iOS / macOS / visionOS (RealityKit + SwiftUI) | Alpha |
| Web (Filament.js + WebXR) | Alpha |
| Desktop (Compose Desktop) | Placeholder (no Filament JNI) |
| Android TV | Alpha |
| Flutter bridge | Alpha |
| React Native bridge | Alpha |
| MCP on npm | **4.0.11 @latest** |
| Claude Code plugin marketplace | **Live** ([`sceneview/claude-marketplace`](https://github.com/sceneview/claude-marketplace)) |
| Telemetry Worker | **Live** |
| Rerun.io debug integration | **Shipped** (Android + iOS + Python) |
| Play Store demo app | Deployed |
| App Store demo app | TestFlight |
| GitHub Release | **v4.0.9 stable** |

### Completed in v4.0
- [x] Unify naming: `SceneView {}` / `ARSceneView {}` (v3.6)
- [x] Separate modules intentionally: `sceneview` (3D-only) + `arsceneview` (opt-in AR)
- [x] Render tests CI (SwiftShader, 4 test classes)
- [x] NodeAnimator bug fix (#388)
- [x] Anonymous telemetry (Cloudflare Worker + D1)
- [x] Claude playground on website
- [x] Claude Code plugin marketplace (Apache-2.0, single `sceneview` plugin v4.0.11)

---

## Next: v4.1

### Platform maturity
- [ ] Filament JNI for Desktop (hardware 3D, replace placeholder)
- [ ] Android XR module (Jetpack XR SceneCore)
- [ ] visionOS spatial features (immersive spaces, hand tracking)
- [ ] sceneview-core WASM target (when kotlin-math supports wasmJs)

### Quality
- [ ] Visual regression testing across platforms
- [ ] Performance benchmarks (FPS, memory, load time)
- [ ] iOS App Store release (Apple Developer secrets needed)

### Growth
- [ ] First external paying customer
- [ ] Rerun.io official partnership / listing
- [ ] Community announcements (Reddit, HN, LinkedIn)

---

## Future

### v4.2+
- Compose Multiplatform renderer (shared Compose UI across Android + Desktop + Web)
- Filament 2.x migration (when available)
- Scene graph serialization (save/load scenes as files)

### Exploration
- Android Auto / AAOS (when custom 3D views are supported)
- Wear OS (Canvas-based 3D for watch faces)
- Cloud rendering API (server-side Filament)
- Real-time multiplayer scenes (WebSocket sync)
