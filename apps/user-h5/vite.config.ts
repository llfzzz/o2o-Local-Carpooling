import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';
import { defineConfig, loadEnv } from 'vite';

// Free Joy (FJ) design system lives at repo-root packages/fj-ui as plain source,
// consumed via the @fj alias. fs.allow is widened to the repo root so Vite can
// serve those files during dev.
const fjUi = fileURLToPath(new URL('../../packages/fj-ui', import.meta.url));
const repoRoot = fileURLToPath(new URL('../../', import.meta.url));
const appRoot = fileURLToPath(new URL('.', import.meta.url));

export default defineConfig(({ mode, command }) => {
  // Env files are read from THIS app's directory, not the repo root — a VITE_* value placed in
  // the root .env is silently ignored. Also accept a plain process env var so CI and server
  // builds can do `VITE_AMAP_JS_KEY=... pnpm build` without writing a file.
  const env = loadEnv(mode, appRoot, '');
  const amapJsKey = env.VITE_AMAP_JS_KEY || process.env.VITE_AMAP_JS_KEY || '';

  // A production build with no map key yields a bundle whose map can only ever say
  // "not configured". That is a deploy-time mistake worth shouting about, because the symptom
  // (no map) shows up far from the cause (an env var in the wrong file).
  if (command === 'build' && !amapJsKey) {
    console.warn(
      '\n\x1b[33m[user-h5] VITE_AMAP_JS_KEY is not set — the built app will render "地图未配置".\x1b[0m\n' +
      '          Set it in apps/user-h5/.env.local, or build with:\n' +
      '            VITE_AMAP_JS_KEY=<key> pnpm -C apps/user-h5 build\n' +
      '          The repo-root .env is NOT read by Vite.\n'
    );
  }

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
    server: {
      host: '127.0.0.1',
      // Default to 5173 for local dev / Playwright / gateway CORS; honor an
      // assigned PORT (e.g. Claude Code preview auto-port) when present.
      port: process.env.PORT ? Number(process.env.PORT) : 5173,
      // Proxy /api to the Gateway so the H5 calls the backend same-origin. This
      // avoids the gateway's per-env CORS whitelist (5173/5174) breaking when the
      // dev server runs on any other port (e.g. Claude Code preview auto-port).
      // Set VITE_API_BASE_URL='' (see .env.local) so the app uses relative URLs.
      proxy: {
        '/api': {
          target: process.env.VITE_PROXY_TARGET ?? 'http://127.0.0.1:8080',
          changeOrigin: true,
          // The gateway enforces a CORS origin whitelist (5173/5174) and returns
          // 403 "Invalid CORS request" for any other Origin. On proxied requests
          // strip the browser Origin so the gateway sees a same-origin/non-browser
          // request (like curl) regardless of which port the dev server runs on.
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
