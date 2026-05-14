# Contributing to SceneView

Thanks for your interest in contributing! This guide covers everything you need to get started.

---

## Development environment setup

### Prerequisites

- **JDK 17** (for Android/KMP modules)
- **Android Studio** (latest stable recommended)
- **Xcode 15+** (for SceneViewSwift / iOS work only)
- Optional but recommended: Google's [`android` CLI](https://developer.android.com/tools/agents/android-cli)
  for agent-driven QA. Bootstrap in one shot:
  ```bash
  bash .claude/scripts/android-env-check.sh --fix
  ```
  This installs the binary to `~/.local/bin/android` and registers the SceneView
  agent skill under `~/.android/cli/skills/xr/sceneview/`, so `android skills list`
  exposes it to any AI agent on this host.

### Clone and open

```bash
git clone https://github.com/sceneview/sceneview.git
cd sceneview
```

Open the project in Android Studio. Gradle sync will pull all dependencies automatically.

### Build

```bash
# Android libraries
./gradlew assembleDebug

# Android demo app
./gradlew :samples:android-demo:assembleDebug
```

For iOS (SceneViewSwift), open `SceneViewSwift/Package.swift` in Xcode and build from there.

### Run tests

```bash
# All tests
./gradlew test

# KMP core tests only
./gradlew :sceneview-core:allTests
```

### Set up an emulator

Google's `android` CLI creates and boots emulators with one command — no
sdkmanager / avdmanager dance:

```bash
android emulator create medium_phone        # positional <profile>; device auto-named from it
android emulator start medium_phone         # boots, waits for ready
```

`android emulator create` takes a single positional argument — the **profile**
— and the resulting device is auto-named from the profile (so `medium_phone`
above creates a device called `medium_phone`). v0.7 does not support a
separate `--name` flag. List profiles with `android emulator create --list-profiles`,
list existing devices with `android emulator list`, remove with
`android emulator remove <name>`.

The `medium_phone` profile matches the Pixel-7-class form factor most of the
screenshot scripts assume.

For SDK packages, prefer `android sdk install` / `android sdk list` over the
legacy `sdkmanager` from `cmdline-tools`.

---

## AI-assisted workflow (recommended)

SceneView ships with a full Claude Code setup so you can contribute with AI assistance
from the first keystroke — no context-gathering needed.

### Quick start

