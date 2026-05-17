import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright configuration for SceneView Web Demo visual regression tests.
 *
 * Run with:
 *   npx playwright test
 *
 * Update screenshots:
 *   npx playwright test --update-snapshots
 */
export default defineConfig({
  testDir: './tests',
  outputDir: './test-results',

  /* Timeout per test — WebGL/WASM loading can be slow */
  timeout: 60_000,

  /* Expect timeout for assertions */
  expect: {
    timeout: 10_000,
    toHaveScreenshot: {
      /* Allow up to 1% pixel difference for GPU/driver variance */
      maxDiffPixelRatio: 0.01,
      /* Threshold per pixel channel (0-1 scale) */
      threshold: 0.2,
    },
  },

  /* Run tests serially — single WebGL context is heavy */
  fullyParallel: false,
  workers: 1,

  /* Reporter
   *
   * - `html`     — local debugging report
   * - `list`     — console output
   * - `json`     — raw Playwright result, kept for tooling
   * - custom QA  — flat `test-results/web-qa-summary.json` consumed by the
   *                autonomous device-QA orchestrator runner (issue #1566).
   */
  reporter: [
    ['html', { outputFolder: './playwright-report', open: 'never' }],
    ['list'],
    ['json', { outputFile: './test-results/playwright-results.json' }],
    ['./tests/qa-summary-reporter.ts'],
  ],

  /* Shared settings for all projects */
  use: {
    /* Base URL — set via env or default to local dev server */
    baseURL: process.env.WEB_DEMO_URL || 'http://localhost:8080',

    /* Capture screenshot on failure */
    screenshot: 'only-on-failure',

    /* Capture trace on first retry */
    trace: 'on-first-retry',

    /* Screen-record every test — parity with the Android / iOS device-QA legs
     * so a 3D regression can be reviewed frame-by-frame. In headless Chromium
     * the video is software-rendered (the real "did it render" assertion is
     * helpers.ts:sampleCanvas, a compositor screenshot); the recording is for
     * human review. Videos land under outputDir (./test-results, gitignored). */
    video: 'on',

    /* Viewport size for consistent screenshots */
    viewport: { width: 1280, height: 720 },
  },

  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        /* Enable WebGL for Filament.js rendering */
        launchOptions: {
          args: [
            '--enable-webgl',
            '--use-gl=angle',
            '--enable-features=Vulkan',
            '--ignore-gpu-blocklist',
            // Chrome removed the automatic SwiftShader fallback for WebGL. On a
            // GPU-less CI runner ANGLE has no hardware path and nothing to fall
            // back to, so WebGL context creation fails outright — the demo
            // never gets a context and the suite would go green-on-nothing.
            // This flag re-enables the software rasteriser so the Filament.js
            // viewer still gets a context on headless CI.
            '--enable-unsafe-swiftshader',
          ],
        },
      },
    },
  ],

  /* Dev server — start the web demo if not already running */
  webServer: process.env.WEB_DEMO_URL ? undefined : {
    command: 'npx http-server src/jsMain/resources -p 8080 -s',
    port: 8080,
    timeout: 30_000,
    reuseExistingServer: true,
  },
});
