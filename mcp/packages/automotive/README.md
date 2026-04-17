# automotive-3d-mcp

**Automotive 3D MCP — give any AI assistant everything it needs to build car configurators, AR showrooms, HUD overlays, 3D dashboards, parts catalogs, and EV charging station viewers with [SceneView](https://sceneview.github.io) on Android.**

Every tool returns complete, compilable Kotlin code using current SceneView 3.6.x APIs (Jetpack Compose, `rememberModelInstance`, `ModelNode`, `LightNode` with the named `apply` parameter, `ARScene`) — ready to drop into an Android project.

## Installation

### Option A — stdio via `npx` (Claude Desktop, Claude Code, Cursor, Windsurf)

Add to `~/Library/Application Support/Claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "automotive-3d": {
      "command": "npx",
      "args": ["-y", "automotive-3d-mcp"]
    }
  }
}
```

Then restart Claude Desktop and ask:

> *"Build me a 3D car configurator for a sports car with color picker and turntable."*

### Option B — hosted HTTP MCP URL (ChatGPT, Claude Desktop "Remote MCP", Cursor URL)

The 7 automotive tools are also exposed (along with sceneview-mcp + the gaming/healthcare/interior/rerun verticals — 63 tools total) on the shared SceneView gateway:

```
https://sceneview-mcp.mcp-tools-lab.workers.dev/mcp/public
```

- Anonymous, no API key required
- All 7 automotive tools are Pro tier — they return a JSON-RPC ACCESS_DENIED with a `/pricing` pointer until you subscribe
- Rate limit: 60 req/h per IP

For the **authenticated Pro tier** (full quota, all tools dispatch):

```
https://sceneview-mcp.mcp-tools-lab.workers.dev/mcp
Authorization: Bearer sv_live_...
```

### From the command line

```bash
npx automotive-3d-mcp
```

### From source

```bash
git clone https://github.com/sceneview/sceneview.git
cd sceneview/mcp/packages/automotive
npm install
npm run build
npm start
```

## Tools

| Tool | Description |
|---|---|
| `get_car_configurator` | 3D car configurator with color / material variants, camera presets (exterior, interior, detail), turntable, and AR mode. 10 body styles. |
| `get_hud_overlay` | Heads-up display overlay via `ViewNode` — speedometer, navigation, alerts, fuel, temperature, gear, turn signals, lane assist. 6 styles. |
| `get_dashboard_3d` | 3D instrument cluster with animated gauges — speedometer, tachometer, fuel, temperature, oil pressure, battery, boost, odometer. 6 themes. |
| `get_ar_showroom` | AR car placement in real space (driveway, parking lot, garage). Walk-around, color swap, open doors, measurements, night lighting, photo capture. |
| `get_parts_catalog` | 3D parts explorer with exploded view, part selection, cross-section, search. 10 categories with realistic part data and pricing. |
| `get_ev_charging_station_viewer` | **New in 1.1.** 3D EV charging station model + Material 3 overlay showing live charge level, available bays, and ETA. 5 connectors, 4 layouts, optional AR mode. |
| `get_car_paint_shader` | **New in 1.1.** Filament `.mat` car-paint material with clearcoat and metallic flakes, plus a Kotlin snippet that loads the compiled `.filamat` and applies it to a model. 4 finish presets. |
| `list_car_models` | Database of 18+ free, openly-licensed 3D car models — Khronos `ToyCar`, Sketchfab free cars, engines, interiors, wheels. Source URLs and licenses included. |
| `validate_automotive_code` | Validates SceneView automotive code for threading, null-safety, API misuse, scale issues, and format warnings. |

## Usage examples

Ask your AI assistant:

- *"Build me a 3D car configurator for a sedan with color picker and turntable."*
- *"Create an AR showroom to place an SUV in my driveway with night lighting."*
- *"Generate a HUD overlay with speedometer, navigation, and fuel gauge in sport style."*
- *"Build a 3D instrument cluster with speedometer and tachometer in electric theme."*
- *"Create an engine parts catalog with exploded view and part numbers."*
- *"Show me a 3D EV charging station viewer for a Tesla bank with the live charge level overlay."*
- *"Give me a Filament car paint shader with a pearlescent gold finish and a clearcoat layer."*
- *"List the free 3D car models I can use for a configurator demo."*
- *"Validate my car viewer SceneView code for common mistakes."*

Every tool returns complete, compilable Kotlin code using SceneView 3.6.x with proper:

- Gradle dependencies
- SceneView composable setup (`rememberEngine`, `rememberModelLoader`, `rememberCollisionSystem`)
- Null-safe model loading via `rememberModelInstance`
- `LightNode` with the named `apply` parameter (not a trailing lambda)
- Material 3 UI controls
- Main-thread Filament calls (no background-thread model or material creation)
- AR mode with ARCore setup instructions

## Dependencies

- [SceneView](https://github.com/sceneview/sceneview) 3.6.x — 3D / AR rendering on Android
- [ARSceneView](https://github.com/sceneview/sceneview) 3.6.x — AR features (optional, only required when `ar=true`)
- [MCP SDK](https://github.com/modelcontextprotocol/typescript-sdk) — Model Context Protocol

## License

Apache 2.0 — see [LICENSE](LICENSE).

Learn more about SceneView at [sceneview.github.io](https://sceneview.github.io).
