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

// ── OpenAI App Store hero composition ──────────────────────────────────────
//
// Reproduces the OpenAI Pizzaz example layout: macOS Safari frame on the
// left showing chatgpt.com with the SceneView widget rendered inside the
// chat, and an iPhone frame on the right showing the same widget in the
// mobile ChatGPT app. The widget iframes load `/widget/3d-viewer.html`
// from the same origin so the rendering is live SceneView.js — not a
// mockup. Screenshot this page at 2560x1600 for the OpenAI App Store
// listing image.

app.get("/hero", (c) => {
  // v3: bring back the ChatGPT contextualization (browser frame showing
  // chatgpt.com with the SceneView widget rendered inside the chat),
  // and pair it with smaller iPad + iPhone frames showing the ChatGPT
  // mobile app — proving "this works on every device, not just mobile".
  // Three different consumer-recognizable models keep the variety
  // (chair shopping · city tour · watch try-on) so it doesn't read as
  // "just a model viewer".
  // CarConcept = mainstream "wow" object on the centerpiece, rendered
  // through the SHOWCASE widget (3-point lighting + bloom + 3D text) so
  // it visibly screams "this is real PBR 3D, not a flat picture".
  const MAC_MODEL = "https://sceneview.github.io/models/platforms/CarConcept.glb";
  const TAB_MODEL = "https://sceneview.github.io/models/platforms/NightCity.glb";
  const PHONE_MODEL = "https://sceneview.github.io/models/platforms/MaterialsVariantsShoe.glb";
  const showcase = (m: string, t: string) =>
    `/widget/scene-showcase.html?modelUrl=${encodeURIComponent(m)}` +
    `&title=${encodeURIComponent(t)}`;
  const widget = (m: string, t: string) =>
    `/widget/3d-viewer.html?modelUrl=${encodeURIComponent(m)}` +
    `&title=${encodeURIComponent(t)}&autoRotate=1`;
  const html = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8" />
<title>SceneView — 3D &amp; AR inside ChatGPT</title>
<style>
  :root { color-scheme: light; }
  html, body { margin: 0; padding: 0; background: linear-gradient(180deg, #f6f7fb 0%, #e9ecf3 100%); font-family: -apple-system, "SF Pro Display", system-ui, sans-serif; -webkit-font-smoothing: antialiased; color: #0f172a; }
  .stage { width: 2560px; height: 1600px; padding: 70px 110px; box-sizing: border-box; display: flex; flex-direction: column; gap: 44px; }

  /* ── Header ────────────────────────────────────────────────────────── */
  .header { display: flex; flex-direction: column; gap: 18px; align-items: flex-start; }
  .headline { font-size: 86px; font-weight: 700; line-height: 1.02; letter-spacing: -0.025em; color: #0b1020; max-width: 2300px; }
  .headline em { font-style: normal; background: linear-gradient(90deg, #2563eb 0%, #7c3aed 50%, #ec4899 100%); -webkit-background-clip: text; background-clip: text; color: transparent; }
  .subline { font-size: 30px; font-weight: 400; color: #475569; line-height: 1.35; max-width: 2100px; }

  /* ── Devices canvas ───────────────────────────────────────────────── */
  .canvas { flex: 1; display: flex; align-items: center; justify-content: center; gap: 48px; min-height: 0; }

  /* MacBook (centerpiece, shows chatgpt.com with widget in conversation) */
  .macbook { display: flex; flex-direction: column; align-items: center; }
  .mac-screen { width: 1500px; height: 940px; background: #0b1020; border-radius: 22px 22px 8px 8px; padding: 22px; box-sizing: border-box; box-shadow: 0 40px 100px rgba(15,23,42,.22), 0 0 0 5px #c5c9d2; position: relative; }
  .mac-cam { position: absolute; top: 8px; left: 50%; transform: translateX(-50%); width: 9px; height: 9px; background: #1e293b; border-radius: 50%; }
  .mac-display { width: 100%; height: 100%; background: #fff; border-radius: 6px; overflow: hidden; display: flex; flex-direction: column; }
  .mac-base { width: 1700px; height: 28px; background: linear-gradient(180deg, #d4d7df, #b6bac3); margin-top: -2px; border-radius: 0 0 16px 16px; position: relative; }
  .mac-base::before { content: ""; position: absolute; top: 0; left: 50%; transform: translateX(-50%); width: 240px; height: 12px; background: #9ca3af; border-radius: 0 0 14px 14px; }

  /* Browser chrome inside MacBook (Safari/Chrome look) */
  .browser-bar { display: flex; align-items: center; gap: 14px; padding: 16px 22px; background: #f9fafb; border-bottom: 1px solid #e5e7eb; flex-shrink: 0; }
  .traffic { display: flex; gap: 8px; }
  .traffic span { width: 14px; height: 14px; border-radius: 50%; }
  .traffic .r { background: #ff5f57; } .traffic .y { background: #febc2e; } .traffic .g { background: #28c840; }
  .url { flex: 1; display: flex; justify-content: center; }
  .url-inner { background: #fff; border: 1px solid #e5e7eb; border-radius: 8px; padding: 9px 24px; font-size: 18px; color: #475569; min-width: 320px; text-align: center; }

  /* ChatGPT shell inside browser */
  .gpt-side { display: flex; flex: 1; min-height: 0; }
  .gpt-rail { width: 220px; background: #f7f7f8; border-right: 1px solid #ececf1; padding: 18px 14px; flex-shrink: 0; }
  .gpt-rail-item { font-size: 14px; color: #374151; padding: 8px 12px; border-radius: 8px; margin-bottom: 4px; }
  .gpt-rail-item.active { background: #ececf1; font-weight: 500; }
  .gpt-main { flex: 1; display: flex; flex-direction: column; min-width: 0; background: #fff; }
  .gpt-bar { padding: 14px 22px; border-bottom: 1px solid #ececf1; display: flex; align-items: center; gap: 10px; }
  .gpt-bar-logo { width: 24px; height: 24px; border-radius: 50%; background: #111; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 700; }
  .gpt-bar-name { font-size: 15px; color: #111; font-weight: 600; }
  .conv { flex: 1; padding: 18px 28px 22px; display: flex; flex-direction: column; gap: 12px; min-height: 0; overflow: hidden; }
  .msg-user { align-self: flex-end; max-width: 70%; background: #f4f4f5; padding: 12px 18px; border-radius: 18px; font-size: 16px; color: #111; flex-shrink: 0; }
  .msg-assist { display: flex; gap: 12px; align-items: flex-start; flex: 1; min-height: 0; }
  .msg-assist .avatar { width: 32px; height: 32px; border-radius: 50%; background: linear-gradient(135deg, #2563eb, #7c3aed); display: flex; align-items: center; justify-content: center; color: #fff; font-weight: 700; font-size: 14px; flex-shrink: 0; }
  .msg-assist .bubble { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 8px; min-height: 0; }
  .msg-assist .text { font-size: 16px; color: #374151; line-height: 1.4; flex-shrink: 0; }
  .widget-card { border-radius: 14px; overflow: hidden; border: 1px solid #e5e7eb; box-shadow: 0 6px 24px rgba(15,23,42,.08); flex: 1; min-height: 480px; display: flex; flex-direction: column; background: #0b1020; }
  .widget-card-bar { padding: 10px 16px; background: rgba(255,255,255,0.96); backdrop-filter: blur(8px); border-bottom: 1px solid #e5e7eb; display: flex; align-items: center; gap: 10px; flex-shrink: 0; color: #111; }
  .widget-icon { width: 26px; height: 26px; border-radius: 8px; background: linear-gradient(135deg, #2563eb, #7c3aed); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 700; flex-shrink: 0; }
  .widget-name { font-size: 14px; font-weight: 600; }
  .widget-tag { font-size: 12px; color: #64748b; margin-left: auto; }
  .scene { flex: 1; min-height: 0; position: relative; background: #0b1020; }
  .scene iframe { position: absolute; inset: 0; width: 100%; height: 100%; border: 0; }

  /* Right column: stacked iPad + iPhone (proves multi-device) */
  .stack { display: flex; flex-direction: column; gap: 36px; align-items: center; }

  .ipad-frame { width: 540px; height: 720px; background: #1f2937; border-radius: 36px; padding: 14px; box-sizing: border-box; box-shadow: 0 30px 80px rgba(15,23,42,.22), 0 0 0 3px #2c3441; position: relative; }
  .ipad-cam { position: absolute; top: 50%; left: 7px; transform: translateY(-50%); width: 7px; height: 7px; background: #0b1020; border-radius: 50%; }
  .ipad-screen { width: 100%; height: 100%; background: #fff; border-radius: 24px; overflow: hidden; display: flex; flex-direction: column; }

  .iphone-frame { width: 320px; height: 660px; background: linear-gradient(135deg, #2a2f38, #1a1d24); border-radius: 48px; padding: 9px; box-sizing: border-box; box-shadow: 0 30px 80px rgba(15,23,42,.28), 0 0 0 2px #3a3f48; position: relative; }
  .iphone-screen { width: 100%; height: 100%; background: #fff; border-radius: 40px; overflow: hidden; position: relative; display: flex; flex-direction: column; }
  .dynamic-island { position: absolute; top: 9px; left: 50%; transform: translateX(-50%); width: 96px; height: 26px; background: #000; border-radius: 14px; z-index: 5; }

  /* In-app ChatGPT mobile bar */
  .gpt-mobile-bar { padding: 38px 16px 10px; display: flex; align-items: center; gap: 8px; border-bottom: 1px solid #ececf1; flex-shrink: 0; }
  .gpt-mobile-bar.tablet { padding: 14px 20px; }
  .gpt-mobile-bar .gpt-bar-logo { width: 22px; height: 22px; font-size: 12px; }
  .gpt-mobile-bar .label { font-size: 13px; color: #111; font-weight: 600; }

  .mini-msg { padding: 10px 16px; font-size: 13px; color: #6b7280; flex-shrink: 0; }
  .mini-msg b { color: #111; font-weight: 600; }

  /* Bottom: platform pills with REAL official logos */
  .platforms { display: flex; gap: 22px; flex-wrap: wrap; align-items: center; justify-content: center; }
  .plat { background: #fff; border: 1px solid #e2e8f0; border-radius: 22px; padding: 18px 30px; font-size: 26px; color: #0f172a; font-weight: 600; box-shadow: 0 4px 14px rgba(15,23,42,.06); display: flex; align-items: center; gap: 16px; }
  .plat img { width: 48px; height: 48px; display: block; }
  .plat.dark { background: #0b1020; color: #fff; border-color: #1e293b; }
</style>
</head>
<body>
<div class="stage">

  <div class="header">
    <div class="headline">See anything in 3D. Place it in AR. <em>Anywhere.</em></div>
    <div class="subline">Ask ChatGPT to render any object with real PBR lighting and shadows, then explore from every angle on iPhone, iPad, Mac, Web, Android &amp; Apple Vision Pro.</div>
  </div>

  <div class="canvas">

    <!-- MacBook centerpiece: chatgpt.com showing a SceneView widget in the chat -->
    <div class="macbook">
      <div class="mac-screen">
        <div class="mac-cam"></div>
        <div class="mac-display">
          <div class="browser-bar">
            <div class="traffic"><span class="r"></span><span class="y"></span><span class="g"></span></div>
            <div class="url"><div class="url-inner">chatgpt.com</div></div>
            <div style="width:80px"></div>
          </div>
          <div class="gpt-side">
            <div class="gpt-rail">
              <div class="gpt-rail-item active">SceneView 3D &amp; AR</div>
              <div class="gpt-rail-item">New chat</div>
              <div class="gpt-rail-item">Living room ideas</div>
              <div class="gpt-rail-item">Watch shopping</div>
            </div>
            <div class="gpt-main">
              <div class="gpt-bar">
                <div class="gpt-bar-logo">G</div>
                <div class="gpt-bar-name">ChatGPT</div>
              </div>
              <div class="conv">
                <div class="msg-user">"Show me the new car concept in 3D"</div>
                <div class="msg-assist">
                  <div class="avatar">S</div>
                  <div class="bubble">
                    <div class="text">Here it is — full PBR materials, studio lighting and reflections. Drag to spin, scroll to zoom, or tap to view in AR.</div>
                    <div class="widget-card">
                      <div class="widget-card-bar">
                        <div class="widget-icon">S</div>
                        <div class="widget-name">SceneView 3D &amp; AR</div>
                        <div class="widget-tag">Live · Interactive · PBR</div>
                      </div>
                      <div class="scene"><iframe src="${showcase(MAC_MODEL, "Car Concept")}" loading="eager"></iframe></div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="mac-base"></div>
    </div>

    <!-- Right column: iPad + iPhone showing ChatGPT mobile -->
    <div class="stack">

      <div class="ipad-frame">
        <div class="ipad-cam"></div>
        <div class="ipad-screen">
          <div class="gpt-mobile-bar tablet">
            <div class="gpt-bar-logo">G</div>
            <div class="label">ChatGPT · SceneView</div>
          </div>
          <div class="mini-msg"><b>You:</b> "Take me on a tour of this city"</div>
          <div class="scene"><iframe src="${widget(TAB_MODEL, "Night City")}" loading="eager"></iframe></div>
        </div>
      </div>

      <div class="iphone-frame">
        <div class="iphone-screen">
          <div class="dynamic-island"></div>
          <div class="gpt-mobile-bar">
            <div class="gpt-bar-logo">G</div>
            <div class="label">ChatGPT · SceneView</div>
          </div>
          <div class="mini-msg"><b>You:</b> "Try this watch on"</div>
          <div class="scene"><iframe src="${widget(PHONE_MODEL, "Watch")}" loading="eager"></iframe></div>
        </div>
      </div>

    </div>

  </div>

  <div class="platforms">
    <div class="plat"><img src="https://cdn.simpleicons.org/apple/000000" alt="Apple" />iPhone · iPad · Mac</div>
    <div class="plat"><img src="https://cdn.simpleicons.org/applevisionpro/000000" alt="Vision Pro" />Vision Pro</div>
    <div class="plat"><img src="https://cdn.simpleicons.org/android/3DDC84" alt="Android" />Android · TV</div>
    <div class="plat"><img src="https://cdn.simpleicons.org/googlechrome/4285F4" alt="Web" />Web</div>
    <div class="plat"><img src="https://cdn.simpleicons.org/flutter/02569B" alt="Flutter" />Flutter</div>
    <div class="plat"><img src="https://cdn.simpleicons.org/react/61DAFB" alt="React Native" />React Native</div>
  </div>

</div>
</body>
</html>`;
  return c.html(html);
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
