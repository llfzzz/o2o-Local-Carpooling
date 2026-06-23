import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react()],
  build: {
    chunkSizeWarningLimit: 1200
  },
  server: {
    host: '127.0.0.1',
    port: 5174
  }
});
