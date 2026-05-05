# Session handoff — 2026-05-06 — Pixel 9 review v3

## Pourquoi ce handoff

La session du 2026-05-05 (`busy-pasteur-477082`) a livré 2 vagues d'agents Opus parallèles pour fixer les retours Pixel 9 review (mode scribe). Les 19 commits sont **pushés sur `origin/claude/tender-haibt-6062c7`**. Demain, reprendre la review live pour valider les fixes vague 2 et lancer une éventuelle vague 3 sur les bugs résiduels.

## État au moment du handoff (2026-05-05 21:00)

### Worktree
`/Users/thomasgorisse/Projects/sceneview/.claude/worktrees/tender-haibt-6062c7/`
Branch : `claude/tender-haibt-6062c7` — **122 commits ahead de main**, **pushée sur origin** (push réussi malgré le compte `thomasgorisse` suspendu — credential helper a un token actif).

### Notes de review live
- `.claude/pixel9-review-2026-05-05.md` — section v1 (review initiale 9 demos) + section v2 (re-review post vague 1)

### Vague 1 commits (8) — 2026-05-05 ~19h
```
38c2842d fix(android-demo): animation — gestures unlock cinematic shots, add Model header
42f1f4ad feat(android-demo): add Axes3DNode utility
356259e2 feat(android-demo): gesture-editing — axes gizmo, sensitivity sliders, live transform readout
250cc959 feat(android-demo): video — mute toggle + cinematic camera + creative surfaces
fc53ab58 feat(android-demo): debug-overlay — auto-fit camera + progressive spawn + perf graph + stress test
ea3789f1 fix(ar): face — make mesh visible + tame washed-out selfie exposure
77a6ca99 fix(ar): streetscape — fall back to plain AR when geospatial is unsupported
36a51c3f fix(ar): pose — tame washed-out rear-camera exposure on Pixel 9
```

### Vague 2 commits (11) — 2026-05-05 ~20h
```
79216327 fix(android-demo): animation v2 — default Reveal+Walk, fix gesture lock, raise low cinematic angles
22e9ee2e fix(sceneview): isolate per-node editing from camera gesture          [LIB]
791425fd fix(android-demo): gesture-editing v2 — clearer axes + docstring update
d787dd90 fix(android-demo): video v2 — make surface picker and cinematic camera actually apply at runtime
fa10d263 fix(android-demo): debug-overlay v2 — single-sphere framing, smooth dolly, stress-test crash
35aca032 fix(android-demo): streetscape — link Google Fused Location Provider
19f464a2 fix(ar): face mesh — opaque diffuse + explicit fill light             [LIB AugmentedFaceNode]
1cfdd7dc fix(android-demo): pose — matte materials + Blender-style axes gizmo
f33d8a91 fix(android-demo): ar-placement v2 — camera exposure + visual gesture indicator
4e37cfdb fix(sceneview): pinch zoom — softer default, configurable speed/damping, optional FOV mode  [LIB]
173c991d feat(android-demo): ar-rerun v2 — UX overhaul with intro, live stream stats, help dialog
3c727f43 docs(review): 2026-05-05 Pixel 9 v2 review notes (post-vague-1 fixes)
```

### Lib touches (à signaler aux releases futures)
- `sceneview/.../Scene.kt` — gesture isolation pour nodes éditables (commit `22e9ee2e`)
- `sceneview/.../gesture/CameraGestureDetector.kt` — pinch sensibilité + FOV mode (commit `4e37cfdb`)
- `arsceneview/.../node/AugmentedFaceNode.kt` — log diagnostic + face mesh material (commit `19f464a2`)

### APK
APK debug ready : `samples/android-demo/build/outputs/apk/debug/android-demo-debug.apk` (272 MB).
**Pas reflashée pour la review v3** — la session s'est arrêtée juste avant le re-flash. La derniere flash sur Pixel 9 contenait uniquement les fixes vague 1 (commits jusqu'à `36a51c3f`).

### Pixel 9
- Wireless debug instable — toggle off/on requis souvent
- Mémoire device : ARCore + permissions caméra/internet déjà OK
- Helper `/tmp/p9-review/v2-*.png` — captures v2 de la session

## Ce qui reste à faire demain

### Phase 1.6 — Re-review v3 (mode scribe)
Reflasher l'APK avec la vague 2 puis re-tester les 9 demos en mode scribe.

