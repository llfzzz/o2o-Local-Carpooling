import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';
import { defineConfig, loadEnv } from 'vite';

// Free Joy (FJ) design system lives at repo-root packages/fj-ui as plain source,
// consumed via the @fj alias. fs.allow is widened to the repo root so Vite can
// serve those files during dev.
const fjUi = fileURLToPath(new URL('../../packages/fj-ui', import.meta.url));
const repoRoot = fileURLToPath(new URL('../../', import.meta.url));
const appRoot = fileURLToPath(new URL('.', import.meta.url));

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, appRoot, '');

  return {
    base: env.VITE_BASE_PATH || '/',
    plugins: [react()],
    resolve: {
      alias: {
        '@fj': fjUi
      },
      // fj-ui lives outside this app's node_modules; dedupe forces its
      // `import React from "react"` to resolve to this app's single React copy.
      dedupe: ['react', 'react-dom']
    },
    build: {
      chunkSizeWarningLimit: 1200,
      rollupOptions: {
        output: {
          // Split heavy vendors off the app chunk for better caching. react and
          // tanstack are leaves (nothing imports back into them), so grouping the
          // rest (antd is the bulk) into one vendor chunk stays cycle-free.
          manualChunks(id) {
            if (!id.includes('node_modules')) return undefined;
            if (id.includes('/react/') || id.includes('/react-dom/') || id.includes('/scheduler/')) return 'react';
            if (id.includes('/@tanstack/')) return 'tanstack';
            return 'vendor';
          }
        }
      }
    },
    server: {
      host: '127.0.0.1',
      // Default to 5174 for local dev / gateway CORS; honor an assigned PORT
      // (e.g. Claude Code preview auto-port) when present.
      port: process.env.PORT ? Number(process.env.PORT) : 5174,
      // Proxy /api to the Gateway so the console calls the backend same-origin. The
      // gateway enforces a CORS origin whitelist (5173/5174) and returns 403 for any
      // other Origin, so strip the browser Origin on proxied requests (mirrors user-h5).
      proxy: {
        '/api': {
          target: process.env.VITE_PROXY_TARGET ?? 'http://127.0.0.1:8080',
          changeOrigin: true,
          configure: (proxy) => {
            proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'));
          }
        }
      },
      fs: {
        allow: [repoRoot]
      }
    }
  };
});
