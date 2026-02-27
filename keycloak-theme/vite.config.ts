import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { keycloakify } from "keycloakify/vite-plugin";
import path from "path";
import tailwindcss from "@tailwindcss/vite";
import Unfonts from "unplugin-fonts/vite";

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    react(),
    keycloakify({
      accountThemeImplementation: "none"
    }),
    tailwindcss(),
    Unfonts({
      google: {
        families: ['Geist']
      }
    })
  ],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src")
    }
  }
});
