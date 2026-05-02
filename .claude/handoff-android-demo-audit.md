# Handoff — Android Demo Audit (worktree `tender-haibt-6062c7`)

**Date saved:** 2026-05-02
**Reason:** session crashed repeatedly on image dimension limit (>2000px). Need to resume in fresh session.

## Current state

- **Branch:** `claude/tender-haibt-6062c7` (90 commits ahead of `main`, working tree CLEAN)
- **Worktree path:** `/Users/thomasgorisse/Projects/sceneview/.claude/worktrees/tender-haibt-6062c7/`
- **Cannot push to origin** — GitHub account `thomasgorisse` is SUSPENDED. Stay local. Continue committing locally; do NOT attempt `git push`.
- **Compile status:** GREEN — last verified after the auto-rotate refactor batch.
- **Pre-push gate:** not run yet for this batch — must run before any handoff close.

## What was done in this session (rolled-up)

90 commits on top of main covering Android demo audit fixes. Highlights:

| Area | Commits | Status |
|---|---|---|
| Camera framing dolly-in | `5e36257f` | 6 demos closer (Billboard, Collision, CustomMesh, Geometry, Shape, Text) |
| Camera-orbits-not-model | `dfc241d5`, `8c0dd693` | EnvironmentDemo, LightingDemo, ModelViewer, ReflectionProbes, SecondaryCamera, Fog, PostProcessing — model is anchor, camera orbits |
| `rememberPausableHeroYaw` | `6acc5b99`, `dfc241d5` | Replaced 5 non-pausable auto-rotate sites; first user gesture freezes orbit |
| `PostProcessingDemo` SideEffect | `287515f8` | View writes moved out of composition body |
| `AnimationDemo` controls dead | `6c52c4a5` | Play/Pause/Speed/Loop now wired to `ModelNode` props |
| `AR Face/Pose framing` | `35e5990d` | Face Mesh visible + Pose cubes back in viewport |
| `ARPose Pose object cache` | `f6ad3d5c` | Cache Pose across slider frames — no GC churn |
| Cloud Anchor + Augmented Image perf | `e6fe46a8` | Host/resolve flow + perf fix |
| `ReflectionProbes` toggle + `CustomMesh` auto-pause | `d56207b6` | Bug fixes |
| `ViewNode` 3D z-fight | `24ceeebc`, prior `6acc5b99` | Cards z-offset + parent rotate node — real 3D card flip |
| `CameraControls` IBL | `89867e6c` | Helmet was black, now full PBR |
| `Geometry Plane` Float3 bug | `6acc5b99` | `Float3(0.32, 0.32, 1f)` was a parallelogram — fixed |
| `PhysicsDemo` floor wall | `cfb4220c` | `Size(x,y)` left z=0 → vertical wall — fixed to `Size(x=1.6f, y=0f, z=1.6f)` |
| `LinesPaths` 3D helix | `cfb4220c` | flat ring → 2-turn helix |
| `Physics Drop 10` | `88a48e2a` | New button + 5×N grid spread |
| `MultiModel` rewrite | `1c4909ea` | 4 models on circular carousel |

**27 demos modified** total (out of 31 in `samples/android-demo/src/main/java/io/github/sceneview/demo/demos/`).

## Testing log this session (live screen-record verifications)

Verified OK on Pixel_7a AVD:

- ✅ **PhysicsDemo** — true horizontal floor + balls drop and bounce
- ✅ **ViewNodeDemo** — 3D card flip authentic (vn5 face, vn6 edge-on)
- ✅ **CameraControlsDemo** — helmet PBR full (was black)
- ✅ **LightingDemo** — PBR + pause-on-tap (frames 2-3 identical after tap)
- ✅ **FogDemo** — render OK
- ✅ **EnvironmentDemo** — HDR Studio reflections
- ✅ **DynamicSkyDemo** — render OK
- ✅ **PostProcessingDemo** — render OK after Filament timing fix
- ✅ **BillboardDemo** — render OK
- ✅ **ShapeDemo** — render OK
- ✅ **TextDemo** — 3 labels visible

🚧 **LinesPathsDemo** — testing crashed here (image >2000px API error). Visual not yet validated this session.

