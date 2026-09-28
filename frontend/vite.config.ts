import path from "node:path";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { "@": path.resolve(__dirname, "./src") },
  },
  build: {
    // Built straight into Spring's static resources so `./mvnw spring-boot:run`
    // serves the UI and the API from one origin on :8080.
    outDir: "../src/main/resources/static",
    emptyOutDir: true,
  },
  server: {
    // In dev the UI runs on :5173 and proxies the API, which keeps the Spring
    // app free of any CORS configuration.
    proxy: {
      "/api": { target: "http://localhost:8080", changeOrigin: true },
    },
  },
});
