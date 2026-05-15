import { defineConfig } from "vitest/config";

/**
 * Vitest configuration for `sceneview-mcp`.
 *
 * The single load-bearing setting here is `test.exclude`: the TypeScript
 * build (`tsc`) compiles `src/**` — INCLUDING every `*.test.ts` — into
 * `dist/`. With vitest's default include glob (`**\/*.{test,spec}.*`) those
 * compiled `dist/**\/*.test.js` files get picked up as a SECOND copy of the
 * suite. That stale copy then:
 *   - fails whenever `src/` has changed but `dist/` has not yet been rebuilt
 *     (every edit-then-test cycle), and
 *   - is exactly the "8 orphan dist/*.test.js suite-loads still fail" noise
 *     called out in the project handoff.
 *
 * Tests are authored and run from `src/` only — `dist/` is a build artefact.
 * Excluding it makes the suite deterministic regardless of `dist/` freshness
 * (see #1113).
 */
export default defineConfig({
  test: {
    exclude: ["**/node_modules/**", "dist/**"],
  },
});
