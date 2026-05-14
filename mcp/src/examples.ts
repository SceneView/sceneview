/**
 * Inline example resources exposed via MCP `examples://` URIs.
 *
 * Two resources, intentionally inline (no fs reads / no fetches) so the
 * `sceneview-mcp` package stays a pure code dependency:
 *
 *  - `examples://demo-with-settings` — DemoScaffold v2 pattern (PR #1169 under
 *    issue #1154). Full-screen 3D / AR scene + ModalBottomSheet controls
 *    + horizontal FilterChip picker row.
 *
 *  - `examples://sketchfab-streaming` — `SketchfabAssetResolver` pattern
 *    (Stage 2 of umbrella issue #1152). Stream CC-BY models from Sketchfab
 *    with per-slug bundled fallback + LRU cache + bounds sanity check.
 *
 * These strings deliberately stay small (≤ 4 KB each) so the resource list
 * doesn't bloat the client's context window. The full recipes live at
 * `docs/docs/recipes/sketchfab-streaming.md` and
 * `docs/docs/recipes/demo-settings-sheet.md`.
 */

export const DEMO_WITH_SETTINGS_EXAMPLE = `# Example — DemoScaffold v2 (full-screen scene + ModalBottomSheet controls)

\`DemoScaffold\` v2 is the shared scaffold every SceneView sample-app demo uses. It renders the 3D / AR scene **full-screen** under the top bar, with a \`Tune\` FAB pinned bottom-right that opens a Material 3 ModalBottomSheet containing the controls.

\`\`\`kotlin
@Composable
fun MyDemo(onBack: () -> Unit) {
    var iblIntensity by remember { mutableFloatStateOf(5_000f) }
    var spinScene by remember { mutableStateOf(true) }
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)

    DemoScaffold(
        title = "My Demo",
        onBack = onBack,
        controls = {
            // Same Column scope you'd use in any settings sheet.
            Text(
                "IBL intensity: \${iblIntensity.toInt()} lux",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = iblIntensity,
                onValueChange = { iblIntensity = it },
                valueRange = 0f..10_000f,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Spin scene", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = spinScene, onCheckedChange = { spinScene = it })
            }
        },
    ) {
        // BoxScope — full-screen scene.
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
        ) {
            // … nodes go here.
        }
    }
}
\`\`\`

**Gestures.** Tap FAB → opens the sheet. Tap peek chip ("Settings", above the FAB) → opens the sheet (discoverability — added under issue #951). Long-press peek chip → toggles \`DemoSettings.qaMode\` for deterministic screenshot captures. Drag handle / outside tap / back → dismiss. AR sessions keep tracking underneath.

**Picker pattern.** Combine with the FilterChip horizontal row for bundled-vs-streamed asset selection — see \`examples://sketchfab-streaming\`.

**Full doc:** \`docs/docs/recipes/demo-settings-sheet.md\` (PR #1169, issue #1154).
`;

export const SKETCHFAB_STREAMING_EXAMPLE = `# Example — Stream Sketchfab CC-BY models into a SceneView demo

The sample app (\`samples/android-demo\`) streams CC-BY licensed glTF models from Sketchfab on demand instead of bundling 30 MB of GLBs in the APK. The same pattern works in any SceneView consumer.

\`\`\`kotlin
@Composable
fun MyStreamedDemo(onBack: () -> Unit) {
    val context = LocalContext.current
    val resolver = remember { SketchfabAssetResolver.getInstance(context) }
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)

    // Warm the cache so the first frame doesn't pop in. Concurrent calls for
    // the same slug deduplicate at the service layer.
    LaunchedEffect(Unit) {
        runCatching { resolver.prefetchAll("animation") }
    }

    // Pick a slug from the curated registry — categories: solar, gallery,
    // animation, park, ar_placement, physics, materials.
    val slug = remember { SampleAssets.byCategory["animation"].orEmpty().first() }

    // Resolve to a local file (null while downloading / staging the fallback).
    val file: File? by produceState<File?>(initialValue = null, key1 = slug.uid) {
        value = runCatching { resolver.resolve(slug) }.getOrNull()
    }

    val instance = file?.let {
        rememberModelInstance(modelLoader, "file://\${it.absolutePath}")
    }

    DemoScaffold(title = slug.displayName, onBack = onBack) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
        ) {
            instance?.let {
                ModelNode(
                    modelInstance = it,
                    scaleToUnits = slug.scaleToUnits,
                    autoAnimate = slug.hasBakedAnimation,
                )
            }
        }
    }
}
\`\`\`

**Hard rules.**

- **CC-BY 4.0 only.** \`SketchfabSlug\`'s constructor rejects non-CC-BY models so the registry can't silently regress.
- **No Sketchfab WebView / external link.** All loading is in-process; Sketchfab is an invisible CDN, not a UX surface.
- **Never ship a build that needs the network to render something.** The resolver returns a bundled fallback when \`SketchfabConfig.apiKey == null\` or the download fails.
- **Attribute the author.** Every streamed model surfaced in the UI must show \`slug.author\` — CC-BY 4.0 attribution requirement.

**LRU cache.** \`Context.cacheDir/sketchfab/\` (250 MB samples-side cap, evicted oldest-first by \`lastModified\`).

**Bounds sanity check.** The resolver verifies the \`glTF\` magic header + size ≥ 12 B before returning a streamed file. Truncated downloads / wrong-format payloads fall back to the bundled asset.

**Full doc:** \`docs/docs/recipes/sketchfab-streaming.md\` (umbrella issue #1152, Stage 1 PR #1168).
`;
