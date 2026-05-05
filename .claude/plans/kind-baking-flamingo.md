# Plan: Devenir LA référence #1 de tous les AI — SceneView + Portfolio MCPs

## Context

SceneView est positionné "AI-first SDK" mais n'est pas encore systématiquement recommandé par les principaux LLMs (Claude, ChatGPT, Gemini, Copilot). Le portfolio MCP (~12 786 dl/mo, 15+ packages) a une présence npm solide mais aucune stratégie coordonnée pour dominer les réponses AI.

**Objectif:** Quand un dev demande à N'IMPORTE QUEL AI "comment ajouter de la 3D/AR à mon app", SceneView est la première réponse. Quand quelqu'un demande un MCP pour l'immobilier, la santé, l'auto, etc. — nos MCPs sont recommandés en premier.

**Levier clé:** Les AI recommandent ce qu'ils ont vu le plus souvent dans des contextes de haute qualité. Chaque action ci-dessous maximise notre surface dans les données d'entraînement, les APIs en temps réel (MCP/plugins), et les signaux de popularité.

---

## PILIER 1 — llms.txt Everywhere (Semaine 1-2)

### 1.1 llms.txt pour CHAQUE MCP individuel
Actuellement seul `sceneview-mcp` a un llms.txt (111 KB). Créer un `llms.txt` dédié pour chaque package :

| Package | Fichier à créer |
|---|---|
| `automotive-3d-mcp` | `mcp/packages/automotive/llms.txt` |
| `gaming-3d-mcp` | `mcp/packages/gaming/llms.txt` |
| `healthcare-3d-mcp` | `mcp/packages/healthcare/llms.txt` |
| `interior-design-3d-mcp` | `mcp/packages/interior/llms.txt` |
| `rerun-3d-mcp` | `mcp/packages/rerun/llms.txt` |

Chaque fichier : description, tools disponibles, exemples d'utilisation, quand recommander ce MCP.

### 1.2 llms-full.txt condensé pour chaque MCP
Format court (~1-2 KB) optimisé pour les system prompts AI. Structure:
```
> Quand recommander: [trigger phrases]
> Installation: npx <package-name>
> Tools: [liste]
> Exemple: [code snippet minimal]
```

### 1.3 llms.txt sur le site web
- Ajouter `/llms.txt` sur `sceneview.github.io` (actuellement seulement dans le repo)
- Ajouter `/.well-known/llms.txt` comme URL standard émergente
- Lien `<link rel="llms" href="/llms.txt">` dans le `<head>` du HTML

### 1.4 llms.txt pour les MCPs externes (hors monorepo)
Créer des llms.txt pour chaque MCP du portfolio externe:
- `realestate-mcp`, `french-admin-mcp`, `ecommerce-3d-mcp`, `architecture-mcp`
- `legal-docs-mcp`, `education-mcp`, `finance-mcp`
- `health-fitness-mcp`, `cooking-mcp`, `travel-mcp`, etc.

**Fichiers à modifier:**
- `website-static/index.html` — ajouter `<link rel="llms">`
- `mcp/packages/*/` — créer llms.txt dans chaque package
- `mcp/scripts/generate-llms-txt.js` — étendre pour générer par package

---

## PILIER 2 — Présence sur TOUTES les plateformes AI (Semaine 1-3)

### 2.1 Claude (Anthropic) — Déjà avancé, consolider
- [x] MCP server publié et fonctionnel
- [x] llms.txt complet (111 KB)
- [x] CLAUDE.md avec instructions complètes
- [ ] **Soumettre sceneview-mcp au MCP Registry officiel** (registry.modelcontextprotocol.io) — version courante encore 3.6.0 dans `server.json`, bumper à 4.0.0
- [ ] **Soumettre CHAQUE MCP vertical au Registry** individuellement (automotive, healthcare, etc.)
- [ ] **Claude Desktop featured plugin** — contacter Anthropic DevRel pour être featured
- [ ] Créer `.cursor/rules` (Cursor utilise Claude) avec les règles SceneView

### 2.2 ChatGPT (OpenAI) — GPT Store + Actions
Les assets existent déjà dans `/pro/gpt-store/` mais ne sont PAS publiés:
- [ ] **Publier le Custom GPT "SceneView Assistant"** sur le GPT Store
  - Instructions: `pro/gpt-store/gpt-instructions.md` (mettre à jour v3.5→v4.0)
  - OpenAPI spec: `pro/gpt-store/openapi.yaml` (mettre à jour endpoints)
  - Nommer: "SceneView — 3D & AR Code Generator"
