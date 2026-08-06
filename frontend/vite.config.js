import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: "autoUpdate",
      includeAssets: ["icon.svg"],
      manifest: {
        name: "Portiq",
        short_name: "Portiq",
        description: "Track your investment portfolio, holdings, and market value",
        start_url: "/",
        display: "standalone",
        background_color: "#f5f7fa",
        theme_color: "#00b386",
        icons: [
          { src: "icon.svg", sizes: "192x192", type: "image/svg+xml", purpose: "any" },
          { src: "icon.svg", sizes: "512x512", type: "image/svg+xml", purpose: "any" },
        ],
      },
      workbox: {
        globPatterns: ["**/*.{js,css,html,svg,png,ico}"],
        navigateFallbackDenylist: [/^\/api\//],
      },
    }),
  ],
  server: {
    port: 5173,
    host: true,
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: "./src/test/setup.js",
    // The PWA plugin generates a service worker at build time; nothing under test needs it, and
    // leaving it in makes every run write files into dist/.
    exclude: ["node_modules/**", "dist/**"],
  },
});
