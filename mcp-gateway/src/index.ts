import { Hono } from "hono";
import type { Env } from "./env.js";
import type { AuthVariables } from "./auth/middleware.js";
import { mcpRoutes } from "./routes/mcp.js";
import { authRoutes } from "./routes/auth.js";
import { dashboardRoutes } from "./routes/dashboard.js";
import { billingRoutes } from "./routes/billing.js";
import { checkoutSuccessRoutes } from "./routes/checkout-success.js";
import { webhookRoutes } from "./routes/webhooks.js";

const app = new Hono<{ Bindings: Env; Variables: AuthVariables }>();

// ── Health check ────────────────────────────────────────────────────────────

app.get("/health", (c) =>
  c.json({
    ok: true,
    service: "sceneview-mcp-gateway",
    version: "0.0.1",
  }),
);

// ── OpenAI Apps domain verification ────────────────────────────────────────
//
// platform.openai.com/apps-manage issues a one-time token that must be served
// at the well-known path on the same hostname as the MCP URL. The token is
// host-bound — it confirms to OpenAI that whoever wrote the listing also
// controls the worker. Once verified, OpenAI re-checks periodically; rotating
// the token is rare so a hard-coded constant is fine and auditable.
//
// Token issued for the SceneView 3D & AR app id
// asdk_app_69e17c43573c819186988306509623c2 on 2026-04-18.
const OPENAI_APPS_CHALLENGE_TOKEN =
  "C-cDfPE9Q15PrWJahuZSUyJCHLETC4DwV4fKZtqHMrw";

app.get("/.well-known/openai-apps-challenge", (c) =>
  c.text(OPENAI_APPS_CHALLENGE_TOKEN, 200, {
    "content-type": "text/plain; charset=utf-8",
    "cache-control": "public, max-age=3600",
  }),
);

// ── Widget preview routes ──────────────────────────────────────────────────
//
// The MCP `resources/read` method is the canonical way for OpenAI to fetch
// the widget bundle, but humans (and screenshot capture tools) cannot easily
// drive a JSON-RPC call from a browser. Mirror each widget at a normal
// `/widget/<name>.html` HTTP path so anyone can preview the UI live by
// pasting a URL — e.g.
//
//   /widget/3d-viewer.html?modelUrl=https://sceneview.github.io/models/Astronaut.glb&title=Astronaut
//
// The widget reads the same query-string fallback it uses for direct
// preview, so the standalone view matches what ChatGPT renders.

import { WIDGETS } from "./mcp/widgets.js";

app.get("/widget/:name", (c) => {
  const name = c.req.param("name");
  const uri = `ui://widget/${name}`;
  const widget = WIDGETS[uri];
  if (!widget) {
    return c.text(`Unknown widget: ${name}`, 404);
  }
  return c.html(widget.html);
});

// ── MCP endpoint ────────────────────────────────────────────────────────────

app.route("/mcp", mcpRoutes());

// ── Dashboard HTML routes (landing, pricing, docs, dashboard, billing) ─────

app.route("/", dashboardRoutes());

// ── Dashboard auth stubs — magic-link is disabled in the MVP ──────────────
//
// The /login, /auth/verify and /auth/logout routes currently return
// 503 Service Unavailable. They are kept in the router so that bots and
// old links get a clean HTTP response instead of a 404. See
// `routes/auth.ts` for the full MVP-disabled stub.

app.route("/", authRoutes());

// ── Billing actions (Stripe checkout) ──────────────────────────────────────

app.route("/", billingRoutes());

// ── Checkout success page (reads the KV handoff) ───────────────────────────

app.route("/", checkoutSuccessRoutes());

// ── Stripe webhook receiver ────────────────────────────────────────────────

app.route("/", webhookRoutes());

// ── Fallback 404 ────────────────────────────────────────────────────────────

app.notFound((c) => c.json({ error: "Not Found" }, 404));

export default app;