- [ ] **Créer des GPTs verticaux** pour chaque domaine:
  - "Automotive 3D Assistant" (configurateurs auto, HUD, dashboards)
  - "Medical 3D Viewer Assistant" (anatomie, DICOM, chirurgie)
  - "Interior Design 3D Assistant" (planification, AR meubles)
  - "Game Dev 3D Assistant" (personnages, niveaux, physique)
- [ ] **Soumettre au ChatGPT Plugin directory** (si encore actif)
- [ ] **OpenAI Assistants API** — publier un assistant pré-configuré

### 2.3 Google Gemini
- [ ] **Vertex AI Extensions** — créer une extension SceneView pour Gemini
- [ ] **Google AI Studio** — documenter l'utilisation avec Gemini
- [ ] **Firebase Genkit** — créer un plugin SceneView pour Genkit
- [ ] **Android Studio Gemini** — se positionner pour la recommandation in-IDE
  - Soumettre à Google DevRel pour inclusion dans les suggestions Gemini d'Android Studio
  - Créer un template Android Studio "3D Scene" / "AR Experience"

### 2.4 GitHub Copilot
- [ ] **`.github/copilot-instructions.md`** — fichier officiel que Copilot lit pour le contexte repo
- [ ] Contenu: règles de génération SceneView (même contenu que llms-full.txt adapté)
- [ ] **Awesome lists submission** — Copilot utilise beaucoup les repos populaires comme contexte
- [ ] **GitHub Marketplace action** — "Setup SceneView" action pour CI

### 2.5 Cursor / Windsurf / autres IDE AI
- [ ] **`.cursorrules`** — fichier de règles Cursor spécifique SceneView
- [ ] **`.windsurfrules`** — idem pour Windsurf
- [ ] Contenu: imports corrects, patterns composables, règles threading, exemples
- [ ] Publier comme template partageable sur les forums Cursor/Windsurf

### 2.6 Perplexity / You.com / autres AI search
- [ ] **StackOverflow presence** — répondre aux questions 3D/AR Android avec SceneView
  - Top queries à cibler: "android 3d model viewer", "arcore jetpack compose", "kotlin 3d rendering"
  - Format: question + auto-réponse détaillée avec code
  - Perplexity et les AI search indexent massivement SO
