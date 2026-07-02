import { defineConfig, devices } from '@playwright/test';

// E2E smoke for the H5. The `webServer` starts the Vite dev server (or reuses a running one).
// The login-screen spec needs no backend; backend-dependent specs need the full demo stack
// (see docs/demo-mode.md) — those are driven headlessly through the Gateway once it is up.
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  fullyParallel: true,
  use: {
    baseURL: 'http://127.0.0.1:5173',
    headless: true,
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'pnpm dev --host 127.0.0.1 --port 5173',
    url: 'http://127.0.0.1:5173',
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
