---
description: Generate or update KDoc for changed public APIs in this SceneView contribution.
---

Generate or update KDoc documentation for changed public APIs in this SceneView contribution.

## Steps

1. Run `git diff --name-only HEAD` to list changed files.
2. Read each changed Kotlin source file.
3. Identify all **public** and **internal** declarations that are new or modified.
4. For each, check whether KDoc is present and accurate.
5. Generate or update KDoc as needed.
6. Also check if `llms.txt` at the repo root needs updating for any API signature changes.

## KDoc rules

### Composable functions
```kotlin
/**
 * Short one-line description.
 *
 * Longer explanation if needed (threading, null behaviour, lifecycle).
 *
 * @param paramName Description including units (meters, radians) where relevant.
 * @param content Optional trailing lambda that opens a [NodeScope] for child nodes.
 */
@Composable
fun MyNode(paramName: Type, content: (@Composable NodeScope.() -> Unit)? = null)
```

### Data classes / value holders
- Document every constructor parameter that isn't self-evident from the name.
- Note default values and their meaning.

### Extension functions
- State the receiver type and what the function does to/with it.

## `llms.txt` update rules

`llms.txt` is the machine-readable API reference used by the MCP server. Update it when:
- A composable signature changes (parameters added/removed/renamed)
- A new node type or composable is added
- Threading or lifecycle behaviour changes
- A deprecated symbol is removed

If `llms.txt` needs changes, show a diff-style proposal (lines to remove prefixed `-`, lines to add prefixed `+`).

## Output

1. Show each file that needs documentation updates.
2. For each file, show the exact KDoc to add or replace (ready to paste).
3. If `llms.txt` needs updating, show the proposed diff.
4. End with: "Apply these changes? (yes/no)" — if the user confirms, use Edit to apply them.
