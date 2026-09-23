import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      "/api": "http://localhost:8080",
      "/goals": "http://localhost:8080",
      "/roadmaps": "http://localhost:8080",
      "/milestones": "http://localhost:8080",
      "/commitments": "http://localhost:8080",
      "/categories": "http://localhost:8080",
      "/execution": "http://localhost:8080",
      "/schedule": "http://localhost:8080",
      "/blocks": "http://localhost:8080",
    },
  },
});
