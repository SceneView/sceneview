/**
 * Multiplexed MCP tool registry for the hub gateway (Gateway #2).
 *
 * Structurally identical to mcp-gateway/src/mcp/registry.ts — the two
 * files are intentionally copy-paste siblings so a future refactor can
 * extract them into a shared workspace package. Do not diverge.
 *
 * Scope: the registry ships ELEVEN libraries with real upstream
 * handlers. Together they cover every ACTIVE non-SceneView package
 * in the portfolio as of 2026-04-11:
 *
 *   [all active portfolio MCPs — real upstream handlers]
 *   - architecture-mcp      (1 134 DL/mo, sceneview-tools)
 *   - realestate-mcp        (1 276 DL/mo, sceneview-tools)
 *   - french-admin-mcp      (1 268 DL/mo, mcp-tools-lab)
 *   - ecommerce-3d-mcp      (1 153 DL/mo, sceneview-tools)
 *   - legal-docs-mcp          (789 DL/mo, orphan — needs repo)
 *   - finance-mcp             (585 DL/mo, mcp-tools-lab)
 *   - education-mcp           (566 DL/mo, mcp-tools-lab)
 *   - social-media-mcp        (341 DL/mo, mcp-tools-lab)
 *   - health-fitness-mcp      (335 DL/mo, mcp-tools-lab — repo 404)
 *   - automotive-3d-mcp     (sceneview monorepo)
 *   - healthcare-3d-mcp     (sceneview monorepo)
 *
 *   sceneview-mcp is intentionally EXCLUDED — it has Gateway #1
 *   and its own lite package at mcp/ (session A is rewriting the
 *   4.0.0-beta.1 proxy on the claude/mcp-monetization branch).
 *
 * sceneview-mcp is intentionally EXCLUDED — it has its own gateway (Gateway #1).
 *
 * When adding a new library:
 *   1. Drop a module in `../libraries/<id>.ts` that exports
 *      `TOOL_DEFINITIONS` and `dispatchTool`.
 *   2. Add an entry to the LIBRARIES array below.
 *   3. Prefix every tool name with `<id>__` to avoid cross-package
 *      collisions. Collisions are detected at import time and throw
 *      a descriptive error, so the Worker fails fast on startup.
 */

import * as ArchitectureTools from "../libraries/architecture.js";
import * as RealestateTools from "../libraries/realestate.js";
import * as FrenchAdminTools from "../libraries/french-admin.js";
import * as Ecommerce3dTools from "../libraries/ecommerce-3d.js";
import * as LegalDocsTools from "../libraries/legal-docs.js";
import * as FinanceTools from "../libraries/finance.js";
import * as EducationTools from "../libraries/education.js";
import * as SocialMediaTools from "../libraries/social-media.js";
import * as HealthFitnessTools from "../libraries/health-fitness.js";
import * as Automotive3dTools from "../libraries/automotive-3d.js";
import * as Healthcare3dTools from "../libraries/healthcare-3d.js";

import type {
  DispatchContext,
  ToolDefinition,
  ToolLibrary,
  ToolResult,
} from "./types.js";

// ── Raw library list ────────────────────────────────────────────────────────

