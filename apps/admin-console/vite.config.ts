import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';

// Free Joy (FJ) design system lives at repo-root packages/fj-ui as plain source,
// consumed via the @fj alias. fs.allow is widened to the repo root so Vite can
// serve those files during dev.
const fjUi = fileURLToPath(new URL('../../packages/fj-ui', import.meta.url));
const repoRoot = fileURLToPath(new URL('../../', import.meta.url));

export default defineConfig({
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
    port: 5174,
    fs: {
      allow: [repoRoot]
    }
  }
});
