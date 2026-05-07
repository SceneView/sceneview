---
description: Verify all published artifacts (Maven Central, npm, SPM, GitHub Release) match the current versions.
---

# /publish-check — Verify all published artifacts are up to date

Check every platform where SceneView is published and compare with local version.

---

## Published Artifact Registry

| Artifact | Platform | Install command | Registry |
|---|---|---|---|
| sceneview | Maven Central | `implementation("io.github.sceneview:sceneview:X.Y.Z")` | Maven Central |
| arsceneview | Maven Central | `implementation("io.github.sceneview:arsceneview:X.Y.Z")` | Maven Central |
| sceneview-core | Maven Central | KMP shared module | Maven Central |
| sceneview-mcp | npm | `npx sceneview-mcp` | npmjs.com |
| @sceneview/sceneview-web | npm | `npm i @sceneview/sceneview-web` | npmjs.com |
| SceneViewSwift | SPM (GitHub tags) | `.package(url: "...", from: "X.Y.Z")` | git tags |
| GitHub Release | GitHub | Download page | GitHub Releases |
| Website | GitHub Pages | sceneview.github.io | Live site |
| Demo APKs | GitHub Release | Attached to release | GitHub Releases |

## Checks to run

### 1. Maven Central (3 artifacts)
```bash
echo "=== Maven Central ==="
for artifact in sceneview arsceneview sceneview-core; do
  V=$(curl -s "https://search.maven.org/solrsearch/select?q=g:io.github.sceneview+AND+a:$artifact&rows=1&wt=json" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['response']['docs'][0]['latestVersion'] if d['response']['docs'] else 'NOT FOUND')" 2>/dev/null)
  echo "  $artifact: $V"
done
```

### 2. npm (2 packages)
```bash
echo "=== npm ==="
for pkg in sceneview-mcp @sceneview/sceneview-web; do
  V=$(curl -s "https://registry.npmjs.org/$pkg/latest" | python3 -c "import sys,json; print(json.load(sys.stdin).get('version','NOT FOUND'))" 2>/dev/null)
  echo "  $pkg: $V"
done
```

### 3. GitHub Release & Tags
```bash
echo "=== GitHub ==="
LATEST_TAG=$(git tag -l 'v*' | sort -V | tail -1)
echo "  Latest local tag: $LATEST_TAG"
gh release view "$LATEST_TAG" --json tagName,publishedAt 2>/dev/null || echo "  No GitHub release for $LATEST_TAG"
```

### 4. Local version
```bash
echo "=== Local ==="
LOCAL_V=$(grep '^VERSION_NAME=' gradle.properties | cut -d= -f2)
echo "  gradle.properties: $LOCAL_V"
MCP_V=$(python3 -c "import json; print(json.load(open('mcp/package.json'))['version'])")
echo "  mcp/package.json: $MCP_V"
```

### 5. Website live check
```bash
echo "=== Website ==="
HTTP=$(curl -s -o /dev/null -w "%{http_code}" https://sceneview.github.io/)
echo "  sceneview.github.io: HTTP $HTTP"
SITE_V=$(curl -s https://sceneview.github.io/ | grep -o 'softwareVersion.*[0-9]\+\.[0-9]\+\.[0-9]\+' | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1 || echo "N/A")
echo "  Website version: $SITE_V"
```

### 6. Release workflow status
```bash
echo "=== Release Workflow ==="
gh run list --workflow=release.yml --limit 3 --json conclusion,createdAt,displayTitle
```

### 7. Compare and report

Print a summary table:

| Artifact | Published | Local | Status |
|---|---|---|---|
| sceneview (Maven) | X.Y.Z | X.Y.Z | OK / BEHIND / AHEAD |
| arsceneview (Maven) | X.Y.Z | X.Y.Z | OK / BEHIND / AHEAD |
| sceneview-core (Maven) | X.Y.Z | X.Y.Z | OK / BEHIND / AHEAD |
| sceneview-mcp (npm) | X.Y.Z | X.Y.Z | OK / BEHIND / AHEAD |
| sceneview-web (npm) | X.Y.Z | X.Y.Z | OK / BEHIND / AHEAD |
| SceneViewSwift (tag) | vX.Y.Z | X.Y.Z | OK / BEHIND / AHEAD |
| GitHub Release | vX.Y.Z | X.Y.Z | OK / BEHIND / AHEAD |
| Website | X.Y.Z | X.Y.Z | OK / BEHIND / AHEAD |

### 8. Actionable recommendations

For each artifact that is BEHIND:
- Maven: "Run `/release` to trigger release.yml which publishes to Maven Central"
- npm MCP: "Run `cd mcp && npm publish --access public`"
- npm web: "Publish is handled by release.yml on tag push"
- SPM: "Push a new git tag: `git tag vX.Y.Z && git push origin vX.Y.Z`"
- GitHub Release: "Create release: `gh release create vX.Y.Z --generate-notes`"
- Website: "Push to `website-static/` triggers docs.yml deployment"

**NEVER say "everything is published" without actually checking the APIs above.**
