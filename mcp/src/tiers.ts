// ─── Tool tier system ─────────────────────────────────────────────────────────
//
// Defines free vs pro tool access for SceneView MCP.

export type Tier = "free" | "pro";

// ─── Free tools (17) ─────────────────────────────────────────────────────────

const FREE_TOOLS: readonly string[] = [
  "get_started",
  "list_samples",
  "get_sample",
  "get_setup",
  "get_node_reference",
  "list_platforms",
  "validate_code",
  "get_troubleshooting",
  "debug_issue",
  "get_best_practices",
  "get_animation_guide",
  "get_gesture_guide",
  "get_performance_tips",
  "get_material_guide",
  "get_collision_guide",
  "get_platform_roadmap",
  "search_models",
  "analyze_project",
] as const;

// ─── Pro tools ────────────────────────────────────────────────────────────────

const PRO_TOOLS: readonly string[] = [
  // Core pro tools
  "get_ios_setup",
  "get_web_setup",
  "get_ar_setup",
  "get_platform_setup",
  "migrate_code",
  "get_migration_guide",
  "render_3d_preview",
  "create_3d_artifact",
  "generate_scene",
  "get_model_optimization_guide",
  "get_web_rendering_guide",

  // Automotive package
  "get_car_configurator",
  "get_dashboard_3d",
  "get_parts_catalog",
  "get_hud_overlay",
  "get_ar_showroom",
  "list_car_models",

  // Gaming package
  "get_physics_game",
  "get_particle_effects",
  "get_level_editor",
  "get_inventory_3d",
  "get_character_viewer",
  "list_game_models",

  // Healthcare package
  "get_surgical_planning",
  "get_dental_viewer",
  "get_medical_imaging",
  "get_anatomy_viewer",
  "get_molecule_viewer",
  "list_medical_models",

  // Interior package
  "get_room_planner",
  "get_lighting_design",
  "get_room_tour",
  "get_material_switcher",
  "get_furniture_placement",
  "list_furniture_models",
] as const;

// ─── Tier map ─────────────────────────────────────────────────────────────────

export const TOOL_TIERS: Record<string, Tier> = Object.fromEntries([
  ...FREE_TOOLS.map((name) => [name, "free" as Tier]),
  ...PRO_TOOLS.map((name) => [name, "pro" as Tier]),
]);

// ─── Helper functions ─────────────────────────────────────────────────────────

/** Returns true if the tool requires a Pro subscription. Unknown tools default to pro. */
export function isProTool(toolName: string): boolean {
  return getToolTier(toolName) === "pro";
}

/** Returns the tier for a tool. Defaults to "pro" for unknown tools. */
export function getToolTier(toolName: string): Tier {
  return TOOL_TIERS[toolName] ?? "pro";
}

/** Returns all pro tool names. */
export function getProToolNames(): string[] {
  return [...PRO_TOOLS];
}

/** Returns all free tool names. */
export function getFreeToolNames(): string[] {
  return [...FREE_TOOLS];
}

// ─── Upgrade message ──────────────────────────────────────────────────────────

export const PRO_UPGRADE_MESSAGE = `## \u{1F512} Pro Feature

This tool requires a SceneView MCP Pro subscription.

**Upgrade for \u20AC19/month** to unlock:
- 35+ premium tools (AR, multi-platform, scene generation, artifacts)
- Specialized packages (Automotive, Gaming, Healthcare, Interior)
- Email support

\u2192 Subscribe at https://sceneview-mcp.mcp-tools-lab.workers.dev/pricing
\u2192 Then set your API key: \`SCENEVIEW_API_KEY=your_key\``;
