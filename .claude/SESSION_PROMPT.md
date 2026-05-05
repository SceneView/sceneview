# Session B — Multi-Gateway Sprint (2nd gateway pour 20+ autres MCPs)

Worktree: `.claude/worktrees/multi-gateway-sprint`
Branch: `claude/multi-gateway-sprint`
Base: `origin/main` @ `f38339d8`

**Objectif unique : designer + scaffolder un 2e gateway Cloudflare Workers
qui couvre les 16+ MCPs actifs hors sceneview-mcp (architecture, realestate,
french-admin, ecommerce-3d, legal-docs, finance, education, health-fitness,
social-media + les 4 verticals 3D live).**

Close la session dès que le scaffold est shippé (worker déployé sur
`*.workers.dev` + au moins 1 MCP pilote wiré en proxy).

---

## Prompt à coller au démarrage d'une session fraîche

```
Lis .claude/SESSION_PROMPT.md dans ce worktree. On est Session B
(multi-gateway-sprint). Objectif : scaffold un 2e Cloudflare Worker
gateway qui consolide le portfolio non-sceneview (16+ MCPs actifs).

Ne pas toucher mcp-gateway/ (gateway #1, déjà live pour sceneview-mcp).
Ne pas toucher mcp/ (package sceneview-mcp).
```

---

## Contexte portfolio (état au 2026-04-11 après-midi)

**Actifs (16 packages, ~11 900 DL/mo cumulés)** :

| Package | DL/mo | Org GitHub | État repo |
|---|---|---|---|
| sceneview-mcp | 3 450 | sceneview | monorepo `mcp/` — **EXCLUS de ce gateway** (a déjà le sien) |
| realestate-mcp | 1 276 | sceneview-tools | OK |
| french-admin-mcp | 1 268 | thomasgorisse | OK |
| sceneview-web | 1 221 | sceneview | N/A (pas MCP, librairie JS) |
| ecommerce-3d-mcp | 1 153 | sceneview-tools | OK |
| architecture-mcp | 1 134 | sceneview-tools | OK |
| legal-docs-mcp | 789 | orphan (no repo field) | **À créer repo** |
| finance-mcp | 585 | mcp-tools-lab | OK |
| education-mcp | 566 | mcp-tools-lab | OK |
| social-media-mcp | 341 | thomasgorisse | OK |
| health-fitness-mcp | 335 | thomasgorisse | **Repo 404, à créer** |
| automotive-3d-mcp | ? | sceneview monorepo `mcp/packages/automotive/` | OK |
| healthcare-3d-mcp | ? | sceneview monorepo `mcp/packages/healthcare/` | OK |

**Deprecated (7 packages ~1 608 DL/mo décroissant)** :
ai-invoice, cooking-mcp, travel-mcp, devops-mcp, seo-mcp (deprecated),
gaming-3d-mcp, interior-design-3d-mcp
**→ NE PAS inclure dans le gateway.** Décision session 2026-04-11 09:57.

**404 (jamais publiés)** :
pet-care-mcp, event-planning-mcp, mcp-creator-kit
**→ NE PAS inclure.**

---

## Questions stratégiques à trancher AVANT de coder

1. **Pricing model unifié** ou **per-vertical** ?
   - Gateway #1 (sceneview): Pro 19€/mo, Team 49€/mo (tiers déjà définis)
   - Gateway #2 : même grille ? Ou plan "portfolio access" 29€/mo qui
     donne accès à tous les MCPs non-sceneview ?

2. **Multiplexing tool registry** : 1 endpoint `/mcp` qui route vers le
   bon package handler en fonction du nom du tool ? Ou 1 endpoint par
   package (`/architecture/mcp`, `/realestate/mcp`, ...) ?
   - Recommandation : multiplex sur `/mcp` avec prefix `{package}__{tool}`
     dans le nom, comme gateway #1 fait pour sceneview-mcp.