## Remaining demos to audit (live screen-record)

Have NOT been re-verified after the latest commits:

1. **LinesPathsDemo** — was being tested when image-limit crash hit. Re-run.
2. **GeometryDemo** — Plane fix committed but full demo not re-recorded.
3. **ImageDemo** — not visited this session.
4. **MultiModelDemo** — not re-verified after auto-rotate refactor.
5. **GestureEditingDemo** — not visited this session.
6. **CollisionDemo** — dolly-in committed, not re-verified.
7. **DebugOverlayDemo** — not visited this session.
8. **SecondaryCameraDemo** — orbit refactor committed, not re-verified.
9. **VideoDemo** — not visited this session.
10. **ARPlacementDemo** — not visited this session.
11. **ARStreetscapeDemo** — not visited this session.
12. **ARRerunDemo** — not visited this session.
13. **ModelViewerDemo** — pausable yaw committed, not re-verified after recent batch.
14. **AnimationDemo** — controls fix committed, not re-verified.
15. **MultiModelDemo** — same.

## Critical operational rules for next session

### 🚫 IMAGE DIMENSION LIMIT — DO NOT BREAK
Repeated cause of crash this session. **NEVER** Read screenshots/grids larger than 2000×2000 px.

Workflow:
- `adb screenrecord --time-limit 12 /sdcard/x.mp4`, pull, then convert with `ffmpeg -i x.mp4 -vf "fps=1,scale=400:-1" frame_%02d.jpg`
- For montage: `ffmpeg -i frame_%02d.jpg -vf "tile=4x3:padding=4" grid.jpg` — verify `identify -format "%wx%h"` is BELOW 2000 in BOTH dims before Read.
- If a single screenshot is needed: `adb exec-out screencap -p | convert - -resize 600x600 small.png`

### 🚫 NO `git push`
GitHub account `thomasgorisse` SUSPENDED. Pushes return 403. Continue committing LOCALLY only.

### 📦 Worktree-local
This worktree has 90 commits not yet on main and not on origin. They live ONLY here. Don't `git reset` or switch branches without saving the SHA.

### 🧪 Pre-push gate before claiming done
Before declaring the audit complete:
```
./gradlew :sceneview:compileReleaseKotlin :arsceneview:compileReleaseKotlin
./gradlew :sceneview:test :arsceneview:testDebugUnitTest
bash .claude/scripts/pre-push-check.sh
```

## Prompt to give to the fresh session

```
Reprends l'audit Android demo dans le worktree
/Users/thomasgorisse/Projects/sceneview/.claude/worktrees/tender-haibt-6062c7/
sur la branche claude/tender-haibt-6062c7 (90 commits sur main, working tree clean).

Lis d'abord .claude/handoff-android-demo-audit.md pour le contexte complet.

Règles absolues :
1. NE JAMAIS Read une image > 2000px (raison du crash de la session précédente).
   Toujours scaler avec ffmpeg/convert avant le Read.
2. NE PAS git push — compte GitHub thomasgorisse suspendu, push retourne 403.
3. Tester en live screen-record (adb screenrecord 12s + ffmpeg fps=1 scale=400)
   les 15 demos restantes listées dans le handoff, en partant de LinesPathsDemo
   où la session précédente a crashé.

Quand tu as fini : run le pre-push gate complet (compile + tests + script),
puis met à jour CLAUDE.md "Current state" et .claude/handoff.md.

Continue sans rien me demander. Tout doit marcher à 100%.
```

## Files to know

- `.claude/handoff-android-demo-audit.md` — this file
- `samples/android-demo/src/main/java/io/github/sceneview/demo/DemoSettings.kt` — `qaMode` flag
- `samples/android-demo/src/main/java/io/github/sceneview/demo/DemoHelpers.kt` — `rememberPausableHeroYaw`, `LoadingScrim`, `OrbitState`
- `samples/android-demo/src/main/java/io/github/sceneview/demo/DemoScaffold.kt` — long-press title toggles `qaMode`
- `sceneview/src/main/java/io/github/sceneview/SceneScope.kt` — `LightNode` reactive props (intensity/direction/color via SideEffect)
