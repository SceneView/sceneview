# healthcare-3d-mcp

MCP server for Healthcare & Medical 3D visualization with [SceneView](https://github.com/sceneview/sceneview).

Give Claude (or any MCP-compatible AI assistant) the ability to generate complete, compilable Kotlin code for medical 3D applications on Android using Jetpack Compose and SceneView.

## Tools

| Tool | Description |
|---|---|
| `get_anatomy_viewer` | 3D anatomy viewer — skeleton, organs, muscles, nervous system, etc. Supports transparency, exploded view, labels, AR mode. |
| `get_molecule_viewer` | Molecular structure viewer — proteins, DNA, RNA, antibodies, viruses. Ball-and-stick, space-filling, ribbon representations. PDB ID support. |
| `get_medical_imaging` | CT/MRI/PET scan 3D visualization. Full DICOM-to-3D pipeline docs. Windowing controls, segmentation overlays, AR mode. |
| `get_surgical_planning` | Surgical planning visualization — measurement, annotation, cross-section, implant placement, pre-op comparison. 8 surgery types. |
| `get_dental_viewer` | Dental scanning visualization — full-arch, implant, orthodontic, CBCT. Root/nerve overlays, treatment stages, shade matching. |
| `list_medical_models` | Database of 20+ free medical 3D models from BodyParts3D, NIH 3D Print Exchange, Sketchfab. With source URLs, licenses, format info. |
| `validate_medical_code` | Validates SceneView medical code for threading, null-safety, API misuse, DICOM requirements, format conversion warnings. |

## Installation

### Option A — stdio via `npx` (Claude Desktop, Claude Code, Cursor, Windsurf)

Add to `~/Library/Application Support/Claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "healthcare-3d": {
      "command": "npx",
      "args": ["-y", "healthcare-3d-mcp"]
    }
  }
}
```

### Option B — hosted HTTP MCP URL (ChatGPT, Claude Desktop "Remote MCP", Cursor URL)

The 6 healthcare tools are also exposed (along with sceneview-mcp + automotive/gaming/interior/rerun verticals — 63 tools total) on the shared SceneView gateway:

```
https://sceneview-mcp.mcp-tools-lab.workers.dev/mcp/public
```

- Anonymous, no API key required
- All 6 healthcare tools are Pro tier — they return a JSON-RPC ACCESS_DENIED with a `/pricing` pointer until you subscribe
- Rate limit: 60 req/h per IP

For the **authenticated Pro tier** (full quota, all tools dispatch):

```
https://sceneview-mcp.mcp-tools-lab.workers.dev/mcp
Authorization: Bearer sv_live_...
```

### From source

```bash
git clone https://github.com/sceneview/sceneview.git
cd sceneview/mcp/packages/healthcare
npm install
npm run build
npm start
```

## Usage examples

Ask your AI assistant:

- "Build me an anatomy viewer showing the skeletal system with transparency"
- "Create an AR molecule viewer for hemoglobin (PDB: 1HHO) with auto-rotation"
- "Generate a CT scan viewer with windowing controls and segmentation overlay"
- "Build a surgical planning tool for orthopedic surgery with implant placement"
- "Create a dental scan viewer with orthodontic treatment stage timeline"
- "List free 3D models for cardiac anatomy education"

Every tool returns complete, compilable Kotlin code using SceneView 3.6.0 with proper:
- Gradle dependencies
- SceneView composable setup (engine, modelLoader, collisionSystem)
- Null-safe model loading
- LightNode with named `apply` parameter (not trailing lambda)
- Material 3 UI controls
- AR mode with ARCore setup instructions

## Medical disclaimer

This software generates code for 3D visualization. It is **not** a medical device and must not be used for clinical diagnosis or treatment without appropriate validation. All generated code should be reviewed before production use.

## Dependencies

- [SceneView](https://github.com/sceneview/sceneview) 3.6.0 — 3D/AR rendering
- [ARSceneView](https://github.com/sceneview/sceneview) 3.6.0 — AR features
- [MCP SDK](https://github.com/modelcontextprotocol/sdk) — Model Context Protocol

## License

Apache 2.0 — see [LICENSE](LICENSE).
