import { defineConfig } from 'vite';

export default defineConfig({
  base: './',
  build: {
    target: 'es2022',
    chunkSizeWarningLimit: 2000,
    rollupOptions: {
      // O servidor local do Capacitor não conhece .mjs; o worker do pdf.js sai como .js.
      output: { assetFileNames: (a) => (a.names?.[0] || a.name || '').endsWith('.mjs') ? 'assets/[name]-[hash].js' : 'assets/[name]-[hash][extname]' },
    },
  },
});
