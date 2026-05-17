import { Page, expect } from '@playwright/test';

/**
 * Shared helpers for the SceneView Web Demo Playwright suite.
 *
 * Extracted from `render.spec.ts` so the catalog-coverage suite
 * (`catalog.spec.ts`) and the original visual-regression suite reuse the
 * same canvas-sampling and console-capture logic.
 */

/** Collected console / page diagnostics for a single test. */
export interface PageDiagnostics {
  /** `console.error(...)` messages emitted by the page. */
  consoleErrors: string[];
  /** Uncaught exceptions / unhandled promise rejections (`pageerror`). */
  pageErrors: string[];
}

/**
 * Attach console + pageerror listeners to a page and return a live
 * diagnostics object. Call this before `page.goto(...)`.
 *
 * Network/CDN noise (Sketchfab auth 401s, jsDelivr hiccups) is filtered out
 * — those are environmental, not demo bugs. We only fail on real script
 * errors and unhandled rejections.
 */
export function captureDiagnostics(page: Page): PageDiagnostics {
  const diag: PageDiagnostics = { consoleErrors: [], pageErrors: [] };

  page.on('console', (msg) => {
    if (msg.type() !== 'error') return;
    const text = msg.text();
    if (isIgnorableNoise(text)) return;
    diag.consoleErrors.push(text);
  });

  page.on('pageerror', (err) => {
    const text = err.message || String(err);
    if (isIgnorableNoise(text)) return;
    diag.pageErrors.push(text);
  });

  return diag;
}

/**
 * Third-party failures are not demo regressions: the Sketchfab Search source
 * hits `api.sketchfab.com`, whose download/search endpoints return 401 without
 * auth (the demo handles that path gracefully). Filter only Sketchfab-domain
 * noise so the suite stays deterministic.
 *
 * NOTE: the catalog models are now self-hosted under `models/` (issue #1573),
 * so a model HTTP 403/404 is a REAL demo regression and must NOT be filtered —
 * that is precisely the failure mode #1573 was about. Only genuinely
 * external (Sketchfab) errors are treated as ignorable noise.
 */
function isIgnorableNoise(text: string): boolean {
  const t = text.toLowerCase();
  // Only suppress errors that originate from the Sketchfab third-party API.
  return t.includes('sketchfab');
}

/** Assert no real console errors / unhandled rejections were collected. */
export function expectNoPageErrors(diag: PageDiagnostics, context: string): void {
  expect(
    diag.pageErrors,
    `Unhandled errors during "${context}": ${diag.pageErrors.join(' | ')}`,
  ).toEqual([]);
  expect(
    diag.consoleErrors,
    `Console errors during "${context}": ${diag.consoleErrors.join(' | ')}`,
  ).toEqual([]);
}

/** Wait for the Filament loading overlay to clear (engine ready). */
export async function waitForEngineReady(page: Page): Promise<void> {
  const overlay = page.locator('#loading-overlay');
  await expect(overlay).toHaveClass(/hidden/, { timeout: 45_000 });
}

/**
 * Sample a 10x10 block of WebGL pixels at the centre of the scene canvas and
 * report whether any pixel is non-black.
 *
 * Headless WebGL on a software rasteriser (SwiftShader / ANGLE) can fail to
 * produce a readable framebuffer. `headlessGpuOk` distinguishes "canvas is
 * genuinely blank" from "this headless GPU cannot read pixels at all" so the
 * caller can soft-skip the assertion on unsupported runners instead of
 * reporting a false failure.
 */
export async function sampleCanvas(
  page: Page,
): Promise<{ hasContent: boolean; headlessGpuOk: boolean }> {
  return page.evaluate(() => {
    const canvas = document.getElementById('scene-canvas') as HTMLCanvasElement | null;
    if (!canvas || canvas.width === 0 || canvas.height === 0) {
      return { hasContent: false, headlessGpuOk: false };
    }
    const gl =
      (canvas.getContext('webgl2') as WebGL2RenderingContext | null) ||
      (canvas.getContext('webgl') as WebGLRenderingContext | null);
    if (!gl) {
      return { hasContent: false, headlessGpuOk: false };
    }
    const pixels = new Uint8Array(4 * 100);
    gl.readPixels(
      Math.max(0, Math.floor(canvas.width / 2) - 5),
      Math.max(0, Math.floor(canvas.height / 2) - 5),
      10,
      10,
      gl.RGBA,
      gl.UNSIGNED_BYTE,
      pixels,
    );
    let nonZero = 0;
    for (let i = 0; i < pixels.length; i += 4) {
      if (pixels[i] > 5 || pixels[i + 1] > 5 || pixels[i + 2] > 5) nonZero++;
    }
    return { hasContent: nonZero > 0, headlessGpuOk: true };
  });
}

/**
 * Drive an orbit gesture on the canvas: press, drag across, release.
 * Mirrors what a user does to rotate the camera. Returns nothing — the
 * caller re-samples the canvas afterwards to confirm the scene survived.
 */
export async function dragCanvas(page: Page): Promise<void> {
  const canvas = page.locator('#scene-canvas');
  const box = await canvas.boundingBox();
  if (!box) return;
  const cx = box.x + box.width / 2;
  const cy = box.y + box.height / 2;

  // Mouse orbit drag.
  await page.mouse.move(cx, cy);
  await page.mouse.down();
  await page.mouse.move(cx + 120, cy + 40, { steps: 12 });
  await page.mouse.move(cx + 40, cy - 60, { steps: 12 });
  await page.mouse.up();

  // Touch-style tap (covers the touch listener path without needing a
  // touch-enabled context — a plain click exercises the pointer handlers).
  await page.mouse.click(cx, cy);

  // Scroll-wheel zoom.
  await page.mouse.move(cx, cy);
  await page.mouse.wheel(0, -240);
  await page.mouse.wheel(0, 240);
}

/** Switch to a catalog tab and assert its panel becomes active. */
export async function switchTab(page: Page, tab: string): Promise<void> {
  await page.locator(`.tab-btn[data-tab="${tab}"]`).click();
  await expect(page.locator(`.tab-btn[data-tab="${tab}"]`)).toHaveClass(/active/);
  await expect(page.locator(`#panel-${tab}`)).toHaveClass(/active/);
}
