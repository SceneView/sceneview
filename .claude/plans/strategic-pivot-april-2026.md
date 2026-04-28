# Strategic Pivot — sceneview-mcp post-ban (avril 2026)

**Auteur:** session `gracious-pare-7f96a2`, 2026-04-28
**Statut:** Phase 1 en cours. Phases 2-3 conditionnelles au déblocage GitHub.

## Contexte

- Compte `thomasgorisse` GitHub suspendu depuis le 13 avril (ticket #4280656, J+15, 0 réponse).
- Téléchargements sceneview-mcp en chute libre : pic 879/jour (12/04) → 7-17/jour fin avril. Last week: 212 dl (vs ~3450/mois historique = -90% sur la semaine).
- 0 client Stripe payant vérifié sur Gateway #1 ou Gateway #2.
- Contrainte CDI : impossible de promouvoir publiquement un SaaS persostratégie carrière (Octopus, viser Staff/Principal Engineer).

## Diagnostic

L'effondrement n'est **pas** la preuve que l'abonnement est un mauvais modèle. C'est la preuve que :
1. Le ban a coupé le flux d'acquisition (npm readme = 404 GitHub)
2. La campagne marketing prévue (Rerun, 15-22/04) n'est jamais sortie
3. Le CTA paywall toutes les 10 calls + lien "upgrade to Pro" crée une friction visible incompatible avec le canal LinkedIn (CDI rule)

## Décision : pivot soft en 3 phases

### Phase 1 — Adoucir le CTA (cette session, faible risque)

**Action:** dans `mcp/src/tools/handler.ts`
- Retirer le lien "upgrade to Pro" du `SPONSOR_CTA`
- Garder le lien GitHub Sponsors (passive, CDI-compatible)
- Conserver l'intervalle 10 calls (tests existants passent)
- Bump 4.0.3 → 4.0.4
- Republier sur npm @latest

**Impact:**
- Friction perçue ↓
- Compatible CDI (sponsoring ≠ SaaS)
- Récupère l'audience early-adopter qui fuyait le paywall
- Gateway #1 reste fonctionnel pour les rares conversions directes via `/pricing`

**Done criteria:**
- v4.0.4 publié sur npm @latest
- Tests `sponsor-cta.test.ts` verts
- `npm pack --dry-run` valide les fichiers
- Aucun lien `/pricing` dans le CTA inline

### Phase 2 — Gateway dormant (post-déblocage GitHub)

**Action:** rétrograder le pricing sur le worker
- Page `/pricing` reste accessible mais non-promue
- Retirer la mention `/pricing` du README MCP
- Garder Stripe products LIVE pour les 4 prix (Pro 19/190, Team 49/490)
- Passer Gateway #2 (hub-mcp) en mode hold — pas de promo, pas de developement

**Pourquoi:** ne pas tuer l'infra (5 mois de boulot), mais arrêter de pousser. Si un client manifeste un besoin, l'option existe.

**Done criteria:** README, llms.txt, CTA inline → 0 mention de `/pricing`. Worker reste up.

### Phase 3 — BYOK option (J+30, conditionnel)

**Action:** ajouter une option BYOK pour les outils premium nécessitant un LLM
- L'utilisateur fournit sa clé Anthropic/OpenAI (variable d'env)
- Aucun abonnement, aucun gateway
- Désactivable

**Pourquoi:** monétisation indirecte via l'utilité (l'utilisateur paie son LLM, pas Thomas), sans friction SaaS.

**Conditionnel à:**
- Déblocage compte GitHub (sinon impossible de releaser)
- Signal de demande (au moins 3 demandes user pour outils premium)

**Done criteria:** non-encore défini. À spécifier en Phase 3 dédiée.

## Ce qui ne change PAS

- La lib SceneView (Android, iOS, Web, etc.) reste 100% gratuite et open source
- Licence MIT/Apache 2.0 inchangée
- Telemetry conservée (diagnostics, pas monétisation)
- Sponsoring GitHub Sponsors reste mis en avant (canal CDI-compatible)
- Le repo monorepo `sceneview/sceneview` n'est pas touché par le ban (org distincte)

## Risques et contre-mesures

| Risque | Mitigation |
|---|---|
| 4.0.4 publish casse les utilisateurs existants | Test suite complète + `npm pack --dry-run` avant publish |
| Régression sponsoring (zéro revenu) | GitHub Sponsors reste affiché — passive donation flow OK |
| Gateway oublié, charge Cloudflare inutile | Worker free tier suffit, pas de coût ; à archiver si abandon définitif après J+90 |
| Reroll vers SaaS si demande explose | Aucun changement structurel — Stripe products vivants, suffit de re-promouvoir |

## Dépendances

- Phase 1 indépendante du déblocage GitHub (publish npm fonctionne sans accès `thomasgorisse`)
- Phases 2-3 nécessitent compte débloqué pour push tag + releases

## Suivi

- Mesure baseline npm dl à J+0 (28/04) : 7-17 dl/jour
- Mesure cible J+14 (12/05) : ≥30 dl/jour (récupération partielle attendue après retrait paywall)
- Si pas de remontée à J+14 : indique que le problème principal est le ban GitHub, pas le paywall
