---
description: Audit test coverage for current changes and help write missing tests.
---

Audit test coverage for the current changes and help write missing tests.

## Steps

1. Run `git diff --name-only HEAD` to see changed files.
2. For each changed source file under `sceneview/src/main/` or `arsceneview/src/main/`, look for a corresponding test file under `src/test/` or `src/androidTest/`.
3. Read the changed source file and the existing test file (if any).
4. Identify untested public functions, edge cases, and threading scenarios.
5. Propose concrete test cases.

## Test patterns for SceneView

### Unit tests (JVM, `src/test/`)
Use for pure logic: math utilities, node transform calculations, state machines, data classes.

```kotlin
class MyNodeTest {
    @Test
    fun `position defaults to origin`() {
        val node = MyNode()
        assertEquals(Position(0f, 0f, 0f), node.position)
    }
}
```

### Instrumented tests (`src/androidTest/`)
Required for anything touching Filament, Compose, or ARCore. Use `createComposeRule()`.

```kotlin
@get:Rule val composeRule = createComposeRule()

@Test
fun modelNode_isVisibleAfterInstanceLoads() {
    composeRule.setContent {
        SceneView {
            val instance = rememberModelInstance(modelLoader, "models/cube.glb")
            instance?.let { ModelNode(it) }
        }
    }
    composeRule.waitUntil(5_000) { /* instance loaded */ true }
}
```

### Threading tests
When testing async loading, always assert on the main thread and use `runOnUiThread` or `MainCoroutineRule`.

## Output

For each changed file:
1. **Coverage gap** — list of public functions/paths not covered by existing tests.
2. **Proposed tests** — ready-to-paste test functions with `@Test` annotations.
3. **File path** — where each test belongs (`src/test/` vs `src/androidTest/`).

After showing the proposals, ask: "Generate these test files? (yes/no)"