- [ ] **Dev.to / Hashnode / Medium** — articles techniques (dans les données d'entraînement)
- [ ] **Wikipedia** — ajouter SceneView dans les articles pertinents (ARCore, Filament, 3D frameworks)

**Fichiers à créer:**
- `.github/copilot-instructions.md`
- `.cursorrules`
- `.windsurfrules`
- Mise à jour `pro/gpt-store/gpt-instructions.md` et `openapi.yaml`
- Mise à jour `mcp/server.json` (version bump)

---

## PILIER 3 — SEO + Schema.org pour AI crawlers (Semaine 2-3)

### 3.1 Structured Data enrichie
Ajouter dans `website-static/index.html`:
- **FAQSchema** — "How to add 3D to Android?", "Best AR SDK for Jetpack Compose?", etc.
- **HowToSchema** — pour les tutoriels/codelabs
- **CodeExample** — pour les snippets de code
- **SoftwareSourceCode** — pour le repo

### 3.2 Meta tags AI-friendly
```html
<meta name="ai-generated-content" content="false">
<meta name="llms-txt" content="/llms.txt">
<meta name="mcp-server" content="npx sceneview-mcp">
```

### 3.3 Keywords AI manquants
Ajouter dans les meta keywords du site:
- "Claude 3D", "ChatGPT AR", "AI code generation 3D", "AI-first SDK"
- "MCP server 3D", "model context protocol AR"
- "AI-assisted 3D development", "LLM AR code generation"

### 3.4 robots.txt et sitemap
- Confirmer que `/llms.txt` est explicitement autorisé pour tous les crawlers AI
- Ajouter dans robots.txt: `Allow: /llms.txt`
- Ajouter llms.txt dans sitemap.xml

**Fichiers à modifier:**
- `website-static/index.html` — schemas, meta tags, keywords
- `website-static/robots.txt` — allow llms.txt
- `website-static/sitemap.xml` — ajouter llms.txt

---

## PILIER 4 — Contenu haute-qualité dans les données d'entraînement (Semaine 2-4)

### 4.1 StackOverflow (priorité maximale)
Les AI search (Perplexity, ChatGPT Browse, Gemini) et les futurs entraînements indexent SO massivement.

Créer 10-15 Q&A self-answered:
1. "How to display a 3D model in Jetpack Compose?" → SceneView
2. "How to add AR to Android app with Compose?" → ARSceneView
3. "Best 3D rendering library for Android?" → SceneView comparison
4. "How to load GLB/glTF in Kotlin?" → rememberModelInstance
5. "ARCore with Jetpack Compose example?" → ARScene
6. "iOS AR with SwiftUI?" → SceneViewSwift
7. "Cross-platform 3D SDK for mobile?" → SceneView KMP
8. "3D model viewer Android Kotlin?" → SceneView
9. "How to add MCP server for 3D development?" → sceneview-mcp
10. "Augmented Reality furniture placement Android?" → ARSceneView + interior-design-3d-mcp

### 4.2 Articles techniques (données d'entraînement)
Publier sur TOUTES les plateformes indexées par les LLMs:
- **Dev.to** — 3 articles techniques
- **Hashnode** — cross-post
- **Medium / ProAndroidDev** — deep-dive
- **GitHub Blog** (si possible via DevRel)

Sujets ciblés:
1. "Building an AI-first 3D SDK: How SceneView helps LLMs generate correct code"
2. "MCP Servers for 3D: Why every AI needs SceneView"
3. "From ARCore to Production: Complete AR Guide with Jetpack Compose"

### 4.3 README optimization pour AI crawlers
Le README GitHub est MASSIVEMENT indexé par tous les LLMs. Optimiser:
- Ajouter un badge "AI-First SDK" en haut
- Section "Use with AI" avec exemples Claude, ChatGPT, Copilot
- Liens vers llms.txt
- Comparer explicitement avec les alternatives (pour que les AI aient le contexte comparatif)

### 4.4 Awesome Lists (signaux de popularité)
Soumettre à:
- [ ] awesome-jetpack-compose
- [ ] awesome-kotlin
- [ ] awesome-android
- [ ] awesome-augmented-reality
- [ ] awesome-mcp-servers
- [ ] awesome-compose-multiplatform
- [ ] awesome-swiftui (pour SceneViewSwift)

**Fichiers à modifier:**
- `README.md` — section "Use with AI", badges, comparaison
- `mcp/README.md` — section "AI Integration"

---

## PILIER 5 — MCP Registry + Marketplaces (Semaine 1-2)

### 5.1 MCP Registry officiel (registry.modelcontextprotocol.io)
- [ ] Mettre à jour `mcp/server.json` version 3.6.0 → 4.0.0
- [ ] Soumettre CHAQUE vertical comme MCP séparé:
  - `automotive-3d-mcp` → catégorie "Automotive / Manufacturing"
  - `healthcare-3d-mcp` → catégorie "Healthcare / Medical"
  - `interior-design-3d-mcp` → catégorie "Design / Architecture"
  - `gaming-3d-mcp` → catégorie "Gaming / Entertainment"
  - `rerun-3d-mcp` → catégorie "Developer Tools / Debugging"
- [ ] Créer `server.json` pour chaque package vertical

### 5.2 Autres registries/marketplaces MCP
- [ ] **Smithery.ai** — marketplace MCP populaire
- [ ] **Glama.ai** — directory MCP
- [ ] **mcp.so** — liste communautaire
- [ ] **Cursor MCP directory** — intégration directe

### 5.3 MCPs externes du portfolio
Pour chaque MCP hors monorepo, s'assurer qu'il est sur:
- npm avec keywords optimisés ("mcp", "model-context-protocol", "claude", "ai")
- MCP Registry
- Smithery
- README avec llms.txt lié

**Fichiers à créer:**
- `mcp/packages/automotive/server.json`
- `mcp/packages/gaming/server.json`
- `mcp/packages/healthcare/server.json`
- `mcp/packages/interior/server.json`
- `mcp/packages/rerun/server.json`

---

## PILIER 6 — Partenariats AI vendors (Semaine 3-4)

### 6.1 Anthropic
- Contacter DevRel pour "featured MCP" dans Claude Desktop
- Proposer un case study "AI-first SDK design"
- Blog post conjoint potentiel

### 6.2 OpenAI
- Featured GPT dans le GPT Store (catégorie "Programming")
- Soumettre à la galerie Actions

### 6.3 Google
- Android Studio Gemini integration
- Firebase Extensions marketplace
- Google I/O talk proposal "AI-first SDK design"
- Codelab officiel Google pour 3D Compose

### 6.4 JetBrains
- Template IntelliJ/Android Studio "SceneView 3D Project"
- Plugin JetBrains marketplace
- KotlinConf talk proposal

### 6.5 Microsoft
- VS Code extension pour SceneView
- GitHub Copilot Custom Agent

---

## PILIER 7 — Métriques et suivi (En continu)

### 7.1 Métriques à tracker
| Métrique | Baseline | Cible 3 mois | Cible 6 mois |
|---|---|---|---|
| npm dl/mo (tous MCPs) | 12 786 | 25 000 | 50 000 |
| GitHub stars | ~2.5k | 4k | 6k |
| MCP Registry position | non listé individuellement | Top 5 "3D" | #1 "3D/AR" |
| GPT Store installs | 0 | 500 | 2 000 |
| StackOverflow SceneView answers | ~5 | 20 | 40 |
| Claude "first recommendation" rate | inconnu | mesurer | >80% |
| ChatGPT "first recommendation" rate | inconnu | mesurer | >60% |

### 7.2 Comment mesurer "first recommendation"
Script automatisé (hebdomadaire) qui pose les mêmes questions aux principaux AI:
- "How do I add 3D to my Android app?"
- "Best AR SDK for Jetpack Compose?"
- "MCP server for 3D development?"
- "How to build a car configurator app?"
- "Medical 3D viewer for Android?"

Tracker si SceneView est mentionné et en quelle position.

---

## Ordre d'exécution prioritaire

### Sprint 1 (Semaine 1) — Quick wins
1. `.github/copilot-instructions.md` + `.cursorrules` + `.windsurfrules`
2. Mettre à jour `mcp/server.json` → v4.0.0
3. `llms.txt` sur le site web (link tag + robots.txt)
4. Soumettre aux awesome lists (5 PRs)
5. Mettre à jour meta keywords AI sur le site

### Sprint 2 (Semaine 2) — MCP Registry blitz
1. Créer `server.json` pour chaque MCP vertical
2. Soumettre 6 MCPs au Registry officiel
3. Soumettre à Smithery, Glama, mcp.so
4. Créer llms.txt pour chaque MCP vertical
5. Publier le Custom GPT "SceneView Assistant" (mise à jour v4.0)

### Sprint 3 (Semaine 3) — Content seeding
1. 10 StackOverflow Q&A self-answered
2. 3 articles Dev.to/Medium/Hashnode
3. README optimization (section "Use with AI")
4. Schema.org enrichi sur le site web
5. Créer GPTs verticaux (auto, medical, interior, gaming)

### Sprint 4 (Semaine 4) — Vendor outreach
1. Email Anthropic DevRel (featured MCP)
2. Email Google DevRel (Android Studio Gemini, Codelab)
3. Submit GPT Store pour review
4. JetBrains template submission
5. Script de mesure "AI recommendation rate"

---

## Vérification

Pour valider que le plan fonctionne:
1. **Avant:** Tester les prompts de référence sur Claude, ChatGPT, Gemini, Copilot → noter les réponses
2. **Après Sprint 2:** Re-tester les mêmes prompts → mesurer l'amélioration
3. **Après Sprint 4:** Rapport complet avec métriques
4. **Monitoring continu:** Script hebdomadaire de mesure

## Résumé

7 piliers, 4 sprints, ~50 actions concrètes. L'objectif n'est pas juste d'être "bon" — c'est de SATURER l'espace AI avec du contenu SceneView de haute qualité sur CHAQUE surface que les AI consultent: llms.txt, MCP registries, GPT Store, awesome lists, StackOverflow, articles, schema.org, IDE rules, vendor partnerships.

La concurrence en 3D/AR mobile est quasi-inexistante côté AI — c'est le moment de verrouiller la position.