Workflow rappelé :
1. Build : `cd /Users/thomasgorisse/Projects/sceneview/.claude/worktrees/tender-haibt-6062c7 && ./gradlew :samples:android-demo:assembleDebug`
2. Connect Pixel 9 : `adb mdns services` puis `adb connect <IP>:<port>`
3. Install : `adb install -r -t samples/android-demo/build/outputs/apk/debug/android-demo-debug.apk`
4. Pour chaque demo : `adb shell am force-stop io.github.sceneview.demo && adb shell am start -n io.github.sceneview.demo/.MainActivity --es demo <id>`
5. Capture en parallèle : `adb exec-out screencap -p > /tmp/p9-review/v3-XX-<demo>.png`
6. Note les retours dans `.claude/pixel9-review-2026-05-05.md` section v3

Ordre review :
1. animation — vérifier default Reveal+Walk + manipulator hot-swap + camera angles
2. ar-placement — vérifier exposure + gesture indicator + zoom plus doux
3. gesture-editing — vérifier isolation gestures vs camera (KEY fix lib)
4. video — vérifier surface picker (Plane/Cube/Reflective visibles) + cinematic vs orbit (deltas plus visibles)
5. debug-overlay — vérifier single-sphere framing OK + smooth dolly + stress test no crash (cap 2000)
6. ar-face — vérifier face mesh visible (opaque diffuse + light explicite)
7. ar-pose — vérifier matériaux mat + axes XYZ branchés
8. ar-streetscape — vérifier feed caméra OK (dépendance Fused Location ajoutée)
9. ar-rerun — vérifier About card + Stream Stats + Help dialog

### Phase 2 — vague 3 si bugs résiduels
Selon la re-review, lancer agents Opus parallèles à nouveau. Probable focus : ar-face (bug lib profond, peut-être fixé peut-être pas), perf SDK sphere geometry (45 MB pour 5000 nodes — partage de geometry à investiguer côté lib).

### Phase 3 — Cleanup main + merge tender-haibt
**À FAIRE QUAND MAIN SERA CLEAN.** Aujourd'hui main = 191 fichiers dirty (autres sessions parallèles, mcp-gateway, mcp/packages, etc.) + 23 conflits dont `handoff.md` (mémoire `session_2026-05-04_zen-blackburn_audit.md`). NE PAS tenter le merge tant que main n'est pas nettoyé.

Conditions de re-merge :
- Main clean (0 dirty)
- 0 conflit avec `claude/tender-haibt-6062c7`
- Une session dédiée pour résoudre les conflits manuels
- Vérifier qu'aucune autre session n'a touché aux mêmes fichiers de demo

### Phase 4 — Optionnel
- PR GitHub : URL fournie par le push `https://github.com/sceneview/sceneview/pull/new/claude/tender-haibt-6062c7`
  - ⚠️ **NE PAS créer la PR sans validation explicite de Thomas** (mémoire `feedback_no_pr_burst.md` : max 1 PR externe/semaine, soumission manuelle)
  - Le repo `sceneview/sceneview` n'est pas externe (c'est le repo perso) mais reste prudent

## Règles importantes

- ⛔ Compte `thomasgorisse` GitHub suspendu, `thomasgorisse-dev` est le stopgap
- ⛔ NE PAS toucher main worktree (191 fichiers dirty d'autres sessions)
- ⛔ NE PAS lancer full UI test suite
- ⛔ Author email git = `thomas.gorisse@gmail.com` (mémoire `feedback_git_email`)
- ✅ Build via `./gradlew :samples:android-demo:assembleDebug`
- ✅ Drive backup vert (mémoire `project_drive_backup_verified.md`) — branche safe localement
- ✅ Push autorisé (le credential helper fonctionne)

## Reprise de session — prompt à coller

```
Reprise session SceneView — Phase 1.6 Pixel 9 review v3 (mode scribe).

Lis d'abord ce fichier pour le contexte complet :
.claude/worktrees/tender-haibt-6062c7/.claude/SESSION_HANDOFF_2026-05-06.md

Workflow : reflashe l'APK puis lance les 9 demos une à une, je dicte les retours.
Worktree = .claude/worktrees/tender-haibt-6062c7
Branche = claude/tender-haibt-6062c7 (à jour sur origin)

Pixel 9 wireless debug : `adb mdns services` puis `adb connect <IP>:<port>`.
Si déphasage APK, rebuild via ./gradlew :samples:android-demo:assembleDebug puis adb install -r -t.

On commence par : animation. À toi.
```
