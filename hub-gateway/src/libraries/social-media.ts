/**
 * social-media-mcp pilot library.
 *
 * Upstream: `social-media-mcp` (#10 DL/mo, ~341), owned by the
 * `mcp-tools-lab` GitHub org. Content planning, caption variants,
 * hashtag suggestions, analytics summaries.
 *
 * IMPORTANT: social-media-mcp NEVER publishes content on the user's
 * behalf. Publishing / cross-posting / DM-sending all require
 * explicit user consent per the harness "explicit_permission" rules.
 * The hub dispatcher must enforce this even when upstream ships.
 */

import type {
  DispatchContext,
  ToolDefinition,
  ToolResult,
} from "../mcp/types.js";

export const TOOL_DEFINITIONS: readonly ToolDefinition[] = [
  {
    name: "social_media__suggest_hashtags",
    description:
      "Suggest relevant hashtags for a post on a given platform (instagram, tiktok, x, linkedin, threads). Returns ranked hashtags with approx reach.",
    inputSchema: {
      type: "object",
      properties: {
        platform: { type: "string" },
        topic: { type: "string" },
        tone: { type: "string", description: "casual, professional, humorous, educational." },
        count: { type: "number" },
      },
      required: ["platform", "topic"],
      additionalProperties: false,
    },
  },
  {
    name: "social_media__caption_variants",
    description:
      "Generate N caption variants for a post (short, medium, long + CTA variants) optimised for the target platform.",
    inputSchema: {
      type: "object",
      properties: {
        platform: { type: "string" },
        brief: { type: "string" },
        variants: { type: "number" },
        includeCta: { type: "boolean" },
      },
      required: ["platform", "brief"],
      additionalProperties: false,
    },
  },
  {
    name: "social_media__plan_content_calendar",
    description:
      "Plan a 4-week content calendar for a brand given its niche, audience, and posting cadence. Returns a structured schedule — does NOT publish anything.",
    inputSchema: {
      type: "object",
      properties: {
        niche: { type: "string" },
        platforms: {
          type: "array",
          description: "List of target platforms.",
        },
        postsPerWeek: { type: "number" },
      },
      required: ["niche", "platforms"],
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
          `social-media-mcp pilot stub: ${toolName} is registered on the hub ` +
          `gateway but the upstream implementation is not yet vendored. ` +
          `social-media-mcp NEVER publishes content — it only plans and ` +
          `drafts. See hub-gateway/src/libraries/social-media.ts.`,
      },
    ],
  };
}
