import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, ".", "");
  const backendTarget = (env.VITE_WCS_BACKEND_BASE_URL || "http://localhost:8080").replace(/\/+$/, "");

  return {
    plugins: [react()],
    server: {
      proxy: {
        "/internal": {
          target: backendTarget,
          changeOrigin: true,
          secure: true
        }
      }
    },
    test: {
      environment: "jsdom",
      clearMocks: true
    }
  };
});
