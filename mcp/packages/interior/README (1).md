# interior-design-3d-mcp

MCP server for Interior Design & Home 3D visualization with [SceneView](https://github.com/sceneview/sceneview-android).

Give Claude the tools to generate complete, compilable Kotlin Compose code for interior design apps: room planning, AR furniture placement, material switching, lighting design, and cinematic room tours.

## Installation

Add to your Claude Code MCP config (`~/.claude.json` or project `.mcp.json`):

```json
{
  "mcpServers": {
    "interior-design-3d": {
      "command": "npx",
      "args": ["-y", "interior-design-3d-mcp"]
    }
  }
}
```

Or install globally:

```bash
npm install -g interior-design-3d-mcp
```

## Tools

### `get_room_planner`
Generate a 3D room layout planner with procedural geometry.

**Parameters:**
- `roomType` — Room type: `living-room`, `bedroom`, `kitchen`, `bathroom`, `dining-room`, `office`, `studio`, `hallway`, `garage`, `open-plan`
- `widthMeters` — Room width in meters (default: `4.0`)
- `lengthMeters` — Room length in meters (default: `5.0`)
- `heightMeters` — Room height in meters (default: `2.7`)
- `wallStyle` — Wall material: `standard`, `brick`, `concrete`, `wood-panel`, `glass`
- `floorStyle` — Floor material: `hardwood`, `tile`, `carpet`, `marble`, `concrete`, `laminate`, `vinyl`
- `windows` — Number of windows (default: `2`)
- `doors` — Number of doors (default: `1`)
- `ar` — Generate AR version for tabletop placement at 1:10 scale (default: `false`)

**Example:**
```
Use get_room_planner with roomType="open-plan", widthMeters=8.0, floorStyle="marble"
```

---

### `get_furniture_placement`
Generate an AR furniture placement composable with rotation and scaling.

**Parameters:**
- `category` — Furniture type: `sofa`, `chair`, `table`, `bed`, `desk`, `shelf`, `wardrobe`, `cabinet`, `lamp`, `rug`, `plant`, `mirror`
- `size` — Size preset: `small`, `medium`, `large`, `custom`
- `colorHex` — Color customization hex (optional)
- `modelPath` — Custom GLB model path (optional)
- `rotatable` — Include rotation control (default: `true`)
- `scalable` — Include scale control (default: `true`)
- `ar` — Generate AR version (default: `true`)

**Example:**
```
Use get_furniture_placement with category="sofa" and size="large"
```

---

### `get_material_switcher`
Generate a 3D material switcher with live PBR preview.

**Parameters:**
- `surface` — Surface type: `wall-paint`, `floor`, `ceiling`, `backsplash`, `countertop`, `fabric`, `curtain`, `wallpaper`
- `finish` — Paint finish: `matte`, `satin`, `semi-gloss`, `gloss`, `eggshell`
- `colors` — Custom color palette (hex strings, optional)
- `texturePaths` — Custom texture GLB paths (optional)
- `beforeAfter` — Include before/after comparison slider (default: `false`)
- `ar` — Generate AR version (default: `false`)

**Example:**
```
Use get_material_switcher with surface="wall-paint", finish="satin", beforeAfter=true
```

---

### `get_lighting_design`
Generate a 3D lighting design scene with configurable light types.

**Parameters:**
- `lights` — List of light types: `ambient`, `spot`, `accent`, `pendant`, `recessed`, `track`, `sconce`, `floor-lamp`, `table-lamp`, `chandelier`, `strip-led`, `natural`
- `colorTemperature` — Color temperature: `candlelight`, `warm-white`, `neutral-white`, `cool-white`, `daylight`
- `dimmable` — Include dimmer sliders (default: `true`)
- `roomModel` — Custom room GLB path (optional)
- `showShadows` — Enable shadow casting (default: `true`)
- `ar` — Generate AR version (default: `false`)

**Example:**
```
Use get_lighting_design with lights=["pendant","strip-led","natural"] and colorTemperature="warm-white"
```

---

### `get_room_tour`
Generate a cinematic room tour with camera animation.

**Parameters:**
- `style` — Tour style: `orbit`, `walkthrough`, `flyover`, `dolly`, `panoramic`
- `speed` — Camera speed: `slow`, `normal`, `fast`
- `roomModel` — Room GLB model path (optional)
- `ar` — Generate AR miniature version (default: `false`)

**Example:**
```
Use get_room_tour with style="walkthrough" and speed="slow"
```

---

### `list_furniture_models`
List free 3D furniture models available for use.

**Parameters:**
- `category` — Filter by category (optional): `seating`, `tables`, `storage`, `beds`, `lighting`, `decor`, `kitchen`, `bathroom`

**Example:**
```
Use list_furniture_models with category="seating"
```

---

### `validate_interior_code`
Validate SceneView interior code for correctness and best practices.

**Parameters:**
- `code` — Kotlin Compose code to validate

**Example:**
```
Use validate_interior_code with the generated room planner code
```

## Generated Code

All tools generate complete, compilable Kotlin Compose code using:
- `io.github.sceneview:sceneview:3.6.2` — 3D scenes
- `io.github.sceneview:arsceneview:3.6.2` — AR scenes

Add to your `build.gradle`:
```kotlin
implementation("io.github.sceneview:sceneview:3.6.2")
// For AR tools:
implementation("io.github.sceneview:arsceneview:3.6.2")
```

## Quality

- **152 tests** across 7 test suites
- All code uses SceneView's composable API (no imperative calls)
- LightNode uses named `apply` parameter (not trailing lambda)
- Threading-safe: uses `rememberModelInstance` for model loading

## Disclaimer

Generated code suggestions for interior design visualization. For inspiration only — verify dimensions and materials before purchasing or installing.
See [LICENSE](https://github.com/sceneview/sceneview/blob/main/mcp-interior/LICENSE).
