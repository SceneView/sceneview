# QA Report - Android Demo App on Pixel 9
**Date:** 2026-04-13  
**Device:** Google Pixel 9 (API 36, Android 16)  
**Connection:** WiFi ADB (192.168.1.108:5555)  
**APK:** debug build from branch `main`  
**Tester:** Claude Code automated QA  

## Summary

| Category | Count |
|----------|-------|
| **PASS (perfect)** | 14 |
| **PASS with visual bugs** | 8 |
| **CRASH** | 1 (AR Tap to Place) |
| **Not tested** | 6 (AR demos - need manual AR testing) |
| **Total** | 31 |

## Detailed Results

### 3D Basics

| # | Demo | Status | Screenshot | Notes |
|---|------|--------|------------|-------|
| 01 | Model Viewer | ⚠️ BUG | 01_Model_Viewer.png | **WAY too zoomed** — only see close-up detail of helmet bolt. scaleToUnits=1.0f + default camera orbit = extreme close-up. Need scaleToUnits=0.3f or camera distance adjustment |
| 02 | Geometry Primitives | ⚠️ BUG | 02_Geometry_Primitives.png | **Plane dominates scene** — yellow plane fills background, sphere is huge, cube barely visible on left edge. Shapes still poorly spaced despite fix. Need smaller sizes + tighter layout |
| 03 | Animation | ✅ PASS | 03_Animation.png | Dragon flying, play/pause, speed slider, loop/once chips. Model is small but correct — animated_dragon.glb loads fine |
| 04 | Multi Model | ⚠️ BUG | 04_Multi_Model.png | **Only Lantern base visible** — Avocado and Helmet are off-screen. Spacing still too wide. positions = [-0.3, 0, 0.3] but models are tiny so gaps look huge |

### Lighting & Environment

| # | Demo | Status | Screenshot | Notes |
|---|------|--------|------------|-------|
| 05 | Lighting | ✅ PASS | 05_Lighting.png | Helmet beautifully rendered. Directional/Point/Spot chips, intensity slider (100000), 4 color circles |
| 06 | Dynamic Sky | ✅ PASS | 06_Dynamic_Sky.png | Helmet with dynamic lighting, Time of Day + Turbidity sliders |
| 07 | Fog | ✅ PASS | 07_Fog.png | Helmet with fog toggle, density slider, 4 color presets |
| 08 | Environment Gallery | ✅ PASS | 08_Environment_Gallery.png | Helmet with 6 HDR chips, instant switching |

### Content & Interaction

| # | Demo | Status | Screenshot | Notes |
|---|------|--------|------------|-------|
| 09 | Text Labels | ✅ PASS | 09_Text_Labels.png | "SceneView 4.0" yellow bold, text input field, font size slider. Perfect |
| 10 | Lines & Paths | ✅ PASS | 10_Lines_Paths.png | Red line + green/yellow curved path, Line/Path chips, Path Points slider |
| 11 | Image Node | ⚠️ BUG | 11_Image.png | **Blue circle instead of image** — shows solid cornflower blue disc. The texture `textures/sceneview_logo.png` likely doesn't exist in assets or ImageNode doesn't apply it correctly. Scale slider works |
| 12 | Billboard | ✅ PASS | 12_Billboard.png | Green "Billboard" quad + blue "Fixed Image" quad, both face camera, visibility chips |
| 13 | Video | ⚠️ BUG | 13_Video.png | **Blue rectangle instead of video** — shows solid cornflower blue. `videos/sample.mp4` likely doesn't exist in assets. Playback pause button is there |
| 14 | Camera Controls | ✅ PASS | 14_Camera_Controls.png | Helmet centered, Orbit/Free Flight/Map chips, Reset Camera button |
| 15 | Gesture Editing | ⚠️ BUG | 15_Gesture_Editing.png | **Avocado too zoomed** — only see the pit/top. scaleToUnits too large for this model. Editable toggle + Reset Position work |
| 16 | Collision | ✅ PASS | 16_Collision.png | Green cube centered, "Tap a shape to highlight it", Reset Colors button |

