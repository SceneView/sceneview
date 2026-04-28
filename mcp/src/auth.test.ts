import { describe, it, expect, vi, beforeEach } from "vitest";
import type { SubscriptionStatus } from "./billing.js";

// ─── Mock billing.ts ────────────────────────────────────────────────────────
//
// We mock getConfiguredApiKey and validateApiKey so tests don't touch Stripe
// or the real environment.

vi.mock("./billing.js", () => ({
  getConfiguredApiKey: vi.fn<() => string | undefined>(() => undefined),
  validateApiKey: vi.fn<(key: string) => Promise<SubscriptionStatus>>(
    async () => ({ valid: false, tier: "free" as const, error: "mocked" }),
  ),
}));

// Import AFTER mocking so the mock is in effect
import {
  checkToolAccess,
  filterToolsForTier,
  createAccessDeniedResponse,
} from "./auth.js";
import { getConfiguredApiKey, validateApiKey } from "./billing.js";
import { PRO_UPGRADE_MESSAGE } from "./tiers.js";

// ─── Helpers ────────────────────────────────────────────────────────────────

const mockedGetApiKey = vi.mocked(getConfiguredApiKey);
const mockedValidate = vi.mocked(validateApiKey);

const FREE_TOOL = "list_samples";
const PRO_TOOL = "render_3d_preview";

function mockFreeUser() {
  mockedGetApiKey.mockReturnValue(undefined);
}

function mockProUser() {
  mockedGetApiKey.mockReturnValue("sub_valid_key");
  mockedValidate.mockResolvedValue({ valid: true, tier: "pro" });
}

function mockInvalidKey() {
  mockedGetApiKey.mockReturnValue("sub_expired");
  mockedValidate.mockResolvedValue({
    valid: false,
    tier: "free",
    error: "Subscription expired",
  });
}

// ─── Setup ──────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.clearAllMocks();
  mockFreeUser(); // default: no API key
});

// ─── checkToolAccess ────────────────────────────────────────────────────────

describe("checkToolAccess", () => {
  it("allows free tools without an API key", async () => {
    mockFreeUser();
    const result = await checkToolAccess(FREE_TOOL);
    expect(result.allowed).toBe(true);
    expect(result.tier).toBe("free");
  });

  it("allows free tools even with a valid API key", async () => {
    mockProUser();
    const result = await checkToolAccess(FREE_TOOL);
    expect(result.allowed).toBe(true);
  });

  it("denies pro tools without an API key", async () => {
    mockFreeUser();
    const result = await checkToolAccess(PRO_TOOL);
    expect(result.allowed).toBe(false);
    expect(result.tier).toBe("free");
    expect(result.message).toBeDefined();
  });

  it("returns PRO_UPGRADE_MESSAGE when no API key is set", async () => {
    mockFreeUser();
    const result = await checkToolAccess(PRO_TOOL);
    expect(result.message).toBe(PRO_UPGRADE_MESSAGE);
  });

  it("allows pro tools with a valid API key", async () => {
    mockProUser();
    const result = await checkToolAccess(PRO_TOOL);
    expect(result.allowed).toBe(true);
    expect(result.tier).toBe("pro");
  });

  it("denies pro tools when API key is invalid", async () => {
    mockInvalidKey();
    const result = await checkToolAccess(PRO_TOOL);
    expect(result.allowed).toBe(false);
    expect(result.tier).toBe("free");
    expect(result.message).toContain("Subscription expired");
  });

  it("does not call validateApiKey for free tools", async () => {
    mockFreeUser();
    await checkToolAccess(FREE_TOOL);
    expect(mockedValidate).not.toHaveBeenCalled();
  });

  it("calls validateApiKey for pro tools when key is present", async () => {
    mockProUser();
    await checkToolAccess(PRO_TOOL);
    expect(mockedValidate).toHaveBeenCalledWith("sub_valid_key");
  });
});

// ─── createAccessDeniedResponse ─────────────────────────────────────────────

describe("createAccessDeniedResponse", () => {
  it("returns an object with isError: true", () => {
    const resp = createAccessDeniedResponse(PRO_TOOL, "Upgrade required");
    expect(resp.isError).toBe(true);
  });

  it("includes the message in content[0].text", () => {
    const msg = "Your subscription has expired.";
    const resp = createAccessDeniedResponse(PRO_TOOL, msg);
    expect(resp.content).toHaveLength(1);
    expect(resp.content[0].type).toBe("text");
    expect(resp.content[0].text).toBe(msg);
  });

  it("has the correct MCP response shape", () => {
    const resp = createAccessDeniedResponse(PRO_TOOL, "denied");
    expect(resp).toEqual({
      content: [{ type: "text", text: "denied" }],
      isError: true,
    });
  });
});

// ─── filterToolsForTier ─────────────────────────────────────────────────────

describe("filterToolsForTier", () => {
  const sampleTools = [
    { name: "list_samples", description: "List code samples" },
    { name: "validate_code", description: "Validate SceneView code" },
    { name: "render_3d_preview", description: "Render a 3D preview" },
    { name: "create_3d_artifact", description: "Create a 3D artifact" },
  ];

  it("marks pro tools with [PRO] prefix for free users", async () => {
    mockFreeUser();
    const result = await filterToolsForTier(sampleTools);

    // Free tools should be unchanged
    const listSamples = result.find((t) => t.name === "list_samples");
    expect(listSamples?.description).toBe("List code samples");

    const validateCode = result.find((t) => t.name === "validate_code");
    expect(validateCode?.description).toBe("Validate SceneView code");

    // Pro tools should have [PRO] prefix
    const preview = result.find((t) => t.name === "render_3d_preview");
    expect(preview?.description).toBe("[PRO] Render a 3D preview");

    const artifact = result.find((t) => t.name === "create_3d_artifact");
    expect(artifact?.description).toBe("[PRO] Create a 3D artifact");
  });

  it("shows all tools normally for pro users", async () => {
    mockProUser();
    const result = await filterToolsForTier(sampleTools);

    // No [PRO] prefixes for pro users
    const preview = result.find((t) => t.name === "render_3d_preview");
    expect(preview?.description).toBe("Render a 3D preview");

    const artifact = result.find((t) => t.name === "create_3d_artifact");
    expect(artifact?.description).toBe("Create a 3D artifact");
  });

  it("preserves all tools in the output (no filtering)", async () => {
    mockFreeUser();
    const result = await filterToolsForTier(sampleTools);
    expect(result).toHaveLength(sampleTools.length);
  });

  it("preserves extra properties on tool objects", async () => {
    mockFreeUser();
    const tools = [
      { name: "render_3d_preview", description: "Preview", inputSchema: { type: "object" } },
    ];
    const result = await filterToolsForTier(tools);
    expect(result[0].inputSchema).toEqual({ type: "object" });
  });

  it("handles tools with no description gracefully", async () => {
    mockFreeUser();
    const tools = [{ name: "render_3d_preview" }];
    const result = await filterToolsForTier(tools);
    expect(result[0].description).toBe("[PRO] ");
  });

  it("does not call validateApiKey when no API key is set", async () => {
    mockFreeUser();
    await filterToolsForTier(sampleTools);
    expect(mockedValidate).not.toHaveBeenCalled();
  });

  it("calls validateApiKey when API key is present", async () => {
    mockProUser();
    await filterToolsForTier(sampleTools);
    expect(mockedValidate).toHaveBeenCalledWith("sub_valid_key");
  });
});
