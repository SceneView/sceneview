/**
 * Regression test: ensure every runtime module imported by src/index.ts
 * (directly and transitively) is actually included in the npm tarball.
 *
 * Why this exists: on April 11 2026 the `files[]` whitelist in package.json
 * lagged behind the refactor from a 1200-line monolith to multiple modules
 * under `src/tools/`, `src/telemetry.ts`, `src/search-models.ts`, etc. A
 * publish would have shipped a tarball missing most of the runtime, so every
 * `npx sceneview-mcp` would crash at startup with a Cannot-find-module error.
 *
 * This test catches that class of bug by:
 *   1. Running `npm pack --dry-run --json` to get the list of files the
 *      tarball will contain.
 *   2. Parsing `src/index.ts` and every transitively-reachable local import
 *      to collect the full set of required `./foo.js` paths.
 *   3. Asserting each required path is present in the tarball.
 *
 * The test also asserts that no `.test.js` files or fixtures leak into the
 * tarball — those are dev-only and bloat the package.
 */

import { execFileSync } from "node:child_process";
import { readFileSync, existsSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const __filename = fileURLToPath(import.meta.url);
const SRC_DIR = dirname(__filename);
const MCP_ROOT = resolve(SRC_DIR, "..");

/**
 * Walk local ES-module imports starting from `entry`. Only follows imports
 * of the form `"./foo.js"` or `"./foo/bar.js"` — skips bare specifiers (npm
 * packages) and node: builtins.
 */
function collectLocalImports(entry: string): Set<string> {
  const visited = new Set<string>();
  const queue: string[] = [entry];

  while (queue.length > 0) {
    const current = queue.shift()!;
    if (visited.has(current)) continue;
    visited.add(current);

    let source: string;
    try {
      source = readFileSync(current, "utf8");
    } catch {
      continue;
    }

    // Match both `import ... from "./foo.js"` and `import("./foo.js")`.
    const importRegex = /(?:import\s+[^'"]*?from\s+|import\()\s*['"](\.\.?\/[^'"]+)['"]/g;
    let match: RegExpExecArray | null;
    while ((match = importRegex.exec(source)) !== null) {
      const specifier = match[1];
      // We only care about `.js` specifiers (ES modules rewrite .ts→.js at build)
      if (!specifier.endsWith(".js")) continue;

      // Resolve to an absolute .ts source path (imports say `.js`, files are `.ts`)
      const resolved = resolve(dirname(current), specifier.replace(/\.js$/, ".ts"));
      if (existsSync(resolved)) {
        queue.push(resolved);
      }
    }
  }

  return visited;
}

/**
 * Convert a set of absolute .ts source paths to the set of compiled `.js`
 * paths under `dist/` that must ship in the tarball.
 */
function toDistPaths(srcPaths: Set<string>): string[] {
  const result: string[] = [];
  for (const src of srcPaths) {
    const rel = relative(SRC_DIR, src);
    if (rel.startsWith("..")) continue;
    const distPath = `dist/${rel.replace(/\.ts$/, ".js")}`;
    result.push(distPath);
  }
  return result.sort();
}

/**
 * Run `npm pack --dry-run --json` and return the list of files that would
 * ship in the tarball. Fast: no actual tarball is written.
 *
 * `--ignore-scripts` prevents the `prepare` hook from polluting stdout with
 * non-JSON log lines (e.g. the generate-llms-txt script).
 */
function getTarballFiles(): string[] {
  const stdout = execFileSync(
    "npm",
    ["pack", "--dry-run", "--json", "--ignore-scripts"],
    { cwd: MCP_ROOT, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] },
  );
  // Some tooling leaks non-JSON lifecycle banners onto stdout (e.g. the
  // generate-llms-txt prebuild script). Find the start of the real JSON array
  // by looking for the first newline followed by '['.
  let jsonStart = stdout.indexOf("\n[");
  if (jsonStart >= 0) {
    jsonStart += 1; // skip the newline
  } else if (stdout.trimStart().startsWith("[")) {
    jsonStart = stdout.indexOf("[");
  } else {
    throw new Error(`Cannot locate JSON array in npm pack output:\n${stdout.slice(0, 400)}`);
  }
  const parsed = JSON.parse(stdout.slice(jsonStart)) as Array<{ files: Array<{ path: string }> }>;
  if (!Array.isArray(parsed) || parsed.length === 0) {
    throw new Error("npm pack --dry-run returned unexpected output");
  }
  return parsed[0].files.map((f) => f.path);
}

describe("npm tarball includes every runtime module imported by src/index.ts", () => {
  const entrySrc = resolve(SRC_DIR, "index.ts");
  const requiredSrcPaths = collectLocalImports(entrySrc);
  const requiredDistPaths = toDistPaths(requiredSrcPaths);
  const tarballFiles = getTarballFiles();
  const tarballSet = new Set(tarballFiles);

  it("all transitively-imported modules are present in the tarball", () => {
    const missing = requiredDistPaths.filter((p) => !tarballSet.has(p));
    expect(
      missing,
      `${missing.length} dist files are imported by src/index.ts (directly or indirectly) but missing from the npm tarball. Update the "files" array in package.json. Missing:\n${missing.join("\n")}`,
    ).toEqual([]);
  });

  it("does not leak *.test.js files into the tarball", () => {
    const leaked = tarballFiles.filter((p) => p.endsWith(".test.js"));
    expect(leaked, `${leaked.length} test files leaked: ${leaked.join(", ")}`).toEqual([]);
  });

  it("does not leak fixtures into the tarball", () => {
    const leaked = tarballFiles.filter((p) => p.includes("__fixtures__"));
    expect(leaked, `${leaked.length} fixture files leaked: ${leaked.join(", ")}`).toEqual([]);
  });

  it("ships the entry point dist/index.js", () => {
    expect(tarballSet.has("dist/index.js")).toBe(true);
  });

  it("does NOT ship the raw llms.txt (it's already embedded in dist/generated)", () => {
    // The raw `llms.txt` (~127 kB) was historically in the tarball but
    // `dist/generated/llms-txt.js` already embeds the same text as a TS
    // string constant. Shipping both doubled the unpacked footprint for
    // zero runtime benefit — see #938. Single source of truth = the
    // generated module.
    expect(tarballSet.has("llms.txt")).toBe(false);
  });

  it("ships the generated llms-txt module used by the tools library", () => {
    // Not directly imported by src/index.ts but imported by src/tools/handler.ts,
    // so the transitive walk should pick it up.
    expect(tarballSet.has("dist/generated/llms-txt.js")).toBe(true);
  });
});
