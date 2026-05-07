/**
 * health-fitness-mcp pilot library.
 *
 * Upstream: `health-fitness-mcp` (#11 DL/mo, ~335), owned by the
 * `mcp-tools-lab` GitHub org. Upstream repo is currently a 404 —
 * package.json on npm points at a non-existent GitHub URL. Repo
 * creation is a follow-up backlog item.
 *
 * IMPORTANT: health-fitness-mcp is INFORMATIONAL ONLY and NEVER
 * provides medical advice or prescriptions. The dispatcher must
 * enforce this once real handlers ship.
 */

import type {
  DispatchContext,
  ToolDefinition,
  ToolResult,
} from "../mcp/types.js";

export const TOOL_DEFINITIONS: readonly ToolDefinition[] = [
  {
    name: "health_fitness__workout_plan",
    description:
      "Generate a weekly workout plan for a goal (strength, hypertrophy, endurance, fat_loss) given available equipment and experience level. Informational, not medical advice.",
    inputSchema: {
      type: "object",
      properties: {
        goal: { type: "string" },
        experience: { type: "string", description: "beginner, intermediate, advanced." },
        daysPerWeek: { type: "number" },
        equipment: {
          type: "array",
          description: "bodyweight, dumbbells, barbell, machines, kettlebells, bands.",
        },
      },
      required: ["goal", "experience"],
      additionalProperties: false,
    },
  },
  {
    name: "health_fitness__macro_calculator",
    description:
      "Estimate daily macros (protein, carbs, fat) from body stats, activity level, and a goal. Uses the Mifflin–St Jeor formula. Informational, not a nutrition prescription.",
    inputSchema: {
      type: "object",
      properties: {
        heightCm: { type: "number" },
        weightKg: { type: "number" },
        age: { type: "number" },
        biologicalSex: { type: "string", description: "male, female." },
        activityLevel: {
          type: "string",
          description: "sedentary, light, moderate, active, very_active.",
        },
        goal: { type: "string", description: "cut, maintain, bulk." },
      },
      required: ["heightCm", "weightKg", "age", "biologicalSex", "activityLevel", "goal"],
      additionalProperties: false,
    },
  },
  {
    name: "health_fitness__exercise_form_cues",
    description:
      "Return form cues and common mistakes for an exercise by name (squat, deadlift, bench_press, pull_up, running).",
    inputSchema: {
      type: "object",
      properties: {
        exercise: { type: "string" },
      },
      required: ["exercise"],
      additionalProperties: false,
    },
  },
  // ── New Strava + nutrition bridge-API tools (v2.0) ────────────────────────
  {
    name: "health_fitness__get_strava_activities",
    description:
      "Fetch recent Strava activities (runs, rides, swims) with distance, duration, pace, and heart rate. Requires Strava API token. Informational only.",
    inputSchema: {
      type: "object",
      properties: {
        activityType: { type: "string", description: "run, ride, swim, walk." },
        limit: { type: "number" },
        after: { type: "string", description: "ISO date to filter activities after." },
      },
      additionalProperties: false,
    },
  },
  {
    name: "health_fitness__get_strava_athlete_stats",
    description:
      "Get Strava athlete stats (YTD totals, all-time totals, recent totals for run/ride/swim). Requires Strava API token. Informational only.",
    inputSchema: {
      type: "object",
      properties: {},
      additionalProperties: false,
    },
  },
  {
    name: "health_fitness__analyze_strava_trends",
    description:
      "Analyze training trends from Strava data: weekly volume, pace progression, heart rate zones, rest days, and overtraining risk. Informational, not medical advice.",
    inputSchema: {
      type: "object",
      properties: {
        period: { type: "string", description: "1w, 4w, 12w, 1y." },
        activityType: { type: "string" },
      },
      additionalProperties: false,
    },
  },
  {
    name: "health_fitness__search_nutrition",
    description:
      "Search food nutrition data (calories, macros, micronutrients) via USDA/Nutritionix API. Returns per-serving and per-100g values. Informational only.",
    inputSchema: {
      type: "object",
      properties: {
        query: { type: "string" },
        servingSize: { type: "string" },
      },
      required: ["query"],
      additionalProperties: false,
    },
  },
  {
    name: "health_fitness__log_meal_nutrition",
    description:
      "Log a meal and compute total nutrition (calories, protein, carbs, fat, fiber) from a list of food items with quantities. Informational only.",
    inputSchema: {
      type: "object",
      properties: {
        items: { type: "array", description: "List of {food, quantity, unit} objects." },
        mealType: { type: "string", description: "breakfast, lunch, dinner, snack." },
      },
      required: ["items"],
      additionalProperties: false,
    },
  },
];

export async function dispatchTool(
  toolName: string,
  _args: Record<string, unknown> | undefined,
  _ctx: DispatchContext = {},
): Promise<ToolResult> {
  return {
    content: [
      {
        type: "text",
        text:
          `health-fitness-mcp pilot stub: ${toolName} is registered on the ` +
          `hub gateway but the upstream repository is currently missing ` +
          `(404). Informational only — never medical advice. See ` +
          `hub-gateway/src/libraries/health-fitness.ts for the wiring checklist.`,
      },
    ],
  };
}