### Advanced

| # | Demo | Status | Screenshot | Notes |
|---|------|--------|------------|-------|
| 17 | ViewNode | ✅ PASS | 17_ViewNode.png | "Tapped 0 times" text, purple "Tap me" button in 3D space, Visible toggle. Compose UI in 3D scene works! |
| 18 | Physics | ⚠️ BUG | 18_Physics.png | **Scene appears empty** — gray gradient background, no visible spheres or ground plane. "Spheres: 1", Drop/Reset buttons present. PhysicsNode may need an environment or the sphere is falling out of view |
| 19 | Post Processing | ✅ PASS | 19_Post_Processing.png | Helmet beautifully rendered, SSAO/MSAA/FXAA/Dithering toggles. Best-looking demo |
| 20 | Custom Mesh | ✅ PASS | 20_Custom_Mesh.png | Cyan molecule structure with gray bonds, auto-rotate toggle, scale slider |
| 21 | Shape | ✅ PASS | 21_Shape.png | Cyan triangle on black background, Triangle/Star/Hexagon chips |
| 22 | Reflection Probes | ✅ PASS | 22_Reflection_Probes.png | Helmet with reflection controls, Probe Radius (3.0m) + Y Position (0.5) sliders |
| 23 | Secondary Camera | ✅ PASS | 23_Secondary_Camera.png | Helmet main view + PiP thumbnail top-right, Top/Side/Front/Corner chips. Excellent demo! |
| 24 | Debug Overlay | ✅ PASS | 24_Debug_Overlay.png | Helmet + green FPS (119.3) / Frame (8.4ms) / Nodes (0) overlay, Show Overlay toggle |

### AR (requires ARCore + camera)

| # | Demo | Status | Screenshot | Notes |
|---|------|--------|------------|-------|
| 25 | Tap to Place | ❌ CRASH | 25_Tap_to_Place_CRASH.png | App crashed — phone showed GitHub app behind. Need to investigate crash cause |
| 26-31 | Other AR demos | ⏸ NOT TESTED | — | Need manual AR testing with camera |

## Bugs to Fix (Priority Order)

### P0 — Blockers
1. **AR Tap to Place CRASH** — app terminates. Investigate logcat for root cause
2. **Image Node — no texture** — shows blue disc. Need `textures/sceneview_logo.png` in assets or fix texture loading
3. **Video — no video** — shows blue rectangle. Need `videos/sample.mp4` in assets or fix video loading

### P1 — Visual Quality
4. **Model Viewer zoom** — scaleToUnits=1.0f is too large for DamagedHelmet. Reduce to 0.3-0.5f or set initial camera distance
5. **Geometry layout** — plane is enormous, shapes poorly distributed. Reduce all sizes, remove plane or make it ground-level
6. **Multi Model spacing** — models too far apart, only 1 visible. Reduce positions to [-0.15, 0, 0.15]
7. **Gesture Editing zoom** — avocado scaleToUnits too large. Reduce to 0.3f
8. **Physics empty scene** — spheres not visible. Check if environment/ground is loading, camera position, object scale

### P2 — Polish
9. **Animation dragon** — model is small and far away. Camera could be closer
10. **All helmet demos** — post-processing demo has perfect zoom but others are too close. Standardize scaleToUnits across all helmet demos

## What Works Great
- **Lighting controls** — all 3 light types + intensity + color perfectly functional
- **Dynamic Sky** — smooth slider interaction, real-time HDR update
- **Text Labels** — live text editing in 3D, font size slider responsive
- **ViewNode** — Compose UI embedded in 3D space is impressive
- **Secondary Camera** — PiP with 4 angle presets, excellent showcase
- **Debug Overlay** — 119 FPS on Pixel 9, real metrics display
- **Post Processing** — best-looking demo, toggles for SSAO/MSAA/FXAA/Dithering
- **Custom Mesh** — molecule structure auto-rotation is visually appealing
- **Shape** — clean 2D shape extrusion with multiple presets
