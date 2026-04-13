# gaming-3d-mcp

MCP server for Gaming & Entertainment 3D visualization with [SceneView](https://github.com/sceneview/sceneview-android).

Give Claude the tools to generate complete, compilable Kotlin Compose code for game UIs: character viewers, level editors, physics simulations, particle effects, and 3D inventories.

## Installation

Add to your Claude Code MCP config (`~/.claude.json` or project `.mcp.json`):

```json
{
  "mcpServers": {
    "gaming-3d": {
      "command": "npx",
      "args": ["-y", "gaming-3d-mcp"]
    }
  }
}
```

Or install globally:

```bash
npm install -g gaming-3d-mcp
```

## Tools

### `get_character_viewer`
Generate a 3D character viewer composable with animation support.

**Parameters:**
- `style` — Character style: `humanoid`, `cartoon`, `chibi`, `robot`, `creature`, `animal`, `fantasy`, `sci-fi`
- `animations` — List of animation states to include (optional)
- `ar` — Generate AR version (default: `false`)
- `modelPath` — Custom GLB model path (optional)

**Example:**
```
Use get_character_viewer with style="robot" and animations=["idle","walk","attack"]
```

---

### `get_level_editor`
Generate a procedural 3D level editor with geometry placement.

**Parameters:**
- `theme` — Level theme: `dungeon`, `forest`, `space`, `underwater`, `desert`, `city`, `castle`, `ice`, `lava`, `sky`
- `gridSize` — Grid size in units (default: `10`)
- `editable` — Include geometry palette (default: `true`)
- `showGrid` — Show grid floor (default: `true`)
- `ar` — Generate AR version (default: `false`)

**Example:**
```
Use get_level_editor with theme="space" and gridSize=20
```

---

### `get_physics_game`
Generate a physics simulation game with collision detection.

**Parameters:**
- `preset` — Physics preset: `bouncing-balls`, `bowling`, `billiards`, `marble-run`, `tower-collapse`, `pong-3d`, `pinball`, `cannon`
- `gravity` — Gravity mode: `earth`, `moon`, `zero-g`, `reverse`, `jupiter`, `mars`
- `bounciness` — Restitution coefficient (default: `0.7`)
- `showTrajectory` — Show trajectory line (default: `false`)
- `ar` — Generate AR version (default: `false`)

**Example:**
```
Use get_physics_game with preset="bowling" and gravity="moon"
```

---

### `get_particle_effects`
Generate a particle system composable with configurable effects.

**Parameters:**
- `effect` — Particle effect: `fire`, `smoke`, `sparkles`, `rain`, `snow`, `explosion`, `magic`, `confetti`, `bubbles`, `fireflies`
- `count` — Number of particles (default: `200`)
- `ar` — Generate AR version (default: `false`)

**Example:**
```
Use get_particle_effects with effect="magic" and count=500
```

---

### `get_inventory_3d`
Generate a 3D game inventory UI with item showcase.

**Parameters:**
- `layout` — Inventory layout: `grid`, `carousel`, `list`, `radial`
- `categories` — Item categories to show (optional)
- `itemCount` — Number of items per category (default: `6`)

**Example:**
```
Use get_inventory_3d with layout="carousel" and categories=["weapons","armor"]
```

---

### `list_game_models`
List free 3D game models available for use.

**Parameters:**
- `category` — Filter by category (optional): `characters`, `weapons`, `environments`, `vehicles`, `props`, `effects`

**Example:**
```
Use list_game_models with category="characters"
```

---

### `validate_game_code`
Validate SceneView game code for correctness and best practices.

**Parameters:**
- `code` — Kotlin Compose code to validate

**Example:**
```
Use validate_game_code with the generated character viewer code
```

## Generated Code

All tools generate complete, compilable Kotlin Compose code using:
- `io.github.sceneview:sceneview:4.0.0` — 3D scenes
- `io.github.sceneview:arsceneview:4.0.0` — AR scenes

Add to your `build.gradle`:
```kotlin
implementation("io.github.sceneview:sceneview:4.0.0")
// For AR tools:
implementation("io.github.sceneview:arsceneview:4.0.0")
```

## Quality

- **156 tests** across 7 test suites
- All code uses SceneView's composable API (no imperative calls)
- LightNode uses named `apply` parameter (not trailing lambda)
- Threading-safe: uses `rememberModelInstance` for model loading

## Disclaimer

Generated code suggestions for gaming/entertainment 3D visualization. Review and test before production use.
See [LICENSE](https://github.com/sceneview/sceneview/blob/main/mcp-gaming/LICENSE).
