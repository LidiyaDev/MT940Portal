import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The preview runs behind https://<port>-<sandbox>.e2b.app, so the dev server
// must bind to all interfaces, accept that host, and speak WSS on 443 for HMR.
export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: Number(process.env.PORT ?? 5173),
    strictPort: false,
    allowedHosts: true as unknown as string[],
    hmr: { clientPort: 443, protocol: 'wss' },
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  preview: { host: '0.0.0.0', port: Number(process.env.PORT ?? 4173) },
});
