---
title: "6 MCP Servers for 3D & AR Development — What AI Can Build Now"
published: false
description: "MCP servers that give Claude, Cursor, and Copilot the ability to generate 3D and AR code. Car configurators, medical viz, game features — from a prompt."
tags: ai, mcp, claude, 3d
cover_image: https://raw.githubusercontent.com/sceneview/sceneview/main/branding/exports/banner-github.png
canonical_url: https://dev.to/sceneview/6-mcp-servers-for-3d-ar-development
---

What if you could ask Claude "build me a car configurator" and get compilable Android code in 10 seconds?

MCP (Model Context Protocol) makes this real. An MCP server gives AI assistants access to specialized tools — code generators, validators, domain knowledge — so they produce working code instead of generic suggestions.

I built 6 MCP servers for 3D and AR development. Here's what each one does and how to use them.

## What Is MCP?

MCP is an open protocol (by Anthropic) that lets AI assistants call external tools. Think of it as plugins, but standardized — one server works with Claude, Cursor, Windsurf, and any MCP-compatible client.

An MCP server provides:
- **Tools**: functions the AI can call (generate code, validate, search)
- **Resources**: data the AI can read (API docs, samples)
- **Prompts**: pre-built workflows (code review, scaffolding)

## The 6 Servers

### 1. sceneview-mcp — General 3D & AR (28 tools)

The flagship. Covers all SceneView features across Android, iOS, and Web.

```bash
npx sceneview-mcp
```

Key tools:
- `generate_scene` — complete scene code from a description
- `validate_code` — catches threading bugs, null handling, wrong APIs
- `search_models` — searches Sketchfab for free 3D models
- `get_sample` — returns working sample code for any feature

**Example prompt**: "Create a 3D product viewer with orbit camera and environment lighting"

### 2. automotive-3d-mcp — Car Configurators & HUD

```bash
npx automotive-3d-mcp
```

Specialized for automotive UX: car configurators, HUD overlays, 3D dashboards, AR showrooms, parts catalogs. Knows ADAS display patterns, Tier-1 integration requirements, and automotive UX standards.

**Example prompt**: "Build a car color configurator with paint + wheel options"

### 3. healthcare-3d-mcp — Medical Visualization

```bash
npx healthcare-3d-mcp
```

Anatomy viewers, DICOM slice rendering, molecular structures, surgical planning tools, dental scanning visualization. Includes HIPAA-compliant patterns and medical imaging standards.

**Example prompt**: "Create an interactive anatomy model with organ highlighting"

### 4. gaming-3d-mcp — Game Development

```bash
npx gaming-3d-mcp
```

Character controllers, terrain generation, particle systems, physics interactions, inventory UI, NPC behaviors. Covers casual games, not AAA — optimized for mobile.

**Example prompt**: "Generate a third-person character controller with jump and attack"

### 5. interior-design-3d-mcp — Room Planning

```bash
npx interior-design-3d-mcp
```

Room planners, AR furniture placement, material/finish switching, lighting simulation, measurement tools. Knows furniture catalog patterns and real estate staging.

**Example prompt**: "Build an AR room planner where users tap to place furniture"

### 6. rerun-3d-mcp — AR Debug Visualization

```bash
npx rerun-3d-mcp
```

Integration with [Rerun.io](https://rerun.io) for AR debugging. Stream point clouds, camera poses, anchor positions, and depth maps from your AR app to Rerun's web viewer in real-time.

**Example prompt**: "Set up Rerun bridge to visualize my AR app's point cloud"

## How to Install

**Claude Code (CLI):**
```bash
claude mcp add sceneview -- npx sceneview-mcp
```

**Claude Desktop / Cursor / Windsurf:**
```json
{
  "mcpServers": {
    "sceneview": {
      "command": "npx",
      "args": ["-y", "sceneview-mcp"]
    }
  }
}
```

Replace `sceneview-mcp` with any of the 6 server names above.

## What Makes a Good 3D MCP Server

After building 6 servers, here's what matters:

1. **Complete, compilable code** — not snippets. The output must build and run.
2. **Validation tools** — catch common mistakes (wrong thread, null model, missing permissions) before the developer runs the code.
3. **Domain knowledge** — not just API docs, but patterns. "A car configurator needs X, Y, Z" is more useful than "here's the ModelNode API."
4. **Working samples** — every tool returns code that was tested against the real SDK.

## Try It Now

The fastest way to test:

```bash
# Install Claude Code if you haven't
npm install -g @anthropic-ai/claude-code

# Add SceneView MCP
claude mcp add sceneview -- npx sceneview-mcp

# Ask it to build something
claude "Build me an AR app that places 3D furniture on detected surfaces"
```

All servers are open source and available on npm:
- [sceneview-mcp](https://www.npmjs.com/package/sceneview-mcp) — 13k+ monthly installs
- [automotive-3d-mcp](https://www.npmjs.com/package/automotive-3d-mcp)
- [healthcare-3d-mcp](https://www.npmjs.com/package/healthcare-3d-mcp)
- [gaming-3d-mcp](https://www.npmjs.com/package/gaming-3d-mcp)
- [interior-design-3d-mcp](https://www.npmjs.com/package/interior-design-3d-mcp)
- [rerun-3d-mcp](https://www.npmjs.com/package/rerun-3d-mcp)

**GitHub**: [github.com/sceneview/sceneview](https://github.com/sceneview/sceneview)

---

*All SceneView MCP servers are open source (Apache 2.0). Contributions welcome.*
