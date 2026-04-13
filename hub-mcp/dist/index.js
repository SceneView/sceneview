#!/usr/bin/env node
/**
 * stdio entrypoint for the `hub-mcp` npm package.
 *
 * Lite mode: 13 free tools run locally (stub responses), 39 Pro tools
 * return a "set HUB_MCP_API_KEY" upsell message.
 *
 * Hosted mode (HUB_MCP_API_KEY set): all 52 tools are forwarded to the hub
 * gateway via JSON-RPC POST. The gateway handles auth, metering, rate
 * limiting, and real dispatch.
 *
 * Usage:
 *   npx hub-mcp@beta           # lite — 13 free tools
 *   HUB_MCP_API_KEY=sk-... npx hub-mcp@beta  # hosted — 52 tools
 */
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { CallToolRequestSchema, ListToolsRequestSchema, } from "@modelcontextprotocol/sdk/types.js";
import { TOOL_DEFINITIONS, isFreeTool, isProTool, } from "./tools.js";
import { dispatchProxyToolCall, isProxyConfigured, DEFAULT_PRICING_URL, } from "./proxy.js";
import { recordInit, recordToolCall, flushTelemetry } from "./telemetry.js";
// ─── Constants ──────────────────────────────────────────────────────────────
const PACKAGE_VERSION = "0.3.0";
const FREE_TOOL_COUNT = TOOL_DEFINITIONS.filter((t) => isFreeTool(t.name)).length;
const TOTAL_TOOL_COUNT = TOOL_DEFINITIONS.length;
// ─── Startup banner (stderr only — stdout is JSON-RPC) ─────────────────────
function logStartupBanner() {
    if (process.env.HUB_MCP_QUIET === "1")
        return;
    const proxied = isProxyConfigured();
    const mode = proxied
        ? `HOSTED (${TOTAL_TOOL_COUNT} tools -> gateway)`
        : `LITE (${FREE_TOOL_COUNT} free tools)`;
    const lines = [
        `[hub-mcp] v${PACKAGE_VERSION} — ${mode}`,
        proxied
            ? `[hub-mcp] All tool calls will be forwarded to the hosted gateway.`
            : `[hub-mcp] Set HUB_MCP_API_KEY to unlock ${TOTAL_TOOL_COUNT - FREE_TOOL_COUNT}+ Pro tools — ${DEFAULT_PRICING_URL}`,
    ];
    for (const line of lines)
        process.stderr.write(`${line}\n`);
}
logStartupBanner();
// ─── MCP Server ─────────────────────────────────────────────────────────────
const server = new Server({ name: "hub-mcp", version: PACKAGE_VERSION }, { capabilities: { tools: {} } });
// ─── Tools: list ────────────────────────────────────────────────────────────
server.setRequestHandler(ListToolsRequestSchema, async () => {
    const unlocked = isProxyConfigured();
    const tools = TOOL_DEFINITIONS.map((tool) => {
        if (unlocked || isFreeTool(tool.name))
            return tool;
        return { ...tool, description: `[PRO] ${tool.description}` };
    });
    return { tools };
});
// ─── Tools: call ────────────────────────────────────────────────────────────
server.setRequestHandler(CallToolRequestSchema, async (request) => {
    const toolName = request.params.name;
    const args = request.params.arguments;
    // ── Hosted mode: forward everything to the gateway ──────────────────────
    if (isProxyConfigured()) {
        recordToolCall(toolName, "pro");
        const result = await dispatchProxyToolCall(toolName, args);
        return result;
    }
    // ── Lite mode: Pro tools get an upsell stub ─────────────────────────────
    if (isProTool(toolName)) {
        recordToolCall(toolName, "pro");
        const result = await dispatchProxyToolCall(toolName, args);
        return result;
    }
    // ── Lite mode: free tools execute locally (stub) ────────────────────────
    recordToolCall(toolName, "free");
    return dispatchFreeToolStub(toolName, args);
});
// ─── Free tool stub dispatcher ──────────────────────────────────────────────
//
// In v1 the free tools return a placeholder response. Real implementations
// will be vendored from the upstream packages in follow-up sessions, same
// as the hub-gateway wiring pattern.
function dispatchFreeToolStub(toolName, _args) {
    // Find which library owns this tool based on the name prefix
    const library = getLibraryLabel(toolName);
    return {
        content: [
            {
                type: "text",
                text: `## ${toolName}\n\n` +
                    `This is a free-tier tool from **${library}** running in Hub MCP lite mode.\n\n` +
                    `The local stub implementation is not yet available in this beta release. ` +
                    `Set \`HUB_MCP_API_KEY\` to get full responses from the hosted gateway, or ` +
                    `install the standalone \`${library}\` package from npm for offline use.\n\n` +
                    `Get an API key at ${DEFAULT_PRICING_URL}`,
            },
        ],
    };
}
/** Maps a tool name to its upstream library label. */
function getLibraryLabel(toolName) {
    const PREFIXES = {
        "architecture__": "architecture-mcp",
        "realestate__": "realestate-mcp",
        "french_admin__": "french-admin-mcp",
        "ecommerce3d__": "ecommerce-3d-mcp",
        "legal_docs__": "legal-docs-mcp",
        "finance__": "finance-mcp",
        "education__": "education-mcp",
        "social_media__": "social-media-mcp",
        "health_fitness__": "health-fitness-mcp",
    };
    for (const [prefix, label] of Object.entries(PREFIXES)) {
        if (toolName.startsWith(prefix))
            return label;
    }
    // Unprefixed tools from automotive and healthcare
    if (toolName.startsWith("get_car_") ||
        toolName.startsWith("list_car_") ||
        toolName.startsWith("validate_automotive") ||
        toolName === "get_hud_overlay" ||
        toolName === "get_dashboard_3d" ||
        toolName === "get_ar_showroom" ||
        toolName === "get_parts_catalog" ||
        toolName === "get_ev_charging_station_viewer" ||
        toolName === "get_car_paint_shader") {
        return "automotive-3d-mcp";
    }
    if (toolName.startsWith("get_anatomy_") ||
        toolName.startsWith("get_molecule_") ||
        toolName.startsWith("get_medical_") ||
        toolName.startsWith("get_surgical_") ||
        toolName.startsWith("get_dental_") ||
        toolName.startsWith("list_medical_") ||
        toolName.startsWith("validate_medical")) {
        return "healthcare-3d-mcp";
    }
    return "hub-mcp";
}
// ─── Connect transport ─────────────────────────────────────────────────────
const transport = new StdioServerTransport();
await server.connect(transport);
recordInit();
process.on("exit", () => flushTelemetry());
process.on("SIGINT", () => { flushTelemetry(); process.exit(0); });
process.on("SIGTERM", () => { flushTelemetry(); process.exit(0); });
