# Session handoff — 2026-05-05 — Pixel 9 review (scribe mode)

## Pourquoi ce handoff

La session précédente (focused-chaplygin) a livré la Phase 1 du refactor de samples (8 commits). Elle commence à saturer en contexte. Repart sur une session fraîche pour la review live des demos sur Pixel 9 sans hériter du bruit.

## État au moment du handoff

### Worktree
`/Users/thomasgorisse/Projects/sceneview/.claude/worktrees/tender-haibt-6062c7/`

Branch : `claude/tender-haibt-6062c7` — ahead de `main` de 101+ commits, push GitHub bloqué (compte `thomasgorisse` suspendu, ticket #4280656). Stratégie : push depuis `thomasgorisse-dev` quand l'accès aux orgs sera totalement configuré (Nikita a accordé l'accès à `sceneview` org le 2026-05-04, à confirmer pour `sceneview-tools`).

### Commits Phase 1 (déjà faits, ne PAS refaire)

```
8cf0c3cb docs(audit): expand 2026-05-04 findings with Pixel 9 AR results
fd702322 feat(android-demo): ar-placement — multi-model spawn + editable + Clear All
0c10d8f0 feat(android-demo): debug-overlay — interactive node spawner for stress testing
7f40282d feat(android-demo): video demo — Big Buck Bunny streaming + 3D scene + camera orbit
d2027ad0 feat(android-demo): gesture-editing — static reference sphere + gesture-target indicator
5c063409 feat(android-demo): animation demo — soldier + cinematic shots + Free hot-swap fix
14845c97 docs(audit): 2026-05-04 demo audit findings
4fef7867 feat(android-demo): deep-link via --es demo <id> for QA navigation
```

### Findings audit existants
Voir `.claude/audit-2026-05-04-findings.md` dans ce même worktree — contient les bugs/améliorations identifiés sur 7 demos classiques + 5 AR.

### Pixel 9
- IP wireless debug : `192.168.1.108:36809` (peut avoir changé depuis — utiliser `adb mdns services` puis `adb connect`)
- Permissions caméra + internet déjà accordées sur l'app
- ARCore pré-installé
- Helper `/tmp/record-demo.sh` existe pour record/extract frames

### AVD
Pixel_7a AVD existe localement, peut être bootée via :
```bash
nohup ~/Library/Android/sdk/emulator/emulator -avd Pixel_7a -no-snapshot-load -no-boot-anim </dev/null >/tmp/avd-boot.log 2>&1 &
disown
```
La règle harness : sans `nohup`/`disown` complets, le process se fait killer en fin de tâche bg.

## Ce qui reste à faire

### Phase 1.5 — Review Pixel 9 (mode scribe)

**Workflow demandé par Thomas :**
1. L'agent (toi) liste **une demo à la fois**
2. Thomas dicte ce qui ne va pas (il a déjà préparé sa liste sur son tel)
3. Tu résumes ses retours pour validation
4. Il confirme ou ajoute
5. Demo suivante seulement quand il valide
6. À la fin : développer **tous les fixes en bloc** via agents parallèles

**Stockage des retours** : dans un nouveau fichier `.claude/pixel9-review-2026-05-05.md` (ou similaire) — créer au fur et à mesure, NE PAS pousser dans la conversation pour économiser le contexte.

**Demos à reviewer (ordre proposé) :**
1. `animation` — Phase 1 refacto (Soldier + 5 cinematic + env rooftop_night + Free fix)
2. `ar-placement` — Phase 1 v2 (6-model cycle + editable + Clear All + counter pill)
3. `gesture-editing` — Phase 1 (sphere ref + gesture-target indicator pill)
4. `video` — Phase 1 (BBB W3Schools URL + 3D orbit + IBL studio_warm)
5. `debug-overlay` — Phase 1 (1000-nodes spawner + Reset)
6. `ar-face` — bug connu : tracking OK, **mesh invisible** (fix `35e5990d` compile-only). Pixel 9 confirme.
7. `ar-pose` — bug connu : sliders OK, **modèle invisible** (fix `a0da14d8` compile-only). Pixel 9 confirme.
8. `ar-streetscape` — bug connu : **écran noir total** (pas même le pill "Scanning environment..."). À retester dehors avec GPS solide.
9. `ar-rerun` — fonctionne (329 frames stream live). Manque visu côté user (le viewer Rerun est sur la machine de dev, pas sur le phone).

### Phase 2 — SDK fixes (après review Pixel 9)
- ar-face mesh rendering (CRITIQUE)
- ar-pose model rendering (CRITIQUE)
- ar-streetscape écran noir (à investiguer après retest extérieur)
- ar-rerun Rerun Web Viewer embed
- Editable toggle binding (gesture-editing)
- Reset Position no-op (gesture-editing)
- ModelInstance partageable entre Scenes (CRITIQUE — débloque PiP)
- setViewport hot-swap propagation (Scene.kt:352 SideEffect)

### Phase 3 — Gros refactors
- model-viewer P0 (model picker, IBL picker, background, AR button)
- secondary-camera PiP restoration
- collision cool features (drag-to-move, ray viz, hit info HUD)

### Phase 4 — Polish
- AR demos retest sur Pixel 9 (post Phase 2 fixes)
- Cleanup main (191 dirty files)
- Archiver handoff
- Consolider mémoires

### Idées business notées
- **SceneView Rerun-hosted server** payant via gateway Stripe (Free 100/mo, Pro 10k @ €9, Team 100k @ €29) — voir mémoire `project_rerun_hosted_idea.md`. À discuter, pas attaquer maintenant.

## Règles importantes pour la nouvelle session

- ⛔ NE PAS toucher au worktree `main` (191 fichiers dirty d'autres sessions parallèles, mémoire `feedback_no_data_loss`)
- ⛔ NE PAS push (compte GitHub `thomasgorisse` suspendu, `thomasgorisse-dev` setup à finaliser)
- ⛔ NE PAS lancer un full UI test suite (mémoire `feedback_no_full_ui_suite_per_commit`)
- ⛔ Author email git doit rester `thomas.gorisse@gmail.com` (mémoire `project_thomasgorisse_dev_account`)
- ✅ Build via `./gradlew :samples:android-demo:assembleDebug` (rapide avec cache)
- ✅ Install via `adb install -r -t samples/android-demo/build/outputs/apk/debug/android-demo-debug.apk`
- ✅ Deep-link demo via `adb shell am start -n io.github.sceneview.demo/.MainActivity --es demo <id>`
- ✅ Phone Pixel 9 wakefulness check via `adb shell dumpsys power | grep mWakefulness=` (Awake = OK, Dozing = écran verrouillé)

## Reprise de session — prompt à coller

(Voir le bloc "Prompt nouvelle session" dans le message du handoff côté chat)
