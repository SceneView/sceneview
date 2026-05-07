# Anthropic official marketplace submission — SceneView plugin

**Status:** READY TO SUBMIT — Thomas submits himself via the in-app form.

## Submission URL

Pick whichever surface Thomas is logged into:

- **Claude.ai:** https://claude.ai/settings/plugins/submit
- **Console:** https://platform.claude.com/plugins/submit

Either form goes to the same review pipeline. Once approved, the plugin shows up under `/plugin discover` for every Claude Code user globally.

## Pre-filled answers

The form is short. Below is the canonical answer for each field — copy/paste verbatim.

### Plugin name

```
sceneview
```

### Marketplace source

```
https://github.com/sceneview/sceneview
```

### One-line description (≤120 chars)

```
AI-first toolkit for building 3D and AR apps with SceneView (Android, iOS, Web). MCP + 11 slash commands + cross-platform hooks.
```

### Long description

```
SceneView is the cross-platform 3D + AR SDK for Jetpack Compose (Android), SwiftUI (iOS, macOS, visionOS), and Kotlin/JS (Web). It is designed as an AI-first SDK: every API, doc, and sample is shaped so that AI assistants generate correct, compilable code on the first try.

This Claude Code plugin bundles:

- The sceneview-mcp server (28 tools, full API reference, 33 compilable code samples, code validator). When you ask Claude "build me an AR app with tap-to-place furniture", you get working Compose / SwiftUI / Kotlin-JS code on the first try.

- 11 namespaced slash commands that automate the SceneView contribution flow: /sceneview:contribute, /sceneview:release, /sceneview:review, /sceneview:test, /sceneview:document, /sceneview:quality-gate, /sceneview:publish-check, /sceneview:sync-check, /sceneview:version-bump, /sceneview:evaluate, /sceneview:maintain.

- Cross-platform reminder hooks that fire on edits to remind the dev to keep API parity between Android (Filament), iOS (RealityKit), Web (Filament.js), Flutter, and React Native bridges.

The same marketplace also publishes 4 vertical MCP bridges (real estate, French admin, e-commerce 3D, architecture) that pair with SceneView or work standalone.

Open-source MIT license. Marketplace manifest at /.claude-plugin/marketplace.json in the repo.
```

### Categories / tags

```
development, 3d, ar, android, ios, web, jetpack-compose, swiftui, mcp
```

### Author / maintainer

- **Name:** SceneView
- **GitHub org:** https://github.com/sceneview
- **Contact email:** (use the public org email — never expose the maintainer's personal email per CDI-safety rules)

### License

```
MIT
```

### Support / issues

```
https://github.com/sceneview/sceneview/issues
```

## Pre-submission checklist

Before clicking **Submit**:

- [ ] All 6 manifests pass `claude plugin validate` cleanly (run from repo root: `claude plugin validate .` then each `claude plugin validate plugins/<name>`)
- [ ] `npm view sceneview-mcp version` returns the expected version (currently 4.0.11) — the plugin install pulls @latest, so the wrapped MCP must be live and current
- [ ] Marketplace `.claude-plugin/marketplace.json` is reachable on `main` (curl https://raw.githubusercontent.com/sceneview/sceneview/main/.claude-plugin/marketplace.json)
- [ ] `claude plugin marketplace add sceneview/sceneview` works from a clean HOME (`HOME=/tmp/test claude plugin marketplace add sceneview/sceneview`)
- [ ] `claude plugin install sceneview@sceneview` succeeds in that clean env
- [ ] `/sceneview:contribute --help` (or any of the 11 commands) shows up in `/help` after install

## After submission

- Anthropic review typically takes 3–7 business days
- Once approved, plugin shows up in `/plugin discover`
- Recommended: publish a one-line "now in the official marketplace" update on the SceneView README + the website install banner
- Track adoption via the `sceneview-telemetry.mcp-tools-lab.workers.dev` worker (already wired into the MCP) — the `init` event fires on first MCP startup so plugin installs are countable

## If rejected

Common rejection reasons + fixes:

- **"Plugin name conflicts"** → bump to `sceneview-sdk` or `sceneview-toolkit`
- **"Missing license file"** → already MIT in repo root, link explicitly in resubmission
- **"MCP not auditable"** → point them at `mcp/src/` (open source) and the `mcp-strategy-report.md`
- **"Description too marketing-heavy"** → strip "AI-first" buzzwords, lead with the technical capability
