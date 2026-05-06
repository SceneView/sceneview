# Setting up ARCore Geospatial / Streetscape Geometry / Cloud Anchors

The android-demo `Streetscape Geometry`, `Cloud Anchors`, and any future Geospatial-based demo require an **ARCore Cloud API key** wired through the AndroidManifest. Without one, those specific demos disable themselves at runtime and surface a clear status message — the rest of the app (Face Mesh, AR Placement, Pose, plain camera AR, all 3D demos) keeps working.

## Why this is gated

Google's ARCore Geospatial / Streetscape / Cloud Anchors APIs hit a hosted backend. They:
- require an ARCore API key bound to your Android package + signing certificate
- require billing enabled on the Google Cloud project
- only return useful data in areas covered by Google Street View VPS

For maintainers / contributors who want to actually exercise the demo, the steps below take ~5 minutes.

## Provisioning your own key

### 1. Enable the ARCore API on a Cloud project

```
https://console.cloud.google.com/apis/library/arcore.googleapis.com
```

Pick (or create) a project, click **Enable**. ARCore API replaces the deprecated "ARCore Cloud Anchor API (Legacy)" — that one is end-of-life, do **not** enable it.

### 2. Activate billing on the project (required by Geospatial)

Geospatial mode hits paid endpoints (Google Cloud documents this explicitly). Without billing, `Session.configure(GeospatialMode.ENABLED)` succeeds but the backend returns no Streetscape geometries.

A defensive budget alert at €1/month is recommended:
```
https://console.cloud.google.com/billing/budgets
```

The free tier on ARCore is generous (thousands of calls/day for a single test device). Real-world risk for a developer testing locally is in the order of a few cents.

### 3. Create an API key restricted to this app

```
https://console.cloud.google.com/apis/credentials
→ Create credentials → API key
```

Restrict the key:
- **Application restrictions** → Android apps
  - Package name: `io.github.sceneview.demo`
  - SHA-1 fingerprint: your debug keystore's SHA-1 (`keytool -list -v -keystore ~/.android/debug.keystore -storepass android -alias androiddebugkey`) and/or your Play App Signing SHA-1 from Play Console.
- **API restrictions** → restrict key → ARCore API only.

### 4. Wire the key locally

Append to `local.properties` at the repo root (this file is gitignored):

```properties
ARCORE_API_KEY=AIzaSy...your-key-here...
```

That's it. `samples/android-demo/build.gradle` reads `ARCORE_API_KEY` from either:
1. `ARCORE_API_KEY` environment variable
2. `local.properties` → `ARCORE_API_KEY`
3. empty fallback

…and injects it into the `<meta-data android:name="com.google.android.ar.API_KEY">` placeholder in the manifest at build time.

### 5. (Optional) Wire the key in CI

For SceneView maintainers, the GitHub Actions secret `ARCORE_API_KEY` is read by the workflows that build the sample app:

- `play-store.yml` (release AABs uploaded to Play Store)
- `build-apks.yml`, `ci.yml`, `pr-check.yml`, `quality-gate.yml`, `render-tests.yml`

Forks and PRs from forks won't have the secret — the demo's runtime check disables Geospatial gracefully and the build still succeeds.

## What "no Streetscape geometry visible" means

Even with everything wired correctly, you'll see no overlay if any of these fail:

- **No Street View VPS coverage in your area.** Strasbourg city centre / Paris / NYC / Tokyo work; suburbs and rural areas often don't.
- **Indoor.** Geospatial needs a clear-ish sky view to compute its localization.
- **Tracking still initializing.** The first 5-30 seconds after launch the device is calibrating its IMU; the banner status will say "Looking for streetscape geometry…" or "Initializing geospatial…".

The status banner at the bottom of the demo distinguishes these cases — read it before assuming the integration is broken.

## Permissions

The demo asks for both `CAMERA` and `ACCESS_FINE_LOCATION` at runtime, gated through a single `RequestMultiplePermissions` flow. ACCESS_FINE_LOCATION is mandatory — `Session.configure(GeospatialMode.ENABLED)` throws `FineLocationPermissionNotGrantedException` otherwise. The composable holds a "Requesting permissions…" UI until both grants resolve.
