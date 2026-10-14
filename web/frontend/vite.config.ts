/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

// Built into the jar's static/ folder (the app serves it); `npm run dev` proxies the API to the app on 8080.
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg', 'apple-touch-icon.png'],
      manifest: {
        name: 'Gig sync hub',
        short_name: 'Gigs',
        description: 'The band’s gigs, everywhere they should be.',
        lang: 'en',
        start_url: '/',
        scope: '/',
        display: 'standalone',
        background_color: '#F3F4F7',
        theme_color: '#3B4FD8',
        icons: [
          { src: 'icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: 'icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        // the API is never served from the cache: the page keeps its last data itself
        // /gigs/ is a BAND's public list served by the server, not one of the page's own
        // addresses: without it here, anyone with the worker registered got the cached
        // shell instead and was bounced to the start page
        navigateFallbackDenylist: [/^\/api\//, /^\/actuator\//, /^\/oauth2\//, /^\/login\//, /^\/auth\//, /^\/gigs\//],
        globPatterns: ['**/*.{js,css,html,svg,png,woff2}'],
      },
    }),
  ],
  build: {
    outDir: 'target/classes/static',
    emptyOutDir: true,
    chunkSizeWarningLimit: 1500,
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: false },
      // signing in (Rauthy at https://localhost through the stack's proxy; the client knows :5173)
      '/oauth2': { target: 'http://localhost:8080', changeOrigin: false },
      '/login/oauth2': { target: 'http://localhost:8080', changeOrigin: false },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['src/test/setup.ts'],
    css: false,
  },
});
