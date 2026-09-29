import { defineConfig, mergeConfig } from "vite";
import base from "./vite.config";

// Isolated disposable backend for Chunk 5's real-browser verification.
export default mergeConfig(base, defineConfig({ server: { host: "127.0.0.1", port: 15175, strictPort: true,
  proxy: Object.fromEntries(Object.keys(base.server?.proxy ?? {}).map(prefix => [prefix, "http://127.0.0.1:18085"])) } }));
