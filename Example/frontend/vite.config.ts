import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { type Connect, type Plugin, loadEnv } from 'vite';
import { defineConfig } from 'vitest/config';

import { RAPIDRATINGS_PREFIX, createRapidRatingsProxy } from './proxy/rapidRatings';

/**
 * Serves `/rapidratings/*` from the dev and preview servers. The token handling and path
 * rules are in proxy/rapidRatings.ts (shared with the Netlify Edge Function); the client
 * secret and the access token stay in this Node process.
 */
function rapidRatingsProxy(env: Record<string, string>): Plugin {
  const handle = createRapidRatingsProxy({
    baseUrl: env.RAPIDRATINGS_API_BASE_URL,
    clientId: env.RAPIDRATINGS_CLIENT_ID,
    clientSecret: env.RAPIDRATINGS_CLIENT_SECRET,
  });
  const middleware: Connect.NextHandleFunction = (req, res, next) => {
    if (!req.url?.startsWith(`${RAPIDRATINGS_PREFIX}/`)) return next();
    handle(new Request(new URL(req.url, 'http://localhost'), { method: req.method }))
      .then(async (response) => {
        res.statusCode = response.status;
        response.headers.forEach((value, name) => res.setHeader(name, value));
        res.end(new Uint8Array(await response.arrayBuffer()));
      })
      .catch(next);
  };
  return {
    name: 'rapidratings-proxy',
    configureServer: (server) => void server.middlewares.use(middleware),
    configurePreviewServer: (server) => void server.middlewares.use(middleware),
  };
}

export default defineConfig(({ mode }) => {
  // Load all env vars (prefix '') so the non-VITE_ token is readable Node-side only.
  const env = loadEnv(mode, process.cwd(), '');
  // Same variable the Netlify Edge Function reads; falls back to the hosted server.
  const enterpriseHost = env.ENTERPRISE_API_HOST?.trim() || 'https://enterprise.ztwo.app';

  return {
    plugins: [react(), tailwindcss(), rapidRatingsProxy(env)],
    server: {
      port: 3000,
      proxy: {
        // Every view calls the Enterprise API through this same-origin path. The proxy injects
        // the Bearer token server-side so it never enters the browser bundle.
        '/enterprise': {
          target: enterpriseHost,
          changeOrigin: true,
          secure: true,
          rewrite: (path) => path.replace(/^\/enterprise/, '/api/v1/platform'),
          configure: (proxy) => {
            proxy.on('proxyReq', (proxyReq) => {
              const token = env.ENTERPRISE_API_TOKEN;
              if (token) {
                proxyReq.setHeader('Authorization', `Bearer ${token}`);
              }
            });
          },
        },
      },
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/test/setup.ts'],
      // Page tests render large tables; the first test per file also pays cold module loading.
      testTimeout: 15_000,
    },
  };
});
