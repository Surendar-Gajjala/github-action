import { defineConfig, loadEnv, type Plugin } from "vite";
import react from "@vitejs/plugin-react";
import type { IncomingMessage, ServerResponse } from "node:http";
import { fileURLToPath } from "node:url";

/**
 * Dev-server-only RapidRatings proxy (auth mode "proxy").
 *
 * Why: the rridata host does not send Access-Control-Allow-Origin for localhost, so the browser
 * cannot call it directly, and the OAuth client secret must never be shipped to the browser.
 * This middleware runs inside `vite dev` on the developer's machine: it performs the documented
 * client-credentials token request using the non-VITE_ variables RR_CLIENT_ID / RR_CLIENT_SECRET
 * (never exposed to the bundle) and forwards requests with a Bearer token.
 *
 * It is NOT part of the production build. Production needs a backend / API gateway.
 */
function rapidRatingsDevProxy(env: Record<string, string>): Plugin {
  const tokenUrl = env.RR_TOKEN_URL;
  const dataBase = env.VITE_RAPIDRATINGS_DATA_API_URL || env.RR_API_BASE_URL;
  const outreachBase = env.VITE_RAPIDRATINGS_OUTREACH_API_URL || env.RR_OUTREACH_BASE_URL;
  const clientId = env.RR_CLIENT_ID;
  const clientSecret = env.RR_CLIENT_SECRET;

  let token: string | null = null;
  let expiresAt = 0;
  let scope: string | undefined;

  async function getToken(force = false): Promise<string> {
    if (!force && token && Date.now() < expiresAt - 60_000) return token;
    if (!tokenUrl || !clientId || !clientSecret) {
      throw Object.assign(new Error("RR_TOKEN_URL, RR_CLIENT_ID and RR_CLIENT_SECRET must be set in .env for proxy mode."), { status: 500 });
    }
    const basic = Buffer.from(`${clientId}:${clientSecret}`).toString("base64");
    const res = await fetch(tokenUrl, {
      method: "POST",
      headers: { Authorization: `Basic ${basic}`, "Content-Type": "application/x-www-form-urlencoded" },
      body: "grant_type=client_credentials",
      signal: AbortSignal.timeout(30_000),
    });
    const body = (await res.json().catch(() => ({}))) as { access_token?: string; expires_in?: number; scope?: string };
    if (!res.ok || !body.access_token) {
      token = null;
      throw Object.assign(new Error(`Token request failed with HTTP ${res.status}.`), { status: res.status });
    }
    token = body.access_token;
    expiresAt = Date.now() + (body.expires_in ?? 0) * 1000;
    scope = body.scope;
    return token;
  }

  const readBody = (req: IncomingMessage) =>
    new Promise<string>((resolve, reject) => {
      let data = "";
      req.on("data", (c) => (data += c));
      req.on("end", () => resolve(data));
      req.on("error", reject);
    });

  const sendJson = (res: ServerResponse, status: number, body: unknown) => {
    res.statusCode = status;
    res.setHeader("Content-Type", "application/json");
    res.end(JSON.stringify(body));
  };

  async function forward(req: IncomingMessage, res: ServerResponse, base: string, rest: string, allowed: string[]) {
    const method = req.method ?? "GET";
    if (!allowed.includes(method)) return sendJson(res, 405, { proxy: { message: `Method ${method} is not enabled in the dev proxy.` } });
    const body = method === "GET" ? undefined : await readBody(req);
    const target = `${base.replace(/\/$/, "")}/${rest}`;

    for (let attempt = 0; attempt < 2; attempt++) {
      const upstream = await fetch(target, {
        method,
        headers: {
          Authorization: `Bearer ${await getToken(attempt > 0)}`,
          Accept: "application/json",
          ...(body ? { "Content-Type": "application/json" } : {}),
        },
        body,
        signal: AbortSignal.timeout(30_000),
      });
      // rridata answers an expired token with 403, outreach with 401: renew once
      if ((upstream.status === 401 || upstream.status === 403) && attempt === 0) continue;
      res.statusCode = upstream.status;
      for (const h of ["content-type", "retry-after", "x-ratelimit-limit", "x-ratelimit-remaining", "x-ratelimit-reset"]) {
        const v = upstream.headers.get(h);
        if (v) res.setHeader(h, v);
      }
      res.end(Buffer.from(await upstream.arrayBuffer()));
      return;
    }
  }

  return {
    name: "rapidratings-dev-proxy",
    apply: "serve",
    configureServer(server) {
      server.middlewares.use("/rr-proxy", async (req, res) => {
        const url = req.url ?? "/";
        try {
          if (url.startsWith("/status")) {
            await getToken(url.includes("force=1"));
            // Never return the token itself
            return sendJson(res, 200, { ok: true, expiresInSeconds: Math.round((expiresAt - Date.now()) / 1000), scope });
          }
          if (url.startsWith("/data/")) return await forward(req, res, dataBase, url.slice("/data/".length), ["GET"]);
          if (url.startsWith("/outreach/")) return await forward(req, res, outreachBase, url.slice("/outreach/".length), ["GET", "POST"]);
          sendJson(res, 404, { proxy: { message: "Unknown proxy route." } });
        } catch (e) {
          const err = e as Error & { status?: number };
          const timeout = err.name === "TimeoutError";
          sendJson(res, timeout ? 504 : 502, { proxy: { message: timeout ? "Upstream request timed out." : err.message, upstreamStatus: err.status } });
        }
      });
    },
  };
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  return {
    plugins: [react(), rapidRatingsDevProxy(env)],
    resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
    server: {
      port: 5173,
      strictPort: true,
      // Only app sources need watching; files in docs/ or imp/ can be locked by other programs (EBUSY crashes the watcher)
      watch: { ignored: ["**/docs/**", "**/imp/**", "**/dist/**"] },
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks: {
            react: ["react", "react-dom", "react-router-dom"],
            data: ["@tanstack/react-query", "axios", "zod", "react-hook-form", "@hookform/resolvers"],
          },
        },
      },
    },
  };
});
