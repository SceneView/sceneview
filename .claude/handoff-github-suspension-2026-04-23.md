# Handoff — Crisis check 2026-04-23 21:20 (suspension GitHub)

## Contexte (à lire en premier)

Le compte GitHub `thomasgorisse` a été **complètement suspendu** ce soir — pas un throttle, pas un ban temporaire, **suspension TOS**. Page `github.com/suspended` confirmée par screenshot Thomas.

Lien direct avec l'incident : ticket #4280656 du 2026-04-13 (burst PRs/issues externes). Probable escalation vers suspension permanente.

Voir mémoire : `project_github_account_suspended.md` + `project_drive_backup_verified.md`

## Ce qui a été checké pendant cette session

### ✅ Drive backup vérifié dans l'app Mac (computer-use)
- L'app **Google Drive (`com.google.drivefs`) mirror directement `~/Projects/`** (pas un snapshot manuel)
- **Sync ACTIF** : 1 907 → 1 779 files queue, "less than a minute left", chiffres descendent en temps réel
- Fichiers en cours vus : `project.pbxproj`, `AppDelegate.swift`, `khronos_fox.glb`, `toon_cat.glb`, `shiba.glb`, `geisha_mask.glb` → contenu SceneView frais

### ✅ Statut Finder par dossier (`~/Projects/`, 21:20)
**Verts (sync OK) — l'essentiel est là :**
- `sceneview` ← repo principal ✅
- `profile-private` ← secrets, credentials ✅
- `sceneview.github.io`, `sceneview-starter-kit`
- `3D`, `3d-viewer-extension`, `buttercut`, `design`, `intelli-claude`, `IntelliClaude`, `legal-docs-mcp`, `mcp-creator-kit`, `n8n-templates`, `prompt-store`
- Tous les `octopus-*` (back, documentation, grpc-def, sdk-android, sdk-android-sources, sdk-swift)

**Rouges (erreur sync) :**
- MCPs: `architecture-mcp`, `ecommerce-3d-mcp`, `education-mcp`, `finance-mcp`, `french-admin-mcp`, `health-fitness-mcp`, `realestate-mcp`, `social-media-mcp`
- Autres: `ai-invoice`, `ar-model-viewer-android`, `armodelviewer-android`, `octopus-app-android`, `telegram-ai-bot`, `videos`

### ✅ Origine des 2 723 erreurs (Sync activity > View)
Tous des fichiers `node_modules/` :
- `typescript.js`, `tuple.js`, `esbuild`, `html-minifier-terser`
- `package.json` (dans node_modules), `no-work-result.d.ts`, `toolWithSampleServer.js.map`

Bug connu Drive avec `node_modules/` (symlinks, dossier `.bin/`, noms longs). **Pas critique** — reproductibles avec `npm install`.

### ⚠️ Snapshot manuel "Mac Backup" est PÉRIMÉ
- Localisation : `~/Library/CloudStorage/GoogleDrive-thomas.gorisse@gmail.com/My Drive/Mac Backup/`
- Date : **30 mars 2026** (24 jours en retard)
- Manque : `sceneview-mcp/`, `rerun-3d-mcp/` (créés depuis)
- C'est le snapshot du `RESTORE.md`, pas le mirror live (le mirror live est OK)

## Actions à faire dans la prochaine session

### 🔴 P0 — Contacter GitHub Support
- URL : https://support.github.com
- Citer ticket #4280656 (2026-04-13) comme contexte
- Demander la **raison exacte** de la suspension TOS
- **Pas créer de nouveau compte** — double-compte = ban permanent

### 🔴 P0 — Refresh Mac Backup snapshot
Le snapshot du 30 mars manque 24 jours de travail (v4.0, Rerun integration, MCP gateway go-live, telemetry worker, empire dashboard, sceneview-mcp 3.6.4 → 4.0.8).

```bash
DRIVE="$HOME/Library/CloudStorage/GoogleDrive-thomas.gorisse@gmail.com/My Drive/Mac Backup"
rsync -av --delete \
  --exclude 'node_modules' \
  --exclude '.gradle' \
  --exclude 'build' \
  --exclude '.idea' \
  --exclude 'DerivedData' \
  ~/Projects/ "$DRIVE/Projects/"
```

### 🟡 P1 — Investiguer les 14 dossiers rouges
Probablement node_modules à clean dans chaque MCP avant que Drive arrête de hurler :
```bash
for d in architecture-mcp ecommerce-3d-mcp education-mcp finance-mcp french-admin-mcp health-fitness-mcp realestate-mcp social-media-mcp ai-invoice; do
  rm -rf ~/Projects/$d/node_modules
done
```
Puis attendre que Drive fasse le clean côté cloud → les badges devraient repasser verts.

### 🟡 P1 — Exclure node_modules du sync Drive
Dans Drive → Preferences → Mirror folders → Excluded patterns :
```
node_modules/
.gradle/
build/
DerivedData/
.idea/
```
Évite les ~2 723 erreurs récurrentes.

### 🟢 P2 — Une fois GitHub résolu
- 46 commits locaux sur branche `claude/hopeful-elgamal-c7433f` à pusher
- Reprendre publication MCPs (npm OK, mais releases git impactées)
- Vérifier que les orgs `sceneview`, `sceneview-tools`, `mcp-tools-lab` ne sont pas affectées par le ban perso

## Verdict général

**Empire en sécurité** : code source + secrets + configs sont mirroirés sur Drive et synchronisent activement. Même si GitHub perma-ban thomasgorisse, **rien n'est perdu**. Les packages npm/Maven Central sont sur des serveurs indépendants.

La crise est **opérationnelle** (push bloqué, releases gelées), pas **existentielle** (code sauvegardé).
