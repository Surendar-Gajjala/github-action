import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The React dev server (port 5173) forwards /api/* to the Spring Boot backend,
// so the browser talks to one origin and no CORS setup is needed.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': process.env.BACKEND_URL || 'http://localhost:8080',
    },
  },
});
