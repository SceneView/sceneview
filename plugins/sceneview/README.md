# SceneView plugin for Claude Code

AI-first toolkit for building 3D and AR apps with [SceneView](https://github.com/sceneview/sceneview) — the cross-platform 3D & AR SDK for Android (Jetpack Compose), iOS / macOS / visionOS (SwiftUI), and Web.

## What you get

- **MCP server** (`sceneview-mcp`) — gives Claude the full SceneView API reference, code generation tools, model search, and project analysis. So when you ask Claude to *"build me an AR floorplan viewer"* it produces correct, compilable code on the first try.
- **11 contributor commands** — `/sceneview:contribute`, `/sceneview:review`, `/sceneview:test`, `/sceneview:document`, `/sceneview:quality-gate`, `/sceneview:publish-check`, `/sceneview:sync-check`, `/sceneview:version-bump`, `/sceneview:release`, `/sceneview:evaluate`, `/sceneview:maintain`. These automate the full contribution workflow: pre-PR checks, multi-platform sync, releases.

## Install

```
/plugin marketplace add sceneview/sceneview
/plugin install sceneview@sceneview
```

The MCP server starts automatically. To use the contributor commands, run any `/sceneview:*` skill.

## Use cases

### Building an app with SceneView

Just ask Claude. The plugin's MCP server gives Claude the full SDK reference so it can:
- Generate `SceneView { }` and `ARSceneView { }` composables with the right node types, materials, animations, and lighting setups
- Search 3D models on Sketchfab, Poly Pizza, and the SceneView Asset CDN
- Analyze your project to suggest the right SceneView APIs for your use case
- Debug AR sessions via the Rerun.io bridge

### Contributing to SceneView

Run `/sceneview:contribute` to walk through the full contribution flow — codebase reading, change scoping, review, documentation, PR opening. Use `/sceneview:review` and `/sceneview:test` for pre-PR validation, and `/sceneview:release` to cut a coordinated multi-platform release (Maven Central + npm + SPM + GitHub Release).

## Platforms supported by SceneView

| Platform | Renderer | Framework |
|---|---|---|
| Android | Filament | Jetpack Compose |
| iOS / macOS / visionOS | RealityKit | SwiftUI |
| Web | Filament.js (WASM) | Kotlin/JS |
| Flutter | Native bridge | PlatformView |
| React Native | Native bridge | Fabric |

## Links

- Docs: https://sceneview.github.io
- API reference: https://github.com/sceneview/sceneview/blob/main/llms.txt
- Issues: https://github.com/sceneview/sceneview/issues
- Sponsor: https://github.com/sponsors/sceneview

## License

MIT
