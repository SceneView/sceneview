// ─── Anonymous opt-out telemetry for sceneview-mcp ───────────────────────────
//
// Fire-and-forget, non-blocking, no personal data. Ever.
//
// What gets sent:
//   - timestamp (UTC ISO string)
//   - event: "init" | "tool"
//   - client: MCP client name (e.g. "claude-desktop", "cursor")
//   - clientVersion: MCP client version string reported during handshake
//   - mcpVersion: this package's version
//   - tier: "free" | "pro"
//   - tool?: tool name (only for "tool" events)
//
// What NEVER gets sent:
//   - IP address (the endpoint strips it server-side; we never send headers)
//   - hostname, OS user, machine id
//   - prompt content, tool arguments, tool results
//   - API keys, billing info
//
// Opt out with `SCENEVIEW_TELEMETRY=0` or by running in CI (`CI=true`).

import type { Tier } from "./tiers.js";

// Worker implementation: telemetry-worker/ (Hono + D1 + KV rate limiting).
// Deploy with: cd telemetry-worker && see DEPLOY.md
const TELEMETRY_ENDPOINT = "https://sceneview-telemetry.mcp-tools-lab.workers.dev/v1/events";
const TELEMETRY_BATCH_ENDPOINT = "https://sceneview-telemetry.mcp-tools-lab.workers.dev/v1/batch";

// Hard cap so we never hang the process on a slow endpoint.
const TELEMETRY_TIMEOUT_MS = 2000;

// Client-side batching: flush when buffer reaches this size or after this delay.
const BATCH_MAX_SIZE = 10;
const BATCH_FLUSH_INTERVAL_MS = 30_000;

// Shared between init and tool events. Populated by `recordClientInit`.
interface ClientContext {
  client: string;
  clientVersion: string;
}

/** Exposed for tests. */
export interface TelemetryPayload {
  timestamp: string;
  event: "init" | "tool";
  client: string;
  clientVersion: string;
  mcpVersion: string;
  tier: Tier;
  tool?: string;
}

let clientContext: ClientContext | undefined;

// ─── Client-side batch buffer ─────────────────────────────────────────────────
let buffer: TelemetryPayload[] = [];
let flushTimer: ReturnType<typeof setTimeout> | undefined;

// Read lazily so tests that mutate env vars between runs see the latest value.
function isEnabled(): boolean {
  if (process.env.SCENEVIEW_TELEMETRY === "0") return false;
  if (process.env.CI === "true") return false;
  return true;
}

// Read the package version from the build-time generated module so the
// telemetry payload reports the real published version. Pre-4.0.8 this
// fell back to a hardcoded "4.0.0" string that was never bumped, which
// made it impossible to track adoption of any new release.
import { PACKAGE_VERSION } from "./generated/version.js";

function getMcpVersion(): string {
  return process.env.SCENEVIEW_MCP_VERSION ?? PACKAGE_VERSION;
}

// Fire-and-forget POST of a single payload to the individual event endpoint.
// Used as fallback when batch delivery fails.
function sendSingle(payload: TelemetryPayload): void {
  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), TELEMETRY_TIMEOUT_MS);

    fetch(TELEMETRY_ENDPOINT, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(payload),
      signal: controller.signal,
    })
      .catch(() => {
        // Swallow all failures — network errors, DNS, timeouts, non-2xx.
        // Telemetry is best-effort.
      })
      .finally(() => {
        clearTimeout(timeoutId);
      });
  } catch {
    // Swallow synchronous throws too.
  }
}

// Fire-and-forget POST of a batch of payloads. Falls back to individual sends
// if the batch request fails.
function sendBatch(events: TelemetryPayload[]): void {
  if (events.length === 0) return;

  // Single-event shortcut: avoid the batch overhead.
  if (events.length === 1) {
    sendSingle(events[0]!);
    return;
  }

  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), TELEMETRY_TIMEOUT_MS);

    fetch(TELEMETRY_BATCH_ENDPOINT, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(events),
      signal: controller.signal,
    })
      .catch(() => {
        // Batch failed — fall back to individual sends so no data is lost.
        clearTimeout(timeoutId);
        for (const payload of events) {
          sendSingle(payload);
        }
      })
      .finally(() => {
        clearTimeout(timeoutId);
      });
  } catch {
    // Synchronous throw from fetch — fall back to individual sends.
    for (const payload of events) {
      sendSingle(payload);
    }
  }
}

// Schedule the 30-second auto-flush timer (idempotent — only one timer at a time).
function scheduleFlushTimer(): void {
  if (flushTimer !== undefined) return;
  flushTimer = setTimeout(() => {
    flushTimer = undefined;
    flushTelemetry();
  }, BATCH_FLUSH_INTERVAL_MS);
  // Allow Node.js to exit even if the timer is still pending.
  if (typeof flushTimer === "object" && flushTimer !== null && "unref" in flushTimer) {
    (flushTimer as { unref(): void }).unref();
  }
}

/**
 * Flush all buffered telemetry events immediately. Safe to call at any time.
 * Useful for graceful shutdown. Fire-and-forget — never throws.
 */
export function flushTelemetry(): void {
  if (flushTimer !== undefined) {
    clearTimeout(flushTimer);
    flushTimer = undefined;
  }
  if (buffer.length === 0) return;
  const snapshot = buffer.splice(0);
  sendBatch(snapshot);
}

// Push a payload into the buffer and flush when the batch is full.
function send(payload: TelemetryPayload): void {
  if (!isEnabled()) return;

  buffer.push(payload);
  scheduleFlushTimer();

  if (buffer.length >= BATCH_MAX_SIZE) {
    flushTelemetry();
  }
}

/** Exposed for tests — resets the cached client context and the batch buffer. */
export function __resetClientContext(): void {
  clientContext = undefined;
  buffer = [];
  if (flushTimer !== undefined) {
    clearTimeout(flushTimer);
    flushTimer = undefined;
  }
}

/**
 * Record an MCP client handshake. Called from the server's `oninitialized`
 * callback once the client has advertised its name/version. Safe to call
 * with `undefined` if the client didn't report info — the event is dropped.
 */
export function recordClientInit(clientInfo: { name: string; version: string } | undefined): void {
  if (!clientInfo) return;

  clientContext = {
    client: clientInfo.name,
    clientVersion: clientInfo.version,
  };

  if (!isEnabled()) return;

  send({
    timestamp: new Date().toISOString(),
    event: "init",
    client: clientContext.client,
    clientVersion: clientContext.clientVersion,
    mcpVersion: getMcpVersion(),
    tier: "free",
  });
}

/**
 * Record a tool invocation. Called from the `CallToolRequestSchema` handler
 * after the tier check has passed. `tier` is the tier the tool resolved to,
 * not the user's subscription status.
 */
export function recordToolCall(toolName: string, tier: Tier): void {
  if (!isEnabled()) return;

  // If the client never completed initialization (which shouldn't happen in
  // practice), fall back to "unknown" so we can still see the tool mix.
  const ctx = clientContext ?? { client: "unknown", clientVersion: "unknown" };

  send({
    timestamp: new Date().toISOString(),
    event: "tool",
    client: ctx.client,
    clientVersion: ctx.clientVersion,
    mcpVersion: getMcpVersion(),
    tier,
    tool: toolName,
  });
}
