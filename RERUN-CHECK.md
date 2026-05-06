# 🔍 Rerun MVP — quick check guide

> Three commits on this worktree branch (oldest → newest):
> - `32586481` feat(rerun): self-serve hosted viewer page + Save & Share flow
> - `62731f44` chore(rerun): one-command review guide
> - `dbc055c6` feat(rerun): Swift parity, iOS share sheet, Tier-S events, viewer references
>
> Branch: `claude/gifted-kowalevski-1753ec` (worktree, not yet on `main`)
> Run **one** command at the bottom and you'll see everything in 60 seconds.

---

## ⚡ One-command check

```bash
bash .claude/scripts/check-rerun.sh
```

Starts a local HTTP server on **http://127.0.0.1:9090**, downloads a sample
`.rrd`, and opens **5 tabs** in your default browser:

1. The hosted viewer page **with a real recording loaded** (dna.rrd by Rerun)
2. The empty state of the viewer page (no `?url=` param)
3. Mobile preview of the viewer (375×812 simulated via DevTools)
4. The modified `ARRerunDemo.kt` on GitHub
5. The commit diff on GitHub

Stop the server with `Ctrl+C` when done.

---

## ✅ Visual checklist (page web)

Tab 1 should show:

- [ ] **Header**: "SV" gradient brand mark + "SceneView" name
- [ ] **Session metadata** displays "AR Session Viewer / Powered by Rerun.io"
  (no real metadata yet — that fills in once the recording carries a `.json`
  sidecar with `{title, device, durationSeconds, frameCount, trackingQuality}`)
- [ ] **Toolbar**: Download / Embed / Share / Theme-toggle buttons
- [ ] **Rerun viewer canvas** fills the rest of the screen and renders the
  DNA recording (rotating helix). _If the canvas is just black, it's a known
  limit of headless Chromium WebGPU — try a real desktop browser._
- [ ] **Theme toggle** flips dark ↔ light (top-right moon icon)
- [ ] **Share button** triggers system share sheet (mobile) or copies the
  page URL (desktop)
- [ ] **Embed button** copies an `<iframe …>` snippet to clipboard

Tab 2 (empty state) should show:

- [ ] Hero "AR Session Viewer" with the gradient brand
- [ ] Instructions hint: `?url=https://example.com/session.rrd`
- [ ] CTA buttons: "Get SceneView" + "rerun-3d-mcp"
- [ ] No Download / Embed buttons (only Share — bug-fixed in this commit)

---

## 🧪 End-to-end test (optional, ~5 min)

Validates the full Save & Share flow on a real device.

```bash
# 1. Install the sidecar deps
pip install rerun-sdk numpy

# 2. Launch the sidecar in SAVE mode
python samples/android-demo/tools/rerun-bridge.py --save
# → prints: [rerun-bridge] save mode -> will write /Users/you/.sceneview/recordings/<ts>.rrd

# 3. Plug an Android phone, install the demo
./gradlew :samples:android-demo:installDebug
adb reverse tcp:9876 tcp:9876

# 4. On the phone: open SceneView Demo → Samples → AR Debug (Rerun)
#    Tap "Connect" → film a few planes (move the phone)
#    Tap "Save & Share recording"

# 5. Sidecar prints:
#    [rerun-bridge] saved 234 events -> /Users/you/.sceneview/recordings/2026-05-06_…rrd
#    [rerun-bridge] open in browser -> https://sceneview.github.io/rerun/?url=file%3A%2F%2F…

# 6. Modal in the app shows path + viewer URL with Copy / Share buttons.
```

Browsers refuse `file://` from HTTPS pages, so step 6's URL only works once
you re-host the `.rrd` (R2, GitHub release, gist). That's the **next-session
backlog** — see `.claude/plans/` once it's drafted.

---

## 📁 What changed (commit `32586481`)

