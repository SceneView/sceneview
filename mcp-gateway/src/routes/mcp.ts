/**
 * `/mcp` route: the critical path for every hosted MCP request.
 *
 * Middleware chain (order is load-bearing):
 *   1. authMiddleware    — populates c.var.auth, 401 JSON-RPC on failure
 *   2. rateLimitMiddleware — 429 JSON-RPC + X-RateLimit-* headers
 *   3. handleMcpRequest from the transport — JSON-RPC parse + dispatch
 *   4. Post-handler: async usage logging in D1 + KV quota cache bump
 *
 * This file stays thin: the complex parts live in the transport
 * module (step 5) and the middleware modules (steps 8-9). It just
 * assembles them and decides how to observe the outcome.
 */

import { Hono } from "hono";
import type { Env } from "../env.js";
import { authMiddleware, type AuthVariables } from "../auth/middleware.js";
import { rateLimitMiddleware } from "../middleware/rate-limit.js";
import { handleMcpRequest, JSON_RPC_ERRORS } from "../mcp/transport.js";
import { canCallTool, getToolTier } from "../mcp/access.js";
import { insertUsageRecord, monthBucket } from "../db/usage.js";
import { incrementQuotaCache } from "../rate-limit/quotas.js";
import { touchApiKey } from "../db/api-keys.js";
import { mcpPublicRoutes } from "./mcp-public.js";

type McpBindings = { Bindings: Env; Variables: AuthVariables };

/** Creates a router group mounted under `/mcp`. */
export function mcpRoutes(): Hono<McpBindings> {
  const app = new Hono<McpBindings>();

  // Anonymous public sub-route — MUST be registered BEFORE the wildcard
  // auth middleware below so `/mcp/public` doesn't hit the 401 gate.
  // Marketplaces (Smithery, mcp.so, ChatGPT MCP picker, Claude Desktop's
  // remote MCP connector) need an unauthenticated URL to list the server;
  // this route serves the free tools to anonymous clients with an
  // IP-based rate limit. See `routes/mcp-public.ts` for the full context.
  app.route("/public", mcpPublicRoutes());

  // Apply the middleware chain to every /mcp request.
  app.use("*", authMiddleware(), rateLimitMiddleware());

  // POST is the nominal path for Streamable HTTP.
  app.post("/", async (c) => {
    const auth = c.get("auth");
    const dispatchContext = {
      userId: auth.userId,
      apiKeyId: auth.keyId,
      tier: auth.tier,
    };

    // Read the body once so we can inspect it for usage logging.
    const rawBody = await c.req.raw.clone().text();

    // Hand off to the transport, preserving headers and method.
    const response = await handleMcpRequest(c.req.raw, {
      kv: c.env.RL_KV,
      dispatchContext,
      canCallTool,
    });

    // Parse the body to figure out what tool was called, if any.
    // Failures here are not fatal: we just skip the observation.
    try {
      const parsed = JSON.parse(rawBody) as {
        method?: string;
        params?: { name?: string };
      };
      if (parsed.method === "tools/call" && typeof parsed.params?.name === "string") {
        const toolName = parsed.params.name;

        // JSON-RPC errors are returned with HTTP 200 — inspect the body
        // to figure out whether the call was allowed, denied, or errored.
        let jsonRpcErrorCode: number | null = null;
        try {
          const bodyText = await response.clone().text();
          if (bodyText) {
            const payload = JSON.parse(bodyText) as {
              error?: { code?: number };
            };
            if (typeof payload?.error?.code === "number") {
              jsonRpcErrorCode = payload.error.code;
            }
          }
        } catch {
          // Non-JSON or unreadable — treat as ok if HTTP says so.
        }

        const status =
          response.status >= 500
            ? "error"
            : response.status === 429 ||
                jsonRpcErrorCode === JSON_RPC_ERRORS.RATE_LIMITED
              ? "rate_limited"
              : response.status === 401 ||
                  jsonRpcErrorCode === JSON_RPC_ERRORS.UNAUTHORIZED ||
                  jsonRpcErrorCode === JSON_RPC_ERRORS.ACCESS_DENIED
                ? "denied"
                : jsonRpcErrorCode !== null
                  ? "error"
                  : "ok";

        // Fire-and-forget the usage log. We rely on the Workers
        // runtime's ctx.waitUntil when available; in tests without
        // ExecutionContext this resolves inline.
        const promise = (async () => {
          try {
            await insertUsageRecord(c.env.DB, {
              apiKeyId: auth.keyId,
              userId: auth.userId,
              toolName,
              tierRequired: getToolTier(toolName),
              status,
            });
            if (status === "ok") {
              await incrementQuotaCache(c.env.RL_KV, auth.keyHash, monthBucket());
              await touchApiKey(c.env.DB, auth.keyId);
            }
          } catch {
            // Never let a logging failure bubble up into the user response.
          }
        })();

        const exec = c.executionCtx as
          | { waitUntil?: (p: Promise<unknown>) => void }
          | undefined;
        if (exec?.waitUntil) {
          exec.waitUntil(promise);
        } else {
          await promise;
        }
      }
    } catch {
      // Ignore parse errors — JSON-RPC layer already handled them.
    }

    return response;
  });

  // GET is reserved by the spec for SSE long-poll, which we do not
  // implement yet. Return the same 501 the transport would produce.
  app.get("/", async (c) => {
    return handleMcpRequest(c.req.raw, {
      kv: c.env.RL_KV,
      canCallTool,
    });
  });

  return app;
}
