# Deep links + QR codes — site / README / docs ↔ demo apps

> Drafted 2026-05-06 (worktree `gifted-kowalevski-1753ec`).
> Goal: scan a QR code on the SceneView website, GitHub README, or docs
> and **land directly on the matching demo screen** in the installed
> Android / iOS app — falling back to the store if not installed.

## Why

Today every demo lives behind two clicks: install the app, then navigate
through the demos list. Discoverability suffers — visitors who read about
"AR Debug (Rerun)" on the website never get to **see** it on their own
device unless they manually install the app and go fishing.

A QR-code-driven deep link removes the gap:

```
README screenshot → [tiny QR] → Android app installed?
                                 ├─ yes → opens AR Rerun demo directly
                                 └─ no  → Play Store install page (with deferred deep link)
```

## Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│ Website / README / docs                                             │
│ embed a QR code that points at:                                     │
│ https://sceneview.github.io/open?demo=ar-rerun                      │
└─────────────────────────────────────┬───────────────────────────────┘
                                      │ scan
                                      ▼
┌──────────────────────────────────────────────────────────────────────┐
│ sceneview.github.io/open/index.html                                 │
│ - JS sniffs UA → mobile (iOS / Android) or desktop                  │
│ - Mobile: tries `sceneview://demo/<id>` AND verified App-Link       │
│   `https://sceneview.github.io/open?demo=<id>` simultaneously       │
│ - 1.5s timeout → fallback to Play Store / App Store                 │
│ - Desktop: render an <SV-DEMO-QR> with the **same** URL so the      │
│   reader scans it with their phone                                  │
└─────────────────────────────────────┬───────────────────────────────┘
                                      │ open scheme / app link
                                      ▼
┌──────────────────────────────────────────────────────────────────────┐
│ Android (samples/android-demo)                                       │
│ AndroidManifest.xml — MainActivity gains intent-filters:             │
│   * sceneview://demo/<id>          (custom scheme, no asset links)   │
│   * https://sceneview.github.io/open?demo=<id>  (App-Links verified  │
│     via /.well-known/assetlinks.json on github.io)                   │
│ MainActivity.handleIntent() reads `intent.data` (last path segment   │
│ for custom scheme, queryParameter("demo") for HTTPS) and pushes the  │
│ matching screen using DemoRegistry IDs (already stable).             │
└──────────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────────┐
│ iOS (samples/ios-demo)                                               │
│ Info.plist:                                                          │
│   * CFBundleURLTypes → "sceneview" scheme                            │
│   * Associated Domains entitlement → applinks:sceneview.github.io    │
│ /.well-known/apple-app-site-association on github.io for Universal   │
│ Links verification.                                                  │
│ ContentView.onOpenURL { handleDemoUrl($0) }                          │
└──────────────────────────────────────────────────────────────────────┘
```

## Pre-reqs blocking shipment

1. **Android Play Store package id** — confirm the published id (likely
   `io.github.sceneview.demo` from `samples/android-demo/build.gradle`).
   Needed for the `intent://#Intent;package=…;end` fallback in the open
   page.
2. **iOS App Store ID + bundle id** — needed for the App Store fallback
   URL `itms-apps://itunes.apple.com/app/idXXXX`. Check
   `samples/ios-demo/SceneViewDemo.xcodeproj/project.pbxproj`.
3. **App-Links / Universal-Links verification files** must be served
   from the production origin:
   - `https://sceneview.github.io/.well-known/assetlinks.json`
   - `https://sceneview.github.io/.well-known/apple-app-site-association`
   The format is well-known; values come from the package id +
   keystore SHA-256 (Android) and `<TEAM_ID>.<bundle-id>` (iOS).

## Subtasks

| # | Task | Effort |
|---|------|--------|
| 1 | Pick canonical scheme + URL: `sceneview://demo/<id>` + `https://sceneview.github.io/open?demo=<id>` | 0 (decided) |
| 2 | Build `website-static/open/index.html` — UA sniff, scheme attempt, 1.5s timeout, store fallback, embeds `<SV-DEMO-QR>` for desktop visitors | ~3h |
| 3 | Drop in a tiny QR generator (`qrcode-generator` ~2kB, no DOM lib) and a custom element `<sv-demo-qr demo="…">` placed via slot in any page | ~1h |
| 4 | Android `AndroidManifest.xml` intent-filters (custom + autoVerify HTTPS), `MainActivity.handleIntent` resolving `demo=<id>` → composable, plus a `pendingDemo` saved-state key for cold-start | ~1.5h |
| 5 | `samples/android-demo` Compose nav: route from MainActivity to demos by id, supporting deep entry without going through the demos list | ~1.5h |
| 6 | iOS `Info.plist` URL types + Associated Domains entitlement + `onOpenURL` in `SceneViewDemoApp` + `DemoRouter` switch | ~1.5h |
| 7 | Generate + commit `assetlinks.json` and `apple-app-site-association` (real SHA-256 + TEAM_ID — Thomas to confirm both) | ~30min + Thomas inputs |
| 8 | Place QR codes on website index Showcase cards, AR Rerun viewer page, README "Try it now" snippets, docs/quickstart, sample Recipes | ~1h |
| 9 | E2E test: scan QR with both phones (installed & not installed), confirm fallback path is correct, confirm cold-start path works | ~30min |

**Total: ~10 hours of focused work** (Thomas-blocked items: Play Store id, App Store id, keystore SHA-256, TEAM_ID).

## Open questions for Thomas

- Single demo id namespace (`ar-rerun`, `geometry`, `model-viewer` …) — these already exist in `DemoRegistry`. We reuse them as-is, no separate URL slug. ✅ confirmed by reading code.
- "Discreet" QR placement on the website: under each demo card, in the bottom-right corner with a 32×32 SVG. On the README, under each section heading. On `samples/recipes/*.md` snippets, under the code block. Any preference?
- Do we ship `sceneview://demo/<id>` only at first (zero-config, works without store verification) and add App-Links verification in a follow-up? That cuts pre-reqs to just the Play Store id (for fallback), and makes the first PR much smaller.

## Recommended first PR

A minimal, shippable slice:

1. Custom scheme only — no App-Links verification.
2. `MainActivity` + `Info.plist` deep-link routing.
3. `website-static/open/index.html` page with UA sniff and Play / App Store fallback.
4. QR codes generated client-side via `qrcode-generator` on:
   - The Showcase cards on `index.html`
   - The Rerun viewer page (`website-static/rerun/index.html`) — pointing at `?demo=ar-rerun`
   - The Android demo's About tab
5. **No** `assetlinks.json` / `apple-app-site-association` yet. Track it as a follow-up labelled "verified deep links".

That's ~5 hours. Cleanly invertible — App-Links can be layered on later
without breaking anything.
