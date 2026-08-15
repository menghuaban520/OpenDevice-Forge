import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  outputDir: "../../test-results/desktop",
  fullyParallel: false,
  reporter: "list",
  use: {
    baseURL: "http://127.0.0.1:1420",
    channel: "chrome",
    viewport: { width: 1600, height: 1000 },
    colorScheme: "light",
  },
  webServer: {
    command: "pnpm dev",
    url: "http://127.0.0.1:1420",
    reuseExistingServer: true,
    timeout: 30_000,
  },
});

