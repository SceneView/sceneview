---
description: Review staged/unstaged changes — threading, Compose API, style, cross-platform parity.
---

Review the staged/unstaged changes in this repository for a SceneView contribution. Run the full checklist below, then output a structured report.

## Steps

1. Run `git diff HEAD` to see all changes (staged and unstaged).
2. Run `git diff --name-only HEAD` to list changed files.
3. Read each changed source file in full.
4. Read `CLAUDE.md` and `llms.txt` for context on APIs and threading rules.

## Review checklist

### Threading (critical)
- [ ] No `modelLoader.createModel*` or `materialLoader.*` calls on a background coroutine (`Dispatchers.IO`, `withContext(Dispatchers.IO) { }`, `launch(Dispatchers.IO) { }`)
- [ ] Async model loading uses `rememberModelInstance` in composables or `loadModelInstanceAsync` in imperative code
- [ ] All Filament JNI calls are on the main thread

### Compose API
- [ ] Nodes declared as composables inside `SceneView { }` / `ARSceneView { }` content blocks — not via `childNodes = rememberNodes { }`
- [ ] `rememberModelInstance` null case is handled (`?.let { }` or null check before using the instance)
- [ ] `LightNode`'s `apply` is a **named parameter** (`apply = { ... }`), not a trailing lambda
- [ ] No manual `addChildNode` / `removeChildNode` calls — hierarchy expressed via nested composables

### Kotlin style
- [ ] Follows [Kotlin style guide](https://developer.android.com/kotlin/style-guide)
- [ ] No unnecessary `!!` (non-null assertion) — prefer safe calls or `let`
- [ ] Public API functions have KDoc comments
- [ ] Changes are minimal — no unrelated reformatting

### Module boundaries
- [ ] Changes in `sceneview/` don't import from `arsceneview/`
- [ ] AR-specific code lives in `arsceneview/`, not in core `sceneview/`

### Filament materials
- [ ] If any `.mat` / `.matc` file is changed, the commit includes the recompiled binary

## Output format

Produce a report with:
- **PASS / FAIL / WARN** for each checklist item that applies to the diff
- A short explanation for any FAIL or WARN
- A recommended action for each issue found
- An overall verdict: ✅ Ready to merge | ⚠️ Needs changes | ❌ Blocking issues

Be concise. Skip checklist items that are not relevant to the diff.
