import axios, { type AxiosError, type AxiosInstance, type AxiosRequestConfig } from "axios";
import { config, getAuthMode } from "./config";
import { authService } from "./rapidRatingsAuthService";
import { recordError, recordSuccess, type HostKey } from "./apiActivity";
import { handleMockRequest } from "./mock/mockServer";
import type { ApiError } from "@/types/api";

const MAX_RETRIES = 2;
const RETRYABLE = new Set([429, 502, 503, 504]);

function baseUrl(host: HostKey): string {
  if (getAuthMode() === "proxy") return host === "data" ? "/rr-proxy/data" : "/rr-proxy/outreach";
  return host === "data" ? config.dataApiUrl : config.outreachApiUrl;
}

function createClient(host: HostKey): AxiosInstance {
  const client = axios.create({ timeout: config.requestTimeoutMs, headers: { Accept: "application/json" } });

  client.interceptors.request.use((req) => {
    req.baseURL = baseUrl(host);
    // Auth headers come only from the auth service (direct mode). Proxy mode authenticates server-side.
    const auth = authService.getAuthorizationHeader();
    if (auth) req.headers.set("Authorization", auth);
    return req;
  });

  client.interceptors.response.use(
    (res) => {
      recordSuccess(host);
      return res;
    },
    async (error: AxiosError) => {
      const req = error.config as (AxiosRequestConfig & { __retry?: number }) | undefined;
      const status = error.response?.status;
      // Bounded retry for rate limits / transient upstream errors, GET only (never re-POST)
      if (req && status && RETRYABLE.has(status) && (req.method ?? "get").toLowerCase() === "get" && (req.__retry ?? 0) < MAX_RETRIES) {
        req.__retry = (req.__retry ?? 0) + 1;
        const retryAfter = Number(error.response?.headers?.["retry-after"]);
        const waitSeconds = Number.isFinite(retryAfter) && retryAfter > 0 ? Math.min(retryAfter, 10) : 2 ** req.__retry;
        await new Promise((r) => setTimeout(r, waitSeconds * 1000));
        return client.request(req);
      }
      const normalised = normaliseError(error, host);
      recordError(host, normalised.message);
      return Promise.reject(normalised);
    },
  );
  return client;
}

const clients: Record<HostKey, AxiosInstance> = { data: createClient("data"), outreach: createClient("outreach") };

export interface RequestOptions {
  method?: "GET" | "POST";
  params?: Record<string, string | number | undefined>;
  data?: unknown;
}

/** Single entry point for all provider calls. Routes to the synthetic mock server in mock mode. */
export async function apiRequest<T>(host: HostKey, path: string, opts: RequestOptions = {}): Promise<T> {
  const params = Object.fromEntries(Object.entries(opts.params ?? {}).filter(([, v]) => v !== undefined && v !== ""));
  if (getAuthMode() === "mock") return handleMockRequest<T>(host, opts.method ?? "GET", path, params, opts.data);
  const res = await clients[host].request<T>({ url: path, method: opts.method ?? "GET", params, data: opts.data });
  return res.data;
}

/** Converts any failure into a secret-free ApiError. Never includes headers, tokens or full bodies. */
export function normaliseError(error: unknown, host?: HostKey): ApiError {
  if (isApiError(error)) return error;
  if (!axios.isAxiosError(error)) return { kind: "unknown", message: error instanceof Error ? error.message : "Unexpected error." };

  if (error.code === "ECONNABORTED" || error.code === "ETIMEDOUT") {
    return { kind: "timeout", message: `The request timed out after ${config.requestTimeoutMs / 1000}s.` };
  }
  if (!error.response) {
    const direct = getAuthMode() === "direct";
    return direct
      ? {
          kind: "cors",
          message: "The browser blocked the request or the network failed.",
          hint:
            host === "data"
              ? "The RapidRatings data API (rridata) does not allow direct browser calls from this origin (no Access-Control-Allow-Origin). Use proxy mode in development or a backend in production."
              : "Check the network and that this origin is allowed by the provider.",
        }
      : { kind: "network", message: "Network error: the request did not reach the server." };
  }

  const status = error.response.status;
  const message = providerMessage(error.response.data) ?? `HTTP ${status}`;
  const retryAfter = Number(error.response.headers?.["retry-after"]);
  const base = { status, message, retryAfterSeconds: Number.isFinite(retryAfter) ? retryAfter : undefined };

  if (status === 400) return { ...base, kind: "bad-request" };
  if (status === 401) return { ...base, kind: "unauthorized", hint: tokenHint() };
  if (status === 403) return { ...base, kind: "forbidden", hint: tokenHint() };
  if (status === 404) return { ...base, kind: "not-found" };
  if (status === 405) return { ...base, kind: "bad-request" };
  if (status === 429) return { ...base, kind: "rate-limited", hint: "Rate limit reached. Wait before retrying." };
  if (status >= 500) return { ...base, kind: "server", hint: status === 500 ? "The provider returns 500 for pages past the end of a list; otherwise retry later." : undefined };
  return { ...base, kind: "unknown" };
}

function tokenHint() {
  const mode = getAuthMode();
  if (mode === "direct") return "The pasted access token is missing, invalid or expired (tokens last 30 minutes, no refresh token). Paste a new one in Settings.";
  if (mode === "proxy") return "The dev proxy could not authenticate. Check RR_CLIENT_ID / RR_CLIENT_SECRET in .env and restart `npm run dev`.";
  return undefined;
}

/** rridata: {"<Scope>": {"message": ...}} | outreach: RFC 7807 {"detail": ...} | dev proxy: {"proxy": {...}} */
function providerMessage(data: unknown): string | undefined {
  if (!data || typeof data !== "object") return undefined;
  const d = data as Record<string, unknown>;
  if (d.detail) {
    const detail = Array.isArray(d.detail) ? d.detail.join("; ") : String(d.detail);
    const errors = Array.isArray(d.errors) ? ` (${(d.errors as unknown[]).join("; ")})` : "";
    return truncate(detail + errors);
  }
  const first = Object.values(d)[0] as { message?: unknown } | undefined;
  if (first && typeof first === "object" && typeof first.message === "string") return truncate(first.message);
  return undefined;
}

const truncate = (s: string) => (s.length > 300 ? `${s.slice(0, 300)}…` : s);

export function isApiError(e: unknown): e is ApiError {
  return !!e && typeof e === "object" && "kind" in e && "message" in e && !(e instanceof Error);
}