const LIBRARIES: ToolLibrary[] = [
  {
    id: "architecture",
    label: "architecture-mcp",
    definitions: ArchitectureTools.TOOL_DEFINITIONS,
    dispatch: (name, args, ctx) => ArchitectureTools.dispatchTool(name, args, ctx),
  },
  {
    id: "realestate",
    label: "realestate-mcp",
    definitions: RealestateTools.TOOL_DEFINITIONS,
    dispatch: (name, args, ctx) => RealestateTools.dispatchTool(name, args, ctx),
  },
  {
    id: "french_admin",
    label: "french-admin-mcp",
    definitions: FrenchAdminTools.TOOL_DEFINITIONS,
    dispatch: (name, args, ctx) => FrenchAdminTools.dispatchTool(name, args, ctx),
  },
  {
    id: "ecommerce3d",
    label: "ecommerce-3d-mcp",
    definitions: Ecommerce3dTools.TOOL_DEFINITIONS,
    dispatch: (name, args, ctx) => Ecommerce3dTools.dispatchTool(name, args, ctx),
  },
  {
    id: "legal_docs",
    label: "legal-docs-mcp",
    definitions: LegalDocsTools.TOOL_DEFINITIONS,
    dispatch: (name, args, ctx) => LegalDocsTools.dispatchTool(name, args, ctx),
  },
  {
    id: "finance",
    label: "finance-mcp",
    definitions: FinanceTools.TOOL_DEFINITIONS,
    dispatch: (name, args, ctx) => FinanceTools.dispatchTool(name, args, ctx),
  },
  {
    id: "education",
    label: "education-mcp",
    definitions: EducationTools.TOOL_DEFINITIONS,
    dispatch: (name, args, ctx) => EducationTools.dispatchTool(name, args, ctx),
  },
  {
    id: "social_media",
    label: "social-media-mcp",
    definitions: SocialMediaTools.TOOL_DEFINITIONS,
    dispatch: (name, args, ctx) => SocialMediaTools.dispatchTool(name, args, ctx),
  },
  {
    id: "health_fitness",
    label: "health-fitness-mcp",
    definitions: HealthFitnessTools.TOOL_DEFINITIONS,
    dispatch: (name, args, ctx) => HealthFitnessTools.dispatchTool(name, args, ctx),
  },
  {
    id: "automotive3d",
    label: "automotive-3d-mcp",
    definitions: Automotive3dTools.TOOL_DEFINITIONS,
    dispatch: (name, args, ctx) => Automotive3dTools.dispatchTool(name, args, ctx),
  },
  {
    id: "healthcare3d",
    label: "healthcare-3d-mcp",
    definitions: Healthcare3dTools.TOOL_DEFINITIONS,
    dispatch: (name, args, ctx) => Healthcare3dTools.dispatchTool(name, args, ctx),
  },
];

// ── Build a name → library lookup and fail fast on collisions ──────────────

const OWNERS: Map<string, ToolLibrary> = (() => {
  const owners = new Map<string, ToolLibrary>();
  const collisions: Array<{ name: string; a: string; b: string }> = [];

  for (const lib of LIBRARIES) {
    for (const def of lib.definitions) {
      const existing = owners.get(def.name);
      if (existing) {
        collisions.push({ name: def.name, a: existing.label, b: lib.label });
        continue;
      }
      owners.set(def.name, lib);
    }
  }

  if (collisions.length > 0) {
    const details = collisions
      .map((c) => `  - "${c.name}" is defined by both ${c.a} and ${c.b}`)
      .join("\n");
    throw new Error(
      `Hub MCP registry: tool name collision detected between packages.\n${details}\n` +
        `Rename the offending tool in one of the packages before redeploying the gateway.`,
    );
  }

  return owners;
})();

// ── Public API ──────────────────────────────────────────────────────────────

/** Merged list of tool definitions from every registered library. */
export function getAllTools(): ToolDefinition[] {
  const out: ToolDefinition[] = [];
  for (const lib of LIBRARIES) {
    for (const def of lib.definitions) out.push(def);
  }
  return out;
}

/** Read-only view of the registry — used by the /health endpoint. */
export function getRegistrySummary(): {
  libraries: Array<{ id: string; label: string; toolCount: number }>;
  totalTools: number;
} {
  const libraries = LIBRARIES.map((lib) => ({
    id: lib.id,
    label: lib.label,
    toolCount: lib.definitions.length,
  }));
  return {
    libraries,
    totalTools: libraries.reduce((n, l) => n + l.toolCount, 0),
  };
}

/** Returns the tool definition for a given name, or `undefined`. */
export function getToolDefinition(name: string): ToolDefinition | undefined {
  const owner = OWNERS.get(name);
  if (!owner) return undefined;
  return owner.definitions.find((d) => d.name === name);
}

/** Routes a tool call to the owning library. */
export async function dispatch(
  toolName: string,
  args: Record<string, unknown> | undefined,
  ctx: DispatchContext = {},
): Promise<ToolResult> {
  const owner = OWNERS.get(toolName);
  if (!owner) {
    return {
      content: [{ type: "text", text: `Unknown tool: ${toolName}` }],
      isError: true,
    };
  }
  return owner.dispatch(toolName, args, ctx);
}

/** Exposed for tests and diagnostics. */
export const __internals = { LIBRARIES, OWNERS };
