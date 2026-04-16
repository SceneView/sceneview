/**
 * `/mcp/public`: anonymous, IP-rate-limited entry point for the gateway.
 *
 * Empirical motivation: marketplaces (Smithery, mcp.so, ChatGPT MCP picker,
 * Claude Desktop's "remote MCP" connector, etc.) want a single HTTP MCP URL
 * they can list. The authenticated `/mcp` route returns 401 without a Bearer
 * token, which silently breaks discovery. This public route accepts anonymous
 * JSON-RPC, dispatches the request through the same transport, and lets the
 * existing access gate (`canCallTool`) deny Pro tools naturally — anonymous
 * callers have no `ctx.tier`, so anything not flagged `free` is rejected with
 * the standard JSON-RPC ACCESS_DENIED error pointing to /pricing.
 *
 * Rate limiting: hourly per-IP, hashed before storage. The limit is generous
 * enough for discovery / "try it" sessions but low enough to prevent abuse
 * without authentication. Unauthenticated callers should not be the dominant
 * traffic source — paying users use `/mcp` with a Bearer token.
 */

import { Hono } from "hono";
import type { Env } from "../env.js";
import {
  handleMcpRequest,
  JSON_RPC_ERRORS,
  type JsonRpcResponse,
} from "../mcp/transport.js";
import { canCallTool } from "../mcp/access.js";
import { checkAndIncrementHourly } from "../rate-limit/kv-counter.js";

/**
 * Per-IP hourly cap for the anonymous endpoint. Picked to comfortably fit a
 * marketplace probing the server (1-2 calls every few minutes) and a curious
 * developer trying things out, while making bulk scraping unattractive.
 */
const PUBLIC_HOURLY_LIMIT = 60;

/** Returns the request's client IP, falling back to a stable sentinel. */
function getClientIp(req: Request): string {
  return (
    req.headers.get("cf-connecting-ip") ??
    req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ??
    "unknown"
  );
}

/** SHA-256 hash of the IP, hex-encoded — never stored in the clear. */
async function hashIp(ip: string): Promise<string> {
  const data = new TextEncoder().encode(`mcp-public:${ip}`);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

/** Builds the JSON-RPC 2.0 error envelope returned on rate-limit. */
function rateLimitedBody(
  resetAt: number,
  limit: number,
  remaining: number,
): JsonRpcResponse {
  return {
    jsonrpc: "2.0",
    id: null,
    error: {
      code: JSON_RPC_ERRORS.RATE_LIMITED,
      message: "Public endpoint rate limit exceeded — please subscribe for higher limits",
      data: {
        tier: "anonymous",
        limit,
        remaining,
        reset: new Date(resetAt).toISOString(),
        upgrade: "https://sceneview-mcp.mcp-tools-lab.workers.dev/pricing",
      },
    },
  };
}

/**
 * Returns a Hono router exposing the anonymous MCP endpoint at the path the
 * caller mounts it under (typically `/public` inside the `/mcp` group).
 */
export function mcpPublicRoutes(): Hono<{ Bindings: Env }> {
  const app = new Hono<{ Bindings: Env }>();

  // Both POST (Streamable HTTP) and GET (info / SSE handshake) are exposed
  // without auth so marketplaces and clients can discover the server.
  const handler = async (c: import("hono").Context<{ Bindings: Env }>) => {
    const ip = getClientIp(c.req.raw);
    const ipHash = await hashIp(ip);

    const decision = await checkAndIncrementHourly(
      c.env.RL_KV,
      `public:${ipHash}`,
      PUBLIC_HOURLY_LIMIT,
    );

    if (!decision.allowed) {
      const body = rateLimitedBody(
        decision.resetAt,
        decision.limit,
        decision.remaining,
      );
      return c.json(body, 429, {
        "X-RateLimit-Limit": String(decision.limit),
        "X-RateLimit-Remaining": String(decision.remaining),
        "X-RateLimit-Reset": String(Math.floor(decision.resetAt / 1000)),
        "Retry-After": String(
          Math.max(1, Math.ceil((decision.resetAt - Date.now()) / 1000)),
        ),
      });
    }

    // No `dispatchContext` — the access gate sees `tier === undefined` and
    // therefore allows only `free` tools. Pro tools naturally return the
    // standard ACCESS_DENIED JSON-RPC error pointing at /pricing.
    const response = await handleMcpRequest(c.req.raw, {
      kv: c.env.RL_KV,
      canCallTool,
    });

    // Surface the rate-limit headers on every response so well-behaved
    // clients can self-pace before they hit the cap.
    response.headers.set("X-RateLimit-Limit", String(decision.limit));
    response.headers.set(
      "X-RateLimit-Remaining",
      String(decision.remaining - 1),
    );
    response.headers.set(
      "X-RateLimit-Reset",
      String(Math.floor(decision.resetAt / 1000)),
    );
    return response;
  };

  app.post("/", handler);
  app.get("/", handler);

  return app;
}
