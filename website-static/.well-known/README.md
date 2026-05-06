# `.well-known/` — verified deep-link manifests

These two files turn `https://sceneview.github.io/open?demo=<id>` into a
**verified** App-Link / Universal-Link, so a single QR code or HTTPS URL
opens the demo app **without** the Android "Open with" picker / iOS
disambiguation popup.

## `assetlinks.json` — Android App-Links

Lists the Android packages allowed to handle `https://sceneview.github.io/`
URLs. Two `sha256_cert_fingerprints` are listed:

1. **`96:AC:55:C2:1B:74:87:4E:…:9C:2A`** — the **Play App Signing key**
   used by Google to sign every build distributed via the Play Store.
   This is the fingerprint that matters for users who installed the app
   from Google Play, and it was copied from
   [Play Console → SceneView Demo → Test and release → App integrity →
   App signing][playconsole] under "App signing key certificate".
2. **`5E:59:9D:AA:62:74:BC:DF:…:6D:8C`** — the **upload key** of
   `samples/android-demo` (alias `sceneview`, see
   `project_play_store_signing.md`). Lets App-Links verify on local
   debug builds installed via `adb install` / `assembleDebug` so
   developers don't see different behaviour from real users.

Both are public artifacts of public certificates — the **private** keys
are not in this file (they live in `~/sceneview-upload.jks` for the
upload key, and on Google's signing service for the App Signing key).

[playconsole]: https://play.google.com/console

After updating the file, also flip `android:autoVerify="true"` on the
HTTPS intent-filter in `samples/android-demo/src/main/AndroidManifest.xml`
(currently commented). Verify with:

```bash
adb shell pm verify-app-links --re-verify io.github.sceneview.demo
adb shell pm get-app-links io.github.sceneview.demo
# Expected output: "sceneview.github.io: verified"
```

## `apple-app-site-association` — iOS Universal Links

Wires `https://sceneview.github.io/open?demo=<id>` to the iOS
**SceneView** app (App Store id `6761329763`, bundle
`io.github.sceneview.demo`, TEAM_ID `5G3DZ3TH45`).

> ⚠️ **Action required for the file to apply** — Apple needs the
> Associated Domains entitlement on a re-signed build:
>
> 1. In `samples/ios-demo/SceneViewDemo.xcodeproj`, add
>    `applinks:sceneview.github.io` to the project's Associated Domains.
> 2. Re-archive + upload to App Store Connect.
> 3. iOS fetches AASA on first install or after an OS upgrade — so
>    existing 1.0 installs won't see Universal Links until they update.

The custom scheme `sceneview://demo/<id>` (already wired in
`Info.plist > CFBundleURLTypes`) keeps working with the current 1.0
build — no app update required to support QR codes today.

## Serving notes

GitHub Pages serves `.well-known/*` with `Content-Type` based on file
extension. Apple expects `apple-app-site-association` (no extension) to
be served as `application/json`; GitHub Pages does this correctly when
the file has no extension, which is why we ship it that way.

If you ever migrate off GitHub Pages, double-check both:

```bash
curl -I https://sceneview.github.io/.well-known/assetlinks.json
# → Content-Type: application/json

curl -I https://sceneview.github.io/.well-known/apple-app-site-association
# → Content-Type: application/json   (NOT text/plain)
```
