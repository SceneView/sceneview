<!-- category: Changed -->
- `validate-demo-assets.sh` now cross-checks every asset physically bundled under the demo asset roots against `assets/catalog.json` and fails CI if a bundled asset is undeclared, making catalog drift a build failure instead of a manual discovery (#1666).
