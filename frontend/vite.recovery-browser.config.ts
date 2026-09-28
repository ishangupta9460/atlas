import { defineConfig } from "vite";
// Dedicated disposable-server configuration for e2e/recovery.browser.mjs.
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
export default defineConfig({plugins:[react(),tailwindcss()],server:{host:"127.0.0.1",port:15173,strictPort:true,proxy:Object.fromEntries(["/api","/users","/goals","/roadmaps","/milestones","/commitments","/categories","/execution","/schedule","/blocks","/recovery","/recurring-intentions"].map(p=>[p,"http://127.0.0.1:18080"]))}});
