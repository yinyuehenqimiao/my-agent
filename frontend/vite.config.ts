import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src")
    }
  },
  server: {
    host: "127.0.0.1",
    port: 5173,
    proxy: {
      // AuthController: /api/**
      "/api": {
        target: "http://localhost:18080",
        changeOrigin: true,
        secure: false
      },
      // Knowledge controllers: /knowledge-base/** (no /api prefix)
      "/knowledge-base": {
        target: "http://localhost:18080",
        changeOrigin: true,
        secure: false
      },
      "/rag": {
        target: "http://localhost:18080",
        changeOrigin: true,
        secure: false
      },
      "/ingestion": {
        target: "http://localhost:18080",
        changeOrigin: true,
        secure: false
      }
    }
  }
});
