---
description: "Rapporteur / Correcteur" workflow — parallel audit, toast-spawned batches per category, Tier 1 child self-review + Tier 2 orchestrator post-merge audit, mandatory visual QA.
---

# /issue-batch — SceneView batched issue workflow

You are the **orchestrator** of the "Rapporteur / Correcteur" workflow. Your job is to dispatch — not to code. Treat each batch as a self-contained child session that you launch via `mcp__ccd_session__spawn_task` (toast notification). Each child session must auto-signal end-of-work so it can be closed.

⛔ **Harness limitation (issue #1243)** — child agents launched via `spawn_task` or `Agent(isolation="worktree")` **cannot themselves spawn nested `Agent()` reviewers**. The harness silently rejects nested `Agent` calls and the child ends up self-reviewing. This means the multi-Agent parallel review **must happen at the orchestrator level (this session)**, not inside the child. The skill therefore splits review into two tiers:

- **Tier 1 (pre-merge, inside child)** — child agent runs a single self-review pass against a 5-angle checklist (security, threading, API consistency, performance, docs). No nested Agent spawning.
- **Tier 2 (post-merge, in orchestrator — Phase 3.5 below)** — orchestrator spawns 5-7 Opus reviewers in parallel via real top-level `Agent` calls, against the merged commit SHA. Any blocker found becomes a follow-up issue (PRs are atomic; no force-revert).

See memory file `feedback_issue_workflow.md` for the rationale and `feedback_pr_review_workflow.md` for the Tier 1 / Tier 2 split details.

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
   d. **Tier 1 — Self-review pass with 5-angle checklist** (DO NOT attempt nested `Agent()` calls — the harness silently rejects them, cf. issue #1243):
      - **Security** : input validation, secrets exposure, permission scopes, deserialization.
      - **Threading** : Filament JNI on main thread, coroutine leaks, race conditions, lifecycle.
      - **API consistency** : naming parity (Android/iOS/Web), KDoc, deprecation hygiene, signature stability.
      - **Performance** : allocations in hot paths, draw-call counts, resource destroy order, regression vs baseline.
      - **Docs** : `llms.txt`, KDoc, cheatsheet, migration notes, MCP examples up to date.
      Document findings inline in the commit message or PR description. Fix anything actionable before merge. Real multi-Agent parallel review happens at Tier 2 (orchestrator post-merge audit).
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

## Phase 3.5 — Post-merge audit (Tier 2, orchestrator-side)

Once a child session signals `✅ SESSION TERMINÉE` AND the merge to `main` is confirmed (commit SHA captured), **the orchestrator (this session) spawns 5-7 Opus reviewers in parallel** for real independent multi-agent review. This is where the multi-Agent pattern of `feedback_pr_review_workflow.md` actually executes — at the top level, not inside the child.

Why post-merge: child agents cannot spawn nested `Agent()` (harness limitation, cf. issue #1243). Pre-merge multi-agent review would require the orchestrator to block on the child, defeating the toast/dispatch model. Post-merge audit accepts that PRs are atomic and trades pre-merge gating for post-merge follow-up issues.

**How to spawn** (orchestrator-only — this is YOU, not the child):

For each merged commit `<SHA>`, dispatch 5 parallel reviewers, each with a single angle:

```
Agent(
  subagent_type="general-purpose",
  model="opus",
  run_in_background=true,
  prompt="""
You are a post-merge reviewer for SceneView commit <SHA> on main (batch <name>).
Review angle: <Security|Threading|API consistency|Performance|Docs>.

Read-only audit. Inspect:
- `git show <SHA>` and `git diff <SHA>^..<SHA>`
- Any file touched by the commit
- Cross-platform parity if API surface changed

Deliverable (<= 500 words, markdown):
1. Verdict: 🟢 ship-as-is | 🟡 follow-up issue recommended | 🔴 hotfix required
2. Concrete blockers (file:line citations) if any
3. Suggested follow-up issue title + body if 🟡 or 🔴

Do NOT push commits. Do NOT spawn nested agents. Report only.
"""
)
```

**Process the 5 outputs**:
- 🟢 only → close batch, done.
- 🟡 → file follow-up issue(s) on GitHub via `gh issue create`. Link the batch issues. No revert.
- 🔴 → spawn a fresh `spawn_task` hotfix batch immediately (do NOT amend the merged commit — atomic PR principle).

---

## Phase 4 — Closeout

Main session monitors:
- Pour chaque toast ouvert : attend le signal `✅ SESSION TERMINÉE`.
- Collecte : commits SHAs, issues fermées, follow-ups filés.
- **Pour chaque SHA, confirme que Phase 3.5 (post-merge audit Tier 2) a tourné** et que les follow-up issues sont filées.
- Update `.claude/handoff.md` avec section "Cycle N : batches X-Y closed" (inclut les follow-ups de Phase 3.5).
- Update `MEMORY.md` metrics file (`metrics_<date>.md`) si fin de journée.
- Décide : nouveau cycle Phase 1 OU pause.

---

## Workflow diagram

```
Phase 1 (Audit, Explore agents in parallel)
   ↓
Phase 2 (Batching table — Thomas approves order)
   ↓
Phase 3 (Execution — toast spawn_task per batch)
   • Child does the work + Tier 1 self-review (5-angle checklist, NO nested Agent)
   • Child signals ✅ SESSION TERMINÉE with SHA + issues closed
   ↓
Phase 3.5 (Post-merge audit — orchestrator-side Tier 2)
   • Orchestrator spawns 5-7 Opus Agent reviewers in parallel against the merged SHA
   • Findings → follow-up GitHub issues (no force-revert; PRs are atomic)
   ↓
Phase 4 (Closeout — handoff, metrics, decide next cycle)
```

---

## Anti-patterns à éviter

- ❌ Spawner un batch sans l'audit Phase 1 (= fait du flow-of-the-day)
- ❌ **Demander au child agent de spawn 5-7 reviewers Opus** (= no-op silencieux, cf. issue #1243). La multi-Agent review se fait **en Phase 3.5 côté orchestrateur**, jamais à l'intérieur du child.
- ❌ Skipper la Phase 3.5 (post-merge audit Tier 2) sous prétexte que la Tier 1 self-review a tourné — les deux sont nécessaires (cf. `feedback_review_update_visual_triptych.md`).
- ❌ Skipper la QA visuelle (= démos cassées, cf. v4.1.2 recovery)
- ❌ Laisser sessions enfant tourner sans signal de fin (= zombies)
- ❌ Burst de PRs externes (cf. `feedback_no_pr_burst.md`)
- ❌ Self-evaluate (le rapporteur n'est pas le correcteur — mais la Tier 1 self-review reste OK, elle est complétée par Tier 2)

---

**Tone** : direct, autonome. Pause uniquement avant `spawn_task` pour approval de l'ordre des batches.
