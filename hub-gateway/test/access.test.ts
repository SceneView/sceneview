/**
 * Tier gating tests — exercises the FREE_TOOLS whitelist and the
 * canCallTool() signature through the full /mcp → transport →
 * access chain.
 *
 * Test matrix:
 *
 *   tier         | free tool           | pro tool
 *   -------------+---------------------+-----------------------------
 *   free         | ok                  | 200 JSON-RPC -32003 denied
 *   pro          | ok                  | ok
 *   team         | ok                  | ok
 *
 * Unknown tools default to `pro` so free users hitting a nonexistent
 * tool get the same ACCESS_DENIED message, not "unknown tool" —
 * conservative fallback.
 */

import { describe, it, expect, beforeEach } from "vitest";
import app from "../src/index.js";
import { hashApiKey } from "../src/auth/api-keys.js";
import { JSON_RPC_ERRORS } from "../src/mcp/transport.js";
import { FREE_TOOLS, getToolTier, canCallTool } from "../src/mcp/access.js";
import { getAllTools } from "../src/mcp/registry.js";
import { makeEnv, FakeD1 } from "./helpers/fake-bindings.js";

const FREE_KEY = "sv_live_FREEACCESS00000000000000000000000";
const PRO_KEY = "sv_live_PROACCESS000000000000000000000000";
const TEAM_KEY = "sv_live_TEAMACCESS00000000000000000000000";

interface Ctx {
  env: ReturnType<typeof makeEnv>["env"];
  db: FakeD1;
}

let ctx: Ctx;

beforeEach(async () => {
  const freeHash = await hashApiKey(FREE_KEY);
  const proHash = await hashApiKey(PRO_KEY);
  const teamHash = await hashApiKey(TEAM_KEY);
  ctx = makeEnv({
    api_keys: [
      { id: "key_free_a", user_id: "usr_free_a", key_hash: freeHash, key_prefix: "sv_live_FREEAC", revoked_at: null },
      { id: "key_pro_a", user_id: "usr_pro_a", key_hash: proHash, key_prefix: "sv_live_PROACC", revoked_at: null },
      { id: "key_team_a", user_id: "usr_team_a", key_hash: teamHash, key_prefix: "sv_live_TEAMAC", revoked_at: null },
    ],
    users: [
      { id: "usr_free_a", email: "free@hub.test", tier: "free" },
      { id: "usr_pro_a", email: "pro@hub.test", tier: "pro" },
      { id: "usr_team_a", email: "team@hub.test", tier: "team" },
    ],
    usage_records: [],
  });
});

function callTool(key: string, name: string): Request {
  return new Request("https://hub-mcp.test/mcp", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: `Bearer ${key}`,
    },
    body: JSON.stringify({
      jsonrpc: "2.0",
      id: 1,
      method: "tools/call",
      params: { name, arguments: {} },
    }),
  });
}

