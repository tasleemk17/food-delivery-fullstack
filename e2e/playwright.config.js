import { defineConfig } from "@playwright/test";

// The tests drive a real browser against the stack that `docker compose up -d --build` starts.
// Nothing is started or mocked here.
//
// Browser: on Windows the Microsoft Edge that is already installed (nothing to download). Elsewhere the
// Chromium that `npx playwright install chromium` downloads. Set E2E_BROWSER_CHANNEL=chrome (or msedge)
// to choose a browser yourself.
const channel = process.env.E2E_BROWSER_CHANNEL || (process.platform === "win32" ? "msedge" : undefined);
export const URLS = {
  app: process.env.E2E_APP_URL || "http://localhost:5173",
  api: process.env.E2E_API_URL || "http://localhost:8090",
  mail: process.env.E2E_MAIL_URL || "http://localhost:8025",
};

export default defineConfig({
  testDir: "./tests",
  // The tests share one user in order (sign up, order, chat), so they run one after another
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: URLS.app,
    channel,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
  },
});
