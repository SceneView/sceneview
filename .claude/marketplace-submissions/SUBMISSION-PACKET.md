# SceneView MCP — Marketplace Submission Packet

**Date:** 2026-04-17
**Package:** `sceneview-mcp` (npm)
**Latest:** v4.0.x (see `mcp/package.json` for the exact version)
**Repo:** github.com/sceneview/sceneview

> **Submission auth:** prefer the `sceneview` GitHub org account when
> a marketplace requires a PR (Cursor catalog, mcp.so). For
> marketplaces that accept Google/email signup, use that — no GitHub
> dependency.

---

## Canonical metadata (paste this everywhere)

**Name:** SceneView MCP
**Tagline (60 chars):** Cross-platform 3D & AR for Android, iOS, Web — one MCP
**Short description (160 chars):**
```
Give any AI assistant expert-level knowledge of 3D and AR development.
26 free tools + 35 Pro tools for SceneView SDK (Android/iOS/Web).
```

**Long description (markdown):**
```
SceneView MCP gives Claude, Cursor, ChatGPT, Copilot and any other
MCP-compatible AI assistant expert-level knowledge of the SceneView SDK
— the cross-platform 3D & AR library for Android (Jetpack Compose +
Filament), iOS / macOS / visionOS (SwiftUI + RealityKit), and Web
(Filament.js + WebXR).

**26 free tools** include:
- `get_started` — onboarding overview + 3 ready prompts
- `get_sample` / `list_samples` — 33 ready-to-paste examples
- `get_node_reference` — full API for any node / composable
- `validate_code` — catch threading & API mistakes before you ship
- `analyze_project` — inspect existing project for SceneView readiness
- `get_setup` / `get_migration_guide` — install snippets & 2.x → 4.x migration

**35+ Pro tools** (subscription) unlock:
- `generate_scene` — describe an AR app in plain English, get working
  Kotlin/Swift/JS in one shot
- `create_3d_artifact` / `render_3d_preview` — visual previews
- Multi-platform setup tools (iOS, web, AR specifically)
- Vertical packages: Automotive · Gaming · Healthcare · Interior
```

**Categories / tags:**
`3d`, `ar`, `mobile`, `android`, `ios`, `web`, `code-generation`, `developer-tools`, `kotlin`, `swift`, `compose`, `swiftui`, `filament`, `realitykit`, `arcore`, `arkit`, `mcp`, `claude`, `webxr`

**Install command (universal — stdio path):**
```bash
npx -y sceneview-mcp@latest
```

**🌐 Hosted HTTP MCP URL — anonymous (NEW, 2026-04-17)**

For marketplaces, ChatGPT MCP picker, Claude Desktop "Remote MCP",
Cursor "Add server by URL", or any client that wants HTTP transport
without an API key:

```
https://sceneview-mcp.mcp-tools-lab.workers.dev/mcp/public
```

- Free tier: 26 tools fully accessible
- Pro tier: 35 tools return JSON-RPC ACCESS_DENIED with /pricing pointer
- Rate limit: 60 requests / hour per IP
- No Authorization header required

**🔐 Hosted HTTP MCP URL — authenticated (paid Pro)**

```
https://sceneview-mcp.mcp-tools-lab.workers.dev/mcp
Authorization: Bearer sv_live_...
```

**Claude Desktop config:**
```json
{
  "mcpServers": {
    "sceneview": {
      "command": "npx",
      "args": ["-y", "sceneview-mcp@latest"]
    }
  }
}
```

**Cursor / Continue.dev config:** same as above, `~/.cursor/mcp.json`
or `~/.continue/config.json` under `mcpServers`.

**Optional env vars:**
- `SCENEVIEW_API_KEY=sv_live_...` — unlock 35 Pro tools (€19/mo)
- `SCENEVIEW_CTA=0` — disable in-tool upgrade reminders
- `SCENEVIEW_MCP_QUIET=1` — silence startup banner
- `SKETCHFAB_API_KEY=...` — bring-your-own Sketchfab token for `search_models`

**License:** MIT
**Pricing page:** https://sceneview-mcp.mcp-tools-lab.workers.dev/pricing
**Homepage:** https://sceneview.github.io
**Issues / source:** https://github.com/sceneview/sceneview

---

## Per-marketplace instructions

### 1. Smithery.ai (priority)

**URL:** https://smithery.ai/new
**Auth options:** GitHub OAuth (try `sceneview` org, NOT `ThomasGorisse`)
or "deploy from npm" path if available.

**Steps:**
1. Sign in with `sceneview` org (or `thomasgorisse` lowercase)
2. "Deploy a server" → "From npm package"
3. Package: `sceneview-mcp`
4. Use the `smithery.yaml` already in the repo root
5. Paste long description above
6. Tags: see metadata
7. Submit

**Confirmation check:** `curl https://server.smithery.ai/sceneview/sceneview-mcp` should return 200 (today returns 404).

### 2. mcp.so

**URL:** https://mcp.so/submit
**Auth:** GitHub OAuth (test with sceneview org)

**Steps:**
1. Sign in with `sceneview` GitHub
2. "Submit a server" → fill the form
3. Repo URL: `https://github.com/sceneview/sceneview`
4. Subfolder: `mcp/`
5. Description: paste long description
6. Submit

### 3. MCPize (monetization layer)

**URL:** https://mcpize.com/developers
**Auth:** Google / email signup likely (verify on the page)

**Why this is the priority:** MCPize provides per-call billing
($0.01/call), 85% revenue share, Stripe-payouts. This bypasses the
€19/mo subscription ceiling entirely — users pay $0.01 per Pro call,
no commitment. Per the strategy doc, this is the model successful MCPs
(21st.dev, SlideForge, Apify) use.

**Steps:**
1. Sign up (Google preferred)
2. Connect npm package `sceneview-mcp`
3. Set per-call price for the 35 Pro tools (suggest $0.05/call)
4. Toggle: keep €19/mo subscription as alternative (annual = €190)
5. Stripe payout setup

### 4. Apify Marketplace

**URL:** https://apify.com/mcp/developers
**Auth:** email/Google
**Audience:** 36k+ active developers, instant distribution

**Steps:** sign up → "Publish MCP server" → paste metadata → done.

### 5. PulseMCP (community directory)

**URL:** https://pulsemcp.com/submit
**Auth:** typically a GitHub PR — submit via the `sceneview` org account.

### 6. Cursor MCP catalog

**URL:** https://cursor.directory/mcp (or PR to cursor's community-mcp repo)
**Auth:** GitHub PR — submit via the `sceneview` org account.

### 7. ChatGPT MCP picker

**URL:** OpenAI's MCP marketplace (Plus/Team only)
**Submission process:** check https://platform.openai.com/docs/mcp
**Auth:** OpenAI account, not GitHub. **Doable now.**

### 8. GitHub Copilot Chat catalog

**URL:** https://docs.github.com/copilot/customizing-copilot/using-model-context-protocol/extending-copilot-chat-with-mcp
**Auth:** GitHub — submit via the `sceneview` org account.

---

## Priority order (what to submit FIRST)

1. **MCPize** — unlocks per-call monetization (Google/email signup)
2. **Apify** — 36k devs (Google/email signup)
3. **Smithery** — biggest catalog, use `sceneview` org auth
4. **mcp.so** — use `sceneview` org auth
5. **ChatGPT MCP marketplace** — OpenAI auth, not GitHub
6. **PulseMCP / Cursor / Copilot catalogs** — `sceneview` org GitHub PRs
