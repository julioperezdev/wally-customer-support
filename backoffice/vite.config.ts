import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/internal": "http://localhost:8080"
    }
  },
  test: {
    environment: "jsdom",
    clearMocks: true
  }
});
