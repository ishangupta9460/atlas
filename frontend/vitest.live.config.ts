import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

// Separate opt-in suite: real HTTP backend, real elapsed overrun grace window.
export default defineConfig({ plugins: [react()], test: {
  environment: "jsdom", setupFiles: ["./src/test-setup.ts"],
  include: ["e2e/**/*.live.tsx"], testTimeout: 480000, hookTimeout: 30000,
  maxWorkers: 1, minWorkers: 1,
} });