1. Install [Claude Code](https://claude.ai/code)
2. Clone the repo and open it: `claude` inside the project root
3. Run `/contribute` — Claude walks you through the entire workflow

See [CLAUDE.md](CLAUDE.md) for the full module map, architecture overview, threading rules, and AI contributor guidelines.

### Available slash commands

| Command | What it does |
|---|---|
| `/contribute` | Full guided workflow from understanding to PR |
| `/review` | Checks threading rules, Compose API, Kotlin style, module boundaries |
| `/document` | Generates/updates KDoc and `llms.txt` for changed APIs |
| `/test` | Audits coverage and generates missing tests |

### MCP server (optional)

If you use Claude Desktop or another MCP-compatible editor, add the SceneView MCP server
for full API context in any chat:

```json
{
  "mcpServers": {
    "sceneview": { "command": "npx", "args": ["-y", "sceneview-mcp"] }
  }
}
```

---

## Pull request guidelines

1. **One feature per PR.** Keep changes focused and reviewable.
2. **Tests required.** Add or update tests for any behavior change.
3. **Follow existing code style.** Match the patterns in the module you are editing.
4. **Describe the why.** PR descriptions should explain the motivation, not just list changed files.
5. **Keep commits clean.** Squash fixups before requesting review.

Contributions to any part of the project are welcome — Android (`sceneview/`, `arsceneview/`), iOS (`SceneViewSwift/`), shared KMP core (`sceneview-core/`), samples, documentation, or the MCP server.

After your changes are merged, the Discord bot will award you the **Contributor** role.

### CI on docs-only PRs

**Docs-only PRs** — changes confined to `*.md`, `docs/**`, `website-static/**`,
`marketing/**`, `branding/**`, `llms*.txt`, or `LICENSE` — skip the Android +
MCP `quality-gate.yml`, `ci.yml`, and `render-tests.yml` checks by design.
The diff cannot affect runtime behaviour, so spending 10-20 min of emulator
time to re-render the same screenshots is pure noise. You will see fewer
green checks than on a code PR; this is correct.

If your docs PR needs to force a full CI run (for example, you suspect a
markdown change has accidentally invalidated an example referenced from
runtime code), trigger the gates manually from the Actions tab —
`Run workflow` on `quality-gate.yml` / `ci.yml` / `render-tests.yml`
accepts your PR's branch as input.

### Code style

- **Kotlin**: follow the official [Kotlin style guide](https://developer.android.com/kotlin/style-guide) and existing Compose API conventions (composable functions, `remember*` helpers, named parameters). The code style is stored in the repository and auto-configured by Android Studio.
- **Swift**: follow the existing SceneViewSwift patterns (builder-style modifiers, RealityKit conventions).
- No wildcard imports. No unused imports.
- Keep changes minimal — you can fix obvious mistakes in formatting or documentation along the way.

### Changes in Filament materials

Recompile Filament materials using the [current Filament version](https://github.com/google/filament/releases) if you modify them. Enable the [Filament plugin](https://github.com/sceneview/sceneview/blob/main/gradle.properties) and build.

#### Filament runtime ↔ `.filamat` ABI invariant

> **The Filament runtime version (in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) → `filament = "X.Y.Z"`) and the `matc` toolchain that produced every committed `.filamat` blob MUST be the same major version.**

Filament refuses any material whose binary version field does not match the runtime, with `Filament panic — material version N ≠ runtime M` on first frame. There is no compile-time check; the mismatch only manifests at runtime, demo by demo. v4.1.0 shipped with the runtime at 1.70.2 and blobs at 1.71 (two parallel branches each fixed half of the pair) — 10 demos crashed; v4.1.1 hot-fixed by realigning both sides to 1.71.

**The 12 committed blobs that must be recompiled together** (under `sceneview/src/main/assets/materials/`):

```
image_texture.filamat                  transparent_colored.filamat
opaque_colored.filamat                 transparent_textured.filamat
opaque_textured.filamat                transparent_unlit_colored.filamat
opaque_unlit_colored.filamat           video_texture.filamat
view_renderable.filamat                video_texture_chroma_key.filamat
view_texture_lit.filamat               view_texture_unlit.filamat
```

**How to recompile after a Filament version bump:**

1. Download the matching `matc` from the Filament release tarball — `https://github.com/google/filament/releases/tag/vX.Y.Z` → `filament-vX.Y.Z-mac.tgz` (or `-linux.tgz`) → `./bin/matc`.
2. Put `matc` on your `PATH` (or update [`tools/GenerateFilamat.sh`](tools/GenerateFilamat.sh) to point at it).
3. Run `cd tools && bash GenerateFilamat.sh` — recompiles every `.filamat` from its `.mat` source.
4. Commit **the runtime bump in `gradle/libs.versions.toml` AND the recompiled `.filamat` files in the SAME PR**. Never split them across commits — that's the failure mode that broke v4.1.0.

If you bump the runtime without touching the blobs (or vice versa), CI will not catch it. The first signal is a runtime crash on whichever demo loads the affected material first.

---

## Issues and discussions

- **Bug reports**: use the issue templates on [GitHub Issues](https://github.com/sceneview/sceneview/issues). Include platform, SceneView version, minimal reproduction steps, and relevant logs.
- **Questions**: open a [Discussion](https://github.com/sceneview/sceneview/discussions) instead of an issue.
- **Feature requests**: welcomed as issues or discussions.
- **Chat**: join the [Discord](https://discord.gg/UbNDDBTNqb) to talk with the community and maintainers.
