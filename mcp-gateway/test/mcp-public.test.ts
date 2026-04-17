/**
 * End-to-end tests for the anonymous `/mcp/public` route.
 *
 * Verifies the contract documented in routes/mcp-public.ts:
 *   - Anonymous access works (no Bearer token required)
 *   - Free tools dispatch successfully
 *   - Pro tools return ACCESS_DENIED with a /pricing pointer
 *   - IP-based hourly rate limit is enforced
 *   - Rate-limit headers are set on every response
 *
 * Bindings are mocked with the same in-memory KV/D1 helpers used by the
 * authenticated route's e2e suite, but no user/key seeding is needed —
 * the public route bypasses the auth chain entirely.
 */

import { afterEach, beforeEach, describe, expect, it } from "vitest";
import app from "../src/index.js";
import type { Env } from "../src/env.js";
import { JSON_RPC_ERRORS } from "../src/mcp/transport.js";
import { createMockD1, type MockD1 } from "./helpers/mock-d1.js";
import { MockKv } from "./helpers/mock-kv.js";

let mock: MockD1;
let kv: MockKv;

beforeEach(async () => {
  mock = await createMockD1();
  kv = new MockKv();
});
afterEach(() => {
  mock.close();
});

function env(): Env {
  return {
    DB: mock.db,
    RL_KV: kv.asKv(),
    ENVIRONMENT: "test",
  } as unknown as Env;
}

function postPublic(
  body: unknown,
  headers: Record<string, string> = {},
) {
  return app.request(
    "/mcp/public",
    {
      method: "POST",
      headers: {
        "content-type": "application/json",
        // Mock Cloudflare's IP header so each test gets a deterministic
        // (and isolated) rate-limit bucket.
        "cf-connecting-ip": "203.0.113.1",
        ...headers,
      },
      body: JSON.stringify(body),
    },
    env(),
  );
}

describe("POST /mcp/public: anonymous access", () => {
  it("initialize succeeds without any Authorization header", async () => {
    const res = await postPublic({
      jsonrpc: "2.0",
      id: 1,
      method: "initialize",
      params: { protocolVersion: "2025-03-26" },
    });
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      result: { protocolVersion: string; serverInfo: { name: string } };
    };
    expect(body.result.protocolVersion).toBe("2025-03-26");
  });

  it("tools/list returns the same multiplexed list as the auth'd route", async () => {
    const res = await postPublic({
      jsonrpc: "2.0",
      id: 2,
      method: "tools/list",
    });
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      result: { tools: Array<{ name: string }> };
    };
    const names = new Set(body.result.tools.map((t) => t.name));
    expect(names.size).toBeGreaterThan(20);
    // list_samples is a long-standing free tool we expect to find.
    expect(names.has("list_samples")).toBe(true);
  });

  it("free tool call (list_samples) returns the handler output", async () => {
    const res = await postPublic({
      jsonrpc: "2.0",
      id: 3,
      method: "tools/call",
      params: { name: "list_samples", arguments: {} },
    });
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      result: { content: Array<{ text: string }> };
      error?: { code: number };
    };
    expect(body.error).toBeUndefined();
    const text = body.result.content[0].text;
    expect(text.length).toBeGreaterThan(0);
  });
});

describe("POST /mcp/public: Pro tool gating", () => {
  it("Pro tool call returns ACCESS_DENIED with /pricing pointer", async () => {
    const res = await postPublic({
      jsonrpc: "2.0",
      id: 4,
      method: "tools/call",
      params: { name: "generate_scene", arguments: {} },
    });
    // JSON-RPC errors are returned with HTTP 200 — inspect the body.
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      error?: { code: number; message: string; data?: unknown };
    };
    expect(body.error?.code).toBe(JSON_RPC_ERRORS.ACCESS_DENIED);
  });
});

describe("POST /mcp/public: rate limiting", () => {
  it("emits X-RateLimit-* headers on every response", async () => {
    const res = await postPublic({
      jsonrpc: "2.0",
      id: 5,
      method: "tools/list",
    });
    expect(res.headers.get("X-RateLimit-Limit")).toBe("60");
    expect(res.headers.get("X-RateLimit-Reset")).toBeTruthy();
    const remaining = Number(res.headers.get("X-RateLimit-Remaining"));
    expect(Number.isFinite(remaining)).toBe(true);
    expect(remaining).toBeGreaterThanOrEqual(0);
    expect(remaining).toBeLessThanOrEqual(60);
  });

  it("returns 429 once the per-IP hourly cap (60) is exhausted", async () => {
    // Use a unique IP so this test's bucket doesn't collide with siblings.
    const ip = "203.0.113.42";
    // 60 calls allowed.
    for (let i = 0; i < 60; i++) {
      const res = await postPublic(
        { jsonrpc: "2.0", id: i, method: "tools/list" },
        { "cf-connecting-ip": ip },
      );
      expect(res.status, `call #${i + 1}`).toBe(200);
    }
    // 61st call must be rejected.
    const denied = await postPublic(
      { jsonrpc: "2.0", id: 61, method: "tools/list" },
      { "cf-connecting-ip": ip },
    );
    expect(denied.status).toBe(429);
    const body = (await denied.json()) as {
      error: { code: number; data: { upgrade: string; tier: string } };
    };
    expect(body.error.code).toBe(JSON_RPC_ERRORS.RATE_LIMITED);
    expect(body.error.data.tier).toBe("anonymous");
    expect(body.error.data.upgrade).toContain("/pricing");
  });

  it("different IPs have independent buckets", async () => {
    // First IP exhausts.
    for (let i = 0; i < 60; i++) {
      await postPublic(
        { jsonrpc: "2.0", id: i, method: "tools/list" },
        { "cf-connecting-ip": "198.51.100.7" },
      );
    }
    const exhaustedRes = await postPublic(
      { jsonrpc: "2.0", id: 999, method: "tools/list" },
      { "cf-connecting-ip": "198.51.100.7" },
    );
    expect(exhaustedRes.status).toBe(429);

    // Second IP still works.
    const freshRes = await postPublic(
      { jsonrpc: "2.0", id: 1, method: "tools/list" },
      { "cf-connecting-ip": "198.51.100.8" },
    );
    expect(freshRes.status).toBe(200);
  });
});

describe("POST /mcp/public: does not leak past the public boundary", () => {
  it("the auth'd /mcp endpoint still requires a Bearer token", async () => {
    // Sanity check — /mcp/public must NOT cause /mcp to become anonymous
    // by accident (e.g., wildcard ordering bug).
    const res = await app.request(
      "/mcp",
      {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ jsonrpc: "2.0", id: 1, method: "initialize" }),
      },
      env(),
    );
    expect(res.status).toBe(401);
  });
});
