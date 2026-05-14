---
description: "Rapporteur / Correcteur" workflow — parallel audit, toast-spawned batches per category, multi-agent reviews, mandatory visual QA.
---

# /issue-batch — SceneView batched issue workflow

You are the **orchestrator** of the "Rapporteur / Correcteur" workflow. Your job is to dispatch — not to code. Treat each batch as a self-contained child session that you launch via `mcp__ccd_session__spawn_task` (toast notification). Each child session must auto-signal end-of-work so it can be closed.

See memory file `feedback_issue_workflow.md` for the rationale and constraints.

---

## Phase 0 — Setup (skip if already done this cycle)

Verify:
- `feedback_issue_workflow.md` exists in memory.
- `.claude/handoff.md` reflects current state.
- Git status clean OR uncommitted work is owned by the current orchestrator only (cf. `feedback_agent_isolation.md`).

If a previous `/issue-batch` cycle is mid-flight (open spawn_task children), STOP and finish those first.

---

## Phase 1 — Audit (Rapporteur, ~30 min)

Launch in parallel via `Agent` `subagent_type=Explore` (read-only, fast):

1. **Issue triage agent** — `gh issue list -L 100 --state open` → categorize into `BUG_CRITICAL / BUG / PARITY / REFACTO / CI / DOCS / TEST`. Identify natural batches. Flag stale candidates for closing.
2. **Parity gaps agent** — compare Android (Filament) vs iOS (RealityKit) vs Web (Filament.js) public API surface. List gaps NOT yet tracked. Don't redocument umbrellas #1004, #1033, #1034, #1036.
3. **CI optimization agent** — audit `.github/workflows/*.yml` for parallelization, cache, path-guards, sharding. Cross-reference recent `gh run list` failures.
4. **Code smells agent** — `rg "TODO|FIXME|XXX|HACK"`, threading violations, coroutine leaks, resource cleanup gaps, deprecated APIs used in our own code, low-coverage modules.

Each agent reports under 1000 words, markdown, ready to pipe.

After audit:
- **File new issues** for parity gaps + CI wins + refacto candidates not yet tracked.
- **Close stale issues** identified by agent #1.
- Build a single batching table.

---

## Phase 2 — Batching

Group issues by category + module touched. Rules:
- 1 batch = 1 worktree = 1 toast `spawn_task`.
- 3-8 issues per batch (smaller = inline fix in orchestrator session, not batch-worthy).
- Order: **CI > BUG_CRITICAL > BUG > PARITY > REFACTO > DOCS > TEST**.
- Mark inter-batch dependencies explicitly (e.g. CI parallelization batch must finish before release-related batches).

Present the table to Thomas. Get approval on order. Don't spawn until confirmed.

---

## Phase 3 — Execution (one toast per batch)

For each approved batch, call `mcp__ccd_session__spawn_task` with:

- **title** : `Fix batch <name> (#<issues>)`
- **tldr** : 1-2 sentences, no file paths
- **prompt** : full self-contained brief. **MUST END WITH** this verbatim block:

```
=== INSTRUCTIONS DE SESSION ENFANT (NE PAS IGNORER) ===

1. Branche : crée une worktree dédiée. Ne touche jamais aux fichiers d'une autre branche.

2. Workflow obligatoire :
   a. Implémente le batch (refacto clean, breaking changes OK si justifié).
   b. Compile : `./gradlew :sceneview:compileReleaseKotlin :arsceneview:compileReleaseKotlin` (+ iOS/Web si touché).
   c. Tests : `./gradlew :sceneview:test :arsceneview:testDebugUnitTest` (+ MCP si touché).
   d. **5-7 reviewers Opus en parallèle**, angles différents (sécurité, threading, API consistency, perf, docs, cross-platform parity, visual QA). Voir `feedback_pr_review_workflow.md`.
   e. Triage 4-buckets : MERGE / FIX-MERGE / HOLD-COMMENT / FOLLOW-UP. Applique FIX-MERGE, file FOLLOW-UP en issues.
   f. **QA visuel obligatoire** avec interactions utilisateur réelles : Android emulator (`bash .claude/scripts/qa-android-demos.sh`), iOS simulator (`xcrun simctl` + computer-use tier "click"), Playwright/Chrome MCP pour web.
   g. Sync : `bash .claude/scripts/impact-check.sh` + update CLAUDE.md handoff + llms.txt + MCP + cheatsheet + version-bump si breaking.
   h. Merge sur main + push (cf. `feedback_merge_direct_main.md`).

3. Respect des règles existantes :
   - PAS de polling/sleep loops (cf. `feedback_no_infinite_loop_polling.md`)
   - PAS de full `connectedDebugAndroidTest` après commit trivial
   - PAS d'image > 1800 px
   - Email git = thomas.gorisse@gmail.com

4. **Signal de fin obligatoire** : quand tout est mergé sur main + follow-ups filés + handoff mis à jour, termine ton DERNIER message par EXACTEMENT ce bloc (pas de variation, pas de texte après) :

   ✅ SESSION TERMINÉE — vous pouvez fermer cet onglet.
   Batch: <nom>
   Branche: <claude/...>
   Commits: <SHAs>
   Issues fermées: #<n1>, #<n2>, ...
   Follow-ups filés: #<n3>, #<n4>, ...
   PR/Merge: <URL ou "direct main">

   Ne poll pas pour d'autre travail. Ne cherche pas à enchaîner sur un autre batch. La session principale orchestre.
```

Spawn parallel batches if they are truly independent (different modules, no merge conflict risk). Otherwise sequential.

---

## Phase 4 — Closeout

Main session monitors:
- Pour chaque toast ouvert : attend le signal `✅ SESSION TERMINÉE`.
- Collecte : commits SHAs, issues fermées, follow-ups filés.
- Update `.claude/handoff.md` avec section "Cycle N : batches X-Y closed".
- Update `MEMORY.md` metrics file (`metrics_<date>.md`) si fin de journée.
- Décide : nouveau cycle Phase 1 OU pause.

---

## Anti-patterns à éviter

- ❌ Spawner un batch sans l'audit Phase 1 (= fait du flow-of-the-day)
- ❌ Skipper les 5-7 reviewers Opus (= bugs en prod, cf. v4.1.0)
- ❌ Skipper la QA visuelle (= démos cassées, cf. v4.1.2 recovery)
- ❌ Laisser sessions enfant tourner sans signal de fin (= zombies)
- ❌ Burst de PRs externes (cf. `feedback_no_pr_burst.md`)
- ❌ Self-evaluate (le rapporteur n'est pas le correcteur)

---

**Tone** : direct, autonome. Pause uniquement avant `spawn_task` pour approval de l'ordre des batches.
