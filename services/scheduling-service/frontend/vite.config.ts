import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Built into the Spring Boot jar (classpath:/static/app/) and served under /app.
export default defineConfig({
  base: '/app/',
  plugins: [react()],
  build: {
    outDir: '../target/classes/static/app',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8090', '/internal': 'http://localhost:8090' },
  },
  test: { environment: 'node' },
});