- **New** [website-static/rerun/index.html](website-static/rerun/index.html) — viewer page (480 LoC)
- **Modified** [arsceneview/.../RerunBridge.kt](arsceneview/src/main/java/io/github/sceneview/ar/rerun/RerunBridge.kt) — `requestSaveAndShare()` + reader coroutine + `ShareResult` data class
- **Modified** [arsceneview/.../RerunWireFormat.kt](arsceneview/src/main/java/io/github/sceneview/ar/rerun/RerunWireFormat.kt) — `controlSaveNow()` + `parseControlAck()` (hand-rolled JSON parser)
- **Modified** [arsceneview/.../RerunWireFormatTest.kt](arsceneview/src/test/java/io/github/sceneview/ar/rerun/RerunWireFormatTest.kt) — +4 control protocol tests
- **Modified** [samples/.../ARRerunDemo.kt](samples/android-demo/src/main/java/io/github/sceneview/demo/demos/ARRerunDemo.kt) — Save & Share button + dialog with copy / system share sheet
- **Modified** [samples/.../tools/rerun-bridge.py](samples/android-demo/tools/rerun-bridge.py) — `--save` mode + `{"type":"control","cmd":"save_now"}` protocol
- **Modified** [mcp/packages/rerun/src/python-sidecar.ts](mcp/packages/rerun/src/python-sidecar.ts) — generator emits the same flow + new `shareBaseUrl` option
- **Modified** [mcp/packages/rerun/src/python-sidecar.test.ts](mcp/packages/rerun/src/python-sidecar.test.ts) — +4 vitest tests
- **Modified** [llms.txt:1161](llms.txt:1161) — AR Debug section: live + save modes, Save & Share flow, control protocol API
- **Modified** [mcp/packages/rerun/README.md](mcp/packages/rerun/README.md) — Save & share section
- **Modified** [mcp/packages/rerun/llms.txt](mcp/packages/rerun/llms.txt) — control protocol mention

**47 Kotlin tests pass. 76 vitest tests pass. arsceneview release + android-demo debug compile clean.**

---

## ⏭️ Next session backlog

What's still pending after this session:

1. **QR-code deep links website ↔ app** — full plan saved at
   [.claude/plans/rerun-deeplinks-and-qr.md](.claude/plans/rerun-deeplinks-and-qr.md).
   ~5 h for the custom-scheme MVP, +5 h for verified App-Links. Needs
   Play Store id, App Store id, keystore SHA-256, TEAM_ID — listed at
   the top of the plan.
2. **R2 bucket + Worker upload signing** → makes the `?r=<hash>` URL
   pattern work end-to-end (sidecar uploads `.rrd` directly, app
   shows public URL instead of file://). ~0.5 day.
3. **MCP tool `share_recording`** in `rerun-3d-mcp` to upload an
   already-saved `.rrd` to the Worker / R2 bucket. ~1 h once #2 is
   live.
4. **Push to `main`** — branch is still local. Needs the pusher
   account (`thomas-gorisse`) since `thomasgorisse` is suspended.

Done in this session (no longer in backlog):

- ✅ `RerunBridge.swift` parity + iOS demo share sheet (commit `dbc055c6`)
- ✅ Tier-S camera-trail + scalar event types (commit `dbc055c6`)
- ✅ Viewer references on website / README / cheatsheet (commit `dbc055c6`)

---

## 🟥 Known limits

- Hosted viewer page is **not yet live on github.io** (worktree branch, not pushed to `main`). Use the local server to check.
- Page works in Chrome / Safari / Firefox desktop. Headless Chromium / sandboxed WebGPU previews render black — confirmed in both Claude Preview and Claude-in-Chrome MCP tools.
- The `.rrd` Rerun WebViewer accepts only files served with permissive CORS (`Access-Control-Allow-Origin: *`). R2 public buckets, GitHub releases, and Rerun's own CDN all qualify. Plain `file://` doesn't.
- Sidecar only writes the `.rrd` on disk — no cloud upload yet. R2 + signed-URL Worker is the next-session item.
