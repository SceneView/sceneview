/** @jsxImportSource hono/jsx */

/**
 * Checkout success page — displayed at `/checkout/success?session_id=cs_...`
 * once Stripe redirects the buyer back after a paid subscription.
 *
 * This is the ONLY place the buyer ever sees their API key in
 * plaintext. The KV entry is deleted on first read by the route
 * handler, so a refresh after the first successful render shows the
 * "key already consumed" variant of the page.
 */

import type { FC } from "hono/jsx";
import { Layout } from "./layout.js";
import { renderToHtml } from "./render.js";
import type { UserTier } from "../db/schema.js";

/** Props for {@link CheckoutSuccess}. */
export interface CheckoutSuccessProps {
  /** The plaintext sv_live_ API key to display. */
  apiKey: string;
  /** The buyer email, shown for confirmation. */
  email: string;
  /** The tier the buyer is now on. */
  tier: UserTier;
}

/** Page shown when the KV handoff succeeds and the key can be displayed. */
export const CheckoutSuccess: FC<CheckoutSuccessProps> = (props) => {
  const tierLabel =
    props.tier === "team" ? "Team" : props.tier === "pro" ? "Pro" : "Free";
  // Claude Desktop does NOT yet support HTTP MCP servers — only stdio.
  // So we ship the npm lite package (`sceneview-mcp`) which runs
  // locally and forwards Pro tool calls to the gateway. Any buyer who
  // copy-pastes this lands in a working state after restarting Claude
  // Desktop. See .claude/NOTICE-2026-04-11-mcp-gateway-live.md §7.
  const claudeDesktopConfig = `{
  "mcpServers": {
    "sceneview": {
      "command": "npx",
      "args": ["-y", "sceneview-mcp"],
      "env": {
        "SCENEVIEW_API_KEY": "${props.apiKey}"
      }
    }
  }
}`;
  // Cursor supports HTTP MCP natively, so we can talk to the gateway
  // directly — no npm package required. Must use the real subdomain
  // `mcp-tools-lab.workers.dev`, NOT the pre-pivot phantom NXDOMAIN
  // `sceneview-mcp.workers.dev` that briefly shipped in docs.
  const cursorConfig = `{
  "mcpServers": {
    "sceneview": {
      "url": "https://sceneview-mcp.mcp-tools-lab.workers.dev/mcp",
      "headers": {
        "Authorization": "Bearer ${props.apiKey}"
      }
    }
  }
}`;
  const curlExample = `curl -X POST https://sceneview-mcp.mcp-tools-lab.workers.dev/mcp \\
  -H "Authorization: Bearer ${props.apiKey}" \\
  -H "Content-Type: application/json" \\
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'`;

  return (
    <Layout
      title={`Welcome to SceneView MCP ${tierLabel}`}
      description="Your SceneView MCP subscription is active. Copy your API key and paste it into your MCP client."
      active="home"
    >
      <section class="hero" style="padding-top:2rem;padding-bottom:2rem;">
        <h1>
          <span aria-hidden="true">🎉</span> Welcome to SceneView MCP {tierLabel}
        </h1>
        <p class="lead">
          Your subscription is active for <strong>{props.email}</strong>.
          Copy your API key below — it will only be shown once.
        </p>
      </section>

      <section
        class="card"
        style="border-color:var(--sv-primary);box-shadow:var(--sv-shadow);"
      >
        <h2 style="margin-top:0;">Your API key</h2>
        <div
          class="alert"
          style="background:rgba(239,68,68,.08);border-color:rgba(239,68,68,.3);color:#991b1b;"
        >
          <strong>Save this key now.</strong> We do not store the plaintext
          value. If you lose it you will need to contact support to have
          a new one issued.
        </div>
        <pre
          id="api-key-block"
          style="user-select:all;word-break:break-all;white-space:pre-wrap;"
        ><code>{props.apiKey}</code></pre>
        <p style="margin-top:.5rem;font-size:.875rem;">
          Triple-click to select, then copy.
        </p>
      </section>

      <section>
        <h2>Install in Claude Desktop</h2>
        <p>
          Open <code>~/Library/Application Support/Claude/claude_desktop_config.json</code>
          {" "}(or the equivalent on Windows/Linux) and paste:
        </p>
        <pre><code>{claudeDesktopConfig}</code></pre>
      </section>

      <section>
        <h2>Install in Cursor (or any HTTP-MCP client)</h2>
        <p>
          Cursor supports HTTP MCP servers natively — you can talk to
          the gateway directly without the npm package. Open Cursor
          Settings → <em>MCP</em> → Add new, and paste:
        </p>
        <pre><code>{cursorConfig}</code></pre>
      </section>

      <section>
        <h2>Test your key now</h2>
        <p>
          Run the following curl command in your terminal to confirm
          everything is wired up:
        </p>
        <pre><code>{curlExample}</code></pre>
      </section>

      <section>
        <h2>What comes next</h2>
        <ul style="color:var(--sv-fg-muted);line-height:1.8;">
          <li>
            Your subscription is managed by Stripe — invoices, payment
            method updates, and cancellation all flow through the Stripe
            email receipts you just received.
          </li>
          <li>
            Rate limits follow the {tierLabel} tier (see the{" "}
            <a href="/pricing">pricing page</a> for numbers).
          </li>
          <li>
            Need a new key or ran into an issue? Email us at{" "}
            <a href="mailto:hello@sceneview.dev">hello@sceneview.dev</a>
            {" "}and reference the session id from your browser address bar.
          </li>
        </ul>
      </section>
    </Layout>
  );
};

/** Variant shown when the KV entry was already consumed or has expired. */
export const CheckoutSuccessConsumed: FC<{ sessionId: string | null }> = (
  props,
) => (
  <Layout
    title="Checkout key already shown"
    description="This checkout session has already been used to reveal an API key, or its 24-hour window has expired."
    active="home"
  >
    <section class="hero" style="padding-top:2rem;padding-bottom:2rem;">
      <h1>This page has already been used</h1>
      <p class="lead">
        API keys can only be displayed once. If you missed it, we can
        issue you a fresh one — just drop us a note.
      </p>
    </section>

    <section class="card">
      <h2 style="margin-top:0;">Contact support</h2>
      <p>
        Email <a href="mailto:hello@sceneview.dev">hello@sceneview.dev</a>
        {" "}and mention:
      </p>
      <ul style="color:var(--sv-fg-muted);line-height:1.8;">
        <li>
          The email address you used at checkout
        </li>
        <li>
          Your Stripe session id:{" "}
          <code>{props.sessionId ?? "(missing)"}</code>
        </li>
      </ul>
      <p style="margin-top:1.5rem;">
        <a href="/pricing" class="btn btn-secondary">
          Back to pricing
        </a>
      </p>
    </section>
  </Layout>
);

/** Top-level renderer for the happy path. */
export function renderCheckoutSuccess(
  props: CheckoutSuccessProps,
): Promise<string> {
  return renderToHtml(<CheckoutSuccess {...props} />);
}

/** Top-level renderer for the "already consumed / expired" path. */
export function renderCheckoutSuccessConsumed(
  sessionId: string | null,
): Promise<string> {
  return renderToHtml(<CheckoutSuccessConsumed sessionId={sessionId} />);
}