3. **Auth** : partagée avec gateway #1 (une API key = accès au portfolio
   complet) ou silos séparés ?
   - Silos = plus simple. Shared = meilleure proposition de valeur ("1 abo
     pour tout mon portfolio").

4. **Nom + subdomain** :
   - `portfolio-mcp.mcp-tools-lab.workers.dev` ?
   - `verticals-mcp.mcp-tools-lab.workers.dev` ?
   - `hub-mcp.mcp-tools-lab.workers.dev` ?

5. **D1 + KV partagés** ou nouveaux ?
   - Si auth partagée → réutiliser D1 `8aaddcda-e36e-4287-9222-1df924426c9f`
     et KV `9a40d334be6149f7a4ba18451a60245f` du gateway #1.

**→ Utiliser AskUserQuestion au début de la session pour trancher.**

---

## Inspiration : Sprint 1/2 du 1er gateway

Le code source du gateway #1 vit dans `mcp-gateway/` sur main. Scanner
sa structure en premier :

```bash
tree mcp-gateway/src -L 2
cat mcp-gateway/wrangler.toml
cat mcp-gateway/src/index.ts | head -100
```

Points clés à reprendre :
- Hono pour le routing
- D1 pour l'usage tracking + API keys
- KV pour le handoff single-use /checkout/success
- Stripe webhook → API key provisioning
- Anonymous checkout (commit `c7d957f3` a ouvert `/billing/checkout`
  aux non-authentifiés)
- Pas de magic-link, pas de dashboard user (stripped in `673ddd88`)
- Vitest pour les tests (168 tests, 15 files passing)

**Ne PAS recoder from scratch** : copier la structure, adapter les
tool handlers.

---

## Livrable minimal (MVP)

1. `portfolio-gateway/` répertoire scaffold (ou autre nom choisi)
2. `wrangler.toml` avec nouveau worker name + D1/KV
3. `src/index.ts` Hono routes : `/`, `/pricing`, `/docs`, `POST /mcp`
4. `src/routes/checkout.ts` + `/checkout/success` + `/stripe/webhook`
5. `src/tools-registry.ts` multiplexé : mapping `{package}__{tool}` → handler
6. Au moins **1 MCP pilote** wiré en proxy (par exemple
   `architecture-mcp` qui est #6 DL/mo et appartient déjà à
   sceneview-tools org) :
   - `packages/architecture-mcp-lite/` : lite package qui forward via fetch
   - `packages/architecture-mcp-lite/package.json` version `2.0.0-beta.1`
   - Banner stderr, stub sur Pro tools
7. Deploy `wrangler deploy` + smoke test `/health` + `/pricing`
8. 1 commit clean sur `claude/multi-gateway-sprint`

**Out-of-scope pour cette session** :
- Wirer les 15 autres MCPs (à faire en session itérative après le MVP)
- Publier le lite package pilot sur npm (après validation MVP)
- Stripe LIVE prices (après MVP déployé en TEST mode)

---

## Bloquants connus

- **legal-docs-mcp n'a pas de repo GitHub** (orphan sur npm). Décision à
  prendre : créer le repo avant de wirer dans le gateway, ou wirer
  d'abord et créer après ?
- **health-fitness-mcp repo 404** (package.json pointe vers URL qui
  n'existe pas). Idem.
- **Secrets Cloudflare** : besoin `wrangler login` + nouveau
  `STRIPE_SECRET_KEY` + `STRIPE_WEBHOOK_SECRET`. Peut-être réutiliser ceux
  du gateway #1 si auth partagée (q3).

---

## Tokens budget

- Q&A strategic (début de session) : 2-3k
- Scaffold + routes + tools-registry : 15-25k
- 1 MCP pilote lite package : 10-15k
- Tests + deploy + smoke : 5-10k
- **Total ~35-50k tokens**

Si ça dépasse, split : ferme après le scaffold, ouvre nouvelle session
pour wirer les MCPs pilotes un par un.
