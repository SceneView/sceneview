# OpenAI App Store — SceneView 3D & AR submission kit

**Submission URL:** https://platform.openai.com/apps-manage
**App ID:** *(stored in maintainer's private notes — re-fetch from the OpenAI Apps dashboard)*
**Status:** Section 1 COMPLETE, Section 2 partially filled (URL pasted + 63 tools auto-scanned), blocked on server-side prerequisites.
**Last touched:** 2026-04-17 (multiple times during the magical-almeida session)

## Blockers identified during the live submission attempt

Two server-side prerequisites surfaced when Section 2 (MCP Server) was filled. Both need code changes before the OpenAI review will accept the app — DO NOT click "Submit for review" until they are resolved.

### Blocker 1 — Tool annotations missing from `tools/list`

OpenAI requires every tool to declare its `readOnlyHint`, `openWorldHint`, and `destructiveHint` in the MCP `tools/list` response. Without these, the submission form makes the developer write a free-form justification per tool — for SceneView's bundled gateway, that is **63 tools × 3 annotations = 189 free-form fields**, which is unmanageable manually.

**Fix:** add the three annotation fields to every entry in:

- `mcp/src/tools/definitions.ts` (sceneview-mcp tools)
- `mcp/packages/automotive/src/tools.ts`
- `mcp/packages/gaming/src/tools.ts`
- `mcp/packages/healthcare/src/tools.ts`
- `mcp/packages/interior/src/tools.ts`
- `mcp/packages/rerun/src/tools.ts`

For all current tools the correct values are `readOnlyHint: true`, `openWorldHint: false`, `destructiveHint: false` — they all just return generated docs/code, none reach external services except `search_models` (which queries Sketchfab → set `openWorldHint: true` for that one only) and `render_3d_preview` / `create_3d_artifact` (still `readOnlyHint: true` because the artifact bytes are returned, not persisted).

Also confirm that `mcp-gateway/src/mcp/transport.ts` (or wherever `tools/list` is built) forwards these fields untouched.

Estimated effort: ~1-2h including a contract test that asserts every TOOL_DEFINITION entry has the three annotations.

### Blocker 2 — Domain verification on `*.workers.dev` may not work

OpenAI's domain verification step asks for a token to be served at a well-known URL on the same hostname (or a parent hostname) as the MCP URL. Our endpoint is `sceneview-mcp.mcp-tools-lab.workers.dev/mcp/public` — that subdomain is owned by Cloudflare, not by Thomas, so the parent-hostname rule may force OpenAI to require verification at the apex `workers.dev`, which is impossible.

**Fix attempts to try in order:**

1. Click "Verify Domain" with the default challenge URL — OpenAI may accept the same-hostname token served from our worker via a `/.well-known/openai-mcp-domain-verification` route. If yes, just add that route to `mcp-gateway/src/index.ts` and ship it.
2. If (1) fails, point the MCP at a custom domain that Thomas owns (e.g. `mcp.sceneview.dev` if `sceneview.dev` is registered). Cloudflare lets you map a Worker to a custom domain in 2 minutes; that solves the verification.
3. Worst case: skip the OpenAI App Store route and rely on the per-user Custom Connector path (Path A, documented at the bottom of this file).

Estimated effort: 30 min if step 1 works, 1-2h if a custom domain is needed.

---

## What's already saved in the OpenAI draft

Section 1 (App Info) — every field below is persisted in the OpenAI draft (auto-save confirmed by the "Draft saved" indicator in the form header):

This file collects every paste-ready field for the 6-step submission flow.
Originated from a Chrome MCP session that turned out to be too slow for
multi-step forms — Thomas finishes the submission manually using these
snippets.

---

## Section 1 — App Info

### Logo icon

PNG, square, no border. Source files:

- `branding/exports/` (root of the repo) — pick the largest square PNG
- Fallback: `branding/exports/sceneview-logo-512.png` if it exists

### App name (already entered)

```
SceneView 3D & AR
```

### Subtitle (30 chars max)

```
3D & AR for Android, iOS, web
```

(28 characters ✓)

### Description

```
SceneView gives you expert-level help for building 3D and AR apps on Android (Jetpack Compose + Filament), iOS / macOS / visionOS (SwiftUI + RealityKit), and Web (Filament.js + WebXR).

What you can do:
- Generate complete, compilable 3D scenes from natural language ("a room with a table and two chairs")
- Build AR experiences with ARCore and ARKit using a single declarative API
- Get the exact API reference for any node, composable or SwiftUI view
- Validate generated code before you ship it (catches threading bugs, deprecated APIs, missing null-checks)
- Browse 33 ready-to-paste samples covering models, animations, gestures, lighting, geometry, AR
- Specialized vertical packs for automotive (configurators, AR showrooms), gaming (character viewers, physics), healthcare (anatomy, surgical planning, dental, medical imaging), and interior design (room planners, AR furniture placement)
- Live AR debugging through Rerun.io integration

Free tier covers 26 documentation, validation and sample tools. Pro tier (€19/mo) unlocks scene generation, 3D artifacts, multi-platform setup, and the four vertical packs.
```

---

## Section 2 — MCP Server

### Server URL (anonymous, free tier)

```
https://sceneview-mcp.mcp-tools-lab.workers.dev/mcp/public
```

- 26 free tools fully accessible
- 35 Pro tools return JSON-RPC ACCESS_DENIED with a /pricing pointer
- IP rate limit: 60 requests / hour

### Server URL (authenticated Pro tier)

If OpenAI requires an authenticated endpoint variant:

```
https://sceneview-mcp.mcp-tools-lab.workers.dev/mcp
```

Auth: Bearer token (`sv_live_…` from <https://sceneview-mcp.mcp-tools-lab.workers.dev/pricing>)

### Transport

Streamable HTTP (POST `/mcp/public` with JSON-RPC 2.0 body).
SSE long-poll (`GET /mcp/public` with `Accept: text/event-stream`) returns
501 Not Implemented today — if OpenAI's reviewers require SSE, we add it
in `mcp-gateway/src/mcp/transport.ts` (TODO already in the file, ~1-2h).

---

## Section 3 — Testing instructions (for OpenAI reviewers)

```
Quick test (no auth required):

1. In ChatGPT, ask: "Show me a SceneView code sample for AR plane detection on Android"
   → expect get_sample to return a complete Kotlin snippet with comments

2. Ask: "What does the rememberModelInstance composable do? Give me the full API reference."
   → expect get_node_reference to return the API surface

3. Ask: "Validate this code: [paste any small Kotlin SceneView snippet]"
   → expect validate_code to flag issues or confirm

Pro-tier tools (generate_scene, create_3d_artifact, render_3d_preview, get_platform_setup, migrate_code, plus the four vertical packs) return JSON-RPC ACCESS_DENIED with a /pricing pointer for free users — this is the intended upgrade path.

Telemetry: anonymous tool-call counts only, no prompt content. Opt-out env var SCENEVIEW_CTA=0 silences the in-tool upgrade reminders.
```

---

## Section 4 — Screenshots

Not yet captured. Two options:

**A. Skip if OpenAI allows it** — submit faster, risk a "needs screenshots" rejection
**B. Capture 3 screenshots from ChatGPT with the connector already added**

Suggested prompts to screenshot in ChatGPT (after adding the connector):

1. *"Show me a SceneView sample for AR with anchors."* — captures `get_sample` returning Kotlin
2. *"Build a 3D car configurator scene."* — captures the ACCESS_DENIED + /pricing path (proves the upgrade UX is clean)
3. *"What's in the SceneView API for LightNode?"* — captures `get_node_reference` returning the API doc

---

## Section 5 — Global

- **Country availability:** All countries (no known legal restrictions)
- **Languages:** English (the tool output is English-language)
- **Categories:** Developer Tools, Design, Productivity (pick from OpenAI's enum)

---

## Section 6 — Submit checklist

Before clicking Submit:

- [ ] Trademark "SceneView" — confirm Thomas owns or is authorized to use the name (npm package, GitHub orgs already published under this name)
- [ ] Logo PNG uploaded, square, no border, no rounded corners
- [ ] Live URL responds (smoke test):
  ```bash
  curl -X POST https://sceneview-mcp.mcp-tools-lab.workers.dev/mcp/public \
    -H "content-type: application/json" \
    -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26"}}'
  ```
  Expect HTTP 200 with `result.serverInfo.name = "sceneview-mcp-gateway"`.
- [ ] Pricing page renders: <https://sceneview-mcp.mcp-tools-lab.workers.dev/pricing>
- [ ] Pro upgrade flow tested at least once end-to-end with a Stripe live checkout (already done by Thomas on 2026-04-12, see CLAUDE.md)

---

## Review timeline

OpenAI documents 1-7 days for review. You'll get an email with the decision.
On rejection, the email typically lists the specific rule violated — fix and
re-submit (no penalty for re-submission).

---

## What this submission unlocks

- SceneView appears in the ChatGPT app directory at apps.openai.com
- ChatGPT Plus / Pro / Team users can install in 1 click (vs the
  Developer Mode + custom URL flow which is hidden behind a toggle)
- Discovery via ChatGPT's search across the directory
- OpenAI also creates a Codex distribution plugin automatically — bonus
  reach into the Codex CLI install base

---

## Related (not done yet)

- **Path A** — per-user "custom connector" via Developer Mode. Each user
  toggles Settings → Developer Mode → Connectors → New connector → paste
  `https://sceneview-mcp.mcp-tools-lab.workers.dev/mcp/public`. Useful for
  early adopters before the App Store listing is approved.
- **SSE endpoint** — if OpenAI's review requires it, see
  `mcp-gateway/src/mcp/transport.ts` line ~145 (TODO marker for the
  long-poll implementation).
