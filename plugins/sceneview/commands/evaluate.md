---
description: Independent quality assessment of current work — 5 weighted criteria, blockers on any 1-2/5 score.
---

Evaluate the current work as an independent quality assessor. You are NOT the generator — do not justify or defend the code. Be skeptical.

## Evaluation Criteria (score 1-5 each)

### 1. Correctness (weight: 3x)
- Does the code compile on ALL targets?
- Do all tests pass?
- Are there runtime errors or crashes?
- Are edge cases handled?

### 2. API Consistency (weight: 2x)
- Does the new code follow existing SceneView API patterns?
- Are naming conventions consistent across platforms?
- Would an AI be able to generate correct code from the docs?

### 3. Completeness (weight: 2x)
- Are all claimed features actually implemented (not just TODOs)?
- Is documentation updated (CLAUDE.md, llms.txt, README)?
- Are there missing tests?

### 4. Safety (weight: 3x)
- No secrets committed (.env, credentials, API keys)
- No security vulnerabilities (XSS, injection, etc.)
- No breaking changes to existing APIs
- Thread safety rules followed (Filament on main thread)

### 5. Minimality (weight: 1x)
- No over-engineering or unnecessary abstractions
- No unrelated changes or formatting
- Changes are focused on the stated goal

## Process

1. Run `git diff main...HEAD --stat` to see all changes
2. Run `./gradlew build` to verify compilation
3. Run all tests: `./gradlew :sceneview-core:jsTest :sceneview-web:jsTest`
4. Read each changed file and score against criteria
5. If ANY criterion scores 1 or 2: flag as BLOCKING
6. Produce a structured report with scores and actionable fixes

## Output Format

```
## Evaluation Report

| Criterion | Score | Notes |
|---|---|---|
| Correctness | X/5 | ... |
| API Consistency | X/5 | ... |
| Completeness | X/5 | ... |
| Safety | X/5 | ... |
| Minimality | X/5 | ... |

**Weighted Total:** X/55
**Verdict:** PASS / NEEDS WORK / BLOCKING

### Issues (if any)
1. [BLOCKING/WARN] Description → Fix
```