describe("access module — pure functions", () => {
  it("FREE_TOOLS covers every library (23 total)", () => {
    // Real architecture (npm): 3 (generate_3d_concept + cost_estimate + search_parcels)
    // Stubbed libraries — discovery + bridge-API tools:
    //   ecommerce3d: 1, education: 2, finance: 3, french_admin: 3,
    //   health_fitness: 2, legal_docs: 2, realestate: 2, social_media: 1 = 16
    // Real automotive (monorepo): 2 (list_car_models + validate_automotive_code)
    // Real healthcare (monorepo): 2 (list_medical_models + validate_medical_code)
    expect(FREE_TOOLS.size).toBe(23);
  });

  it("getToolTier returns `free` for whitelisted tools", () => {
    expect(getToolTier("architecture__generate_3d_concept")).toBe("free");
    expect(getToolTier("finance__compound_interest")).toBe("free");
  });

  it("getToolTier defaults unknown tools to `pro` (conservative)", () => {
    expect(getToolTier("architecture__render_walkthrough")).toBe("pro");
    expect(getToolTier("nonexistent__tool")).toBe("pro");
  });

  it("canCallTool allows anyone to call free tools", () => {
    expect(canCallTool("architecture__generate_3d_concept", { tier: "free" })).toBe(true);
    expect(canCallTool("architecture__generate_3d_concept", undefined)).toBe(true);
  });

  it("canCallTool blocks free users from pro tools", () => {
    expect(canCallTool("architecture__render_walkthrough", { tier: "free" })).toBe(false);
    expect(canCallTool("architecture__render_walkthrough", undefined)).toBe(false);
  });

  it("canCallTool allows pro and team users on every tool", () => {
    expect(canCallTool("architecture__render_walkthrough", { tier: "pro" })).toBe(true);
    expect(canCallTool("architecture__render_walkthrough", { tier: "team" })).toBe(true);
  });

  it("every FREE_TOOLS entry exists in the current registry", () => {
    const registeredNames = new Set(getAllTools().map((t) => t.name));
    for (const name of FREE_TOOLS) {
      expect(registeredNames.has(name), `free tool missing from registry: ${name}`).toBe(true);
    }
  });
});

describe("access module — /mcp tools/call integration", () => {
  it("free user calling a free tool → 200 ok + usage logged as tier_required=free", async () => {
    const res = await app.request(
      callTool(FREE_KEY, "architecture__generate_3d_concept"),
      {},
      ctx.env,
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      result?: { content: Array<{ text: string }>; isError?: boolean };
      error?: unknown;
    };
    expect(body.error).toBeUndefined();
    expect(body.result?.isError).toBeFalsy();

    const records = ctx.db.getUsageRecords();
    expect(records).toHaveLength(1);
    expect(records[0].tier_required).toBe("free");
    expect(records[0].status).toBe("ok");
  });

  it("free user calling a pro tool → JSON-RPC -32003 denied + usage logged as tier_required=pro", async () => {
    const res = await app.request(
      callTool(FREE_KEY, "architecture__render_walkthrough"),
      {},
      ctx.env,
    );
    // HTTP 200 — JSON-RPC access denied is still a well-formed response.
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      error: {
        code: number;
        message: string;
        data: { tool: string; requiredTier: string; currentTier: string; upgradeUrl: string };
      };
    };
    expect(body.error.code).toBe(JSON_RPC_ERRORS.ACCESS_DENIED);
    expect(body.error.message).toContain("Portfolio Access subscription");
    expect(body.error.data.tool).toBe("architecture__render_walkthrough");
    expect(body.error.data.requiredTier).toBe("pro");
    expect(body.error.data.currentTier).toBe("free");
    expect(body.error.data.upgradeUrl).toContain("/pricing");

    // The /mcp observer records this as `denied` so dashboards
    // can count "upsell signal" events per user.
    const records = ctx.db.getUsageRecords();
    expect(records).toHaveLength(1);
    expect(records[0].status).toBe("denied");
    expect(records[0].tier_required).toBe("pro");
    expect(records[0].tool_name).toBe("architecture__render_walkthrough");
  });

  it("pro user calling a pro tool → 200 ok", async () => {
    const res = await app.request(
      callTool(PRO_KEY, "architecture__render_walkthrough"),
      {},
      ctx.env,
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      result: { content: Array<{ text: string }>; isError?: boolean };
    };
    // Real upstream handler — may return data or an input validation
    // error, either way it's not a stub marker anymore.
    expect(body.result.content[0].text).toBeDefined();

    const records = ctx.db.getUsageRecords();
    expect(records[0].status).toBe("ok");
    expect(records[0].tier_required).toBe("pro");
  });

  it("team user calling a pro tool → 200 ok", async () => {
    const res = await app.request(
      callTool(TEAM_KEY, "realestate__search_listings"),
      {},
      ctx.env,
    );
    expect(res.status).toBe(200);
    const records = ctx.db.getUsageRecords();
    expect(records[0].status).toBe("ok");
  });
});
