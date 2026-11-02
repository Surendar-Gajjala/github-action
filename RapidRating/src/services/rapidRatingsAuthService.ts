import axios from "axios";
import { config, configProblems, getAuthMode } from "./config";
import { createStore } from "@/utils/store";
import type { AuthMode } from "@/types/api";
import { recordSuccess, type HostKey } from "./apiActivity";

/**
 * Authentication per the documented RapidRatings contract:
 *   POST {auth url} with HTTP Basic client credentials, body grant_type=client_credentials
 *   -> { access_token, token_type: "Bearer", expires_in: 1800, scope }. No refresh token is issued.
 *
 * The browser must never hold the client secret, so:
 *  - mock:   no auth, synthetic data
 *  - proxy:  the Vite dev server performs the token request (secret stays on the dev machine) and
 *            renews it on expiry; the browser only asks it for status, never for the token
 *  - direct: an access token obtained out-of-band is pasted at runtime and held in memory only.
 *            There is no refresh token, so it must be replaced after `expires_in`.
 */

export type ConnectionState = "demo" | "checking" | "connected" | "partial" | "disconnected" | "not-configured" | "unknown";

export interface ConnectionStatus {
  mode: AuthMode;
  state: ConnectionState;
  message: string;
  checkedAt?: string;
  tokenExpiresAt?: string;
  hosts?: { data: HostCheck; outreach: HostCheck };
}

export interface HostCheck {
  ok: boolean;
  message: string;
}

export const connectionStore = createStore<ConnectionStatus>({
  mode: getAuthMode(),
  state: getAuthMode() === "mock" ? "demo" : "unknown",
  message: getAuthMode() === "mock" ? "Demo mode: synthetic data, no provider connection." : "Connection not checked yet.",
});

// --- direct mode: in-memory token only (never persisted, never logged) ---
let runtimeToken: { value: string; expiresAt?: number } | null = null;

export const authService = {
  getMode: getAuthMode,

  setRuntimeToken(value: string, expiresInSeconds?: number) {
    const v = value.trim().replace(/^Bearer\s+/i, "");
    runtimeToken = v ? { value: v, expiresAt: expiresInSeconds ? Date.now() + expiresInSeconds * 1000 : undefined } : null;
  },

  clearRuntimeToken() {
    runtimeToken = null;
  },

  hasRuntimeToken() {
    return !!runtimeToken && (!runtimeToken.expiresAt || Date.now() < runtimeToken.expiresAt);
  },

  runtimeTokenExpiresAt() {
    return runtimeToken?.expiresAt ? new Date(runtimeToken.expiresAt).toISOString() : undefined;
  },

  /** Authorization header for direct mode. Proxy mode adds auth server-side; mock needs none. */
  getAuthorizationHeader(): string | undefined {
    if (getAuthMode() !== "direct" || !this.hasRuntimeToken()) return undefined;
    return `Bearer ${runtimeToken!.value}`;
  },

  /** Performs a real request to determine connection state. Never reports success without one. */
  async checkConnection(force = false): Promise<ConnectionStatus> {
    const mode = getAuthMode();
    const set = (s: Omit<ConnectionStatus, "mode">) => {
      const status = { mode, ...s };
      connectionStore.set(status);
      return status;
    };

    if (mode === "mock") return set({ state: "demo", message: "Demo mode: synthetic data, no provider connection." });

    const problems = configProblems(mode);
    if (problems.length) return set({ state: "not-configured", message: problems.join(" "), checkedAt: new Date().toISOString() });

    connectionStore.set((s) => ({ ...s, mode, state: "checking", message: "Checking connection…" }));

    if (mode === "proxy") {
      try {
        const res = await axios.get(`/rr-proxy/status${force ? "?force=1" : ""}`, { timeout: config.requestTimeoutMs });
        if (typeof res.data !== "object" || res.data?.ok !== true) {
          return set({ state: "not-configured", message: "Dev proxy is not available. Run the app with `npm run dev`.", checkedAt: new Date().toISOString() });
        }
        const data = await probe(`/rr-proxy/data/portfolios/`, "data");
        const outreach = await probe(`/rr-proxy/outreach/outreach-requests?limit=1`, "outreach");
        return set({
          ...summarise(data, outreach),
          checkedAt: new Date().toISOString(),
          tokenExpiresAt: new Date(Date.now() + res.data.expiresInSeconds * 1000).toISOString(),
          hosts: { data, outreach },
        });
      } catch (e) {
        const status = axios.isAxiosError(e) ? e.response?.status : undefined;
        const msg = axios.isAxiosError(e) && typeof e.response?.data === "object" ? e.response?.data?.proxy?.message : undefined;
        return set({
          state: status === 404 ? "not-configured" : "disconnected",
          message: status === 404 ? "Dev proxy is not available. Run the app with `npm run dev`." : `Token request via dev proxy failed${msg ? `: ${msg}` : "."}`,
          checkedAt: new Date().toISOString(),
        });
      }
    }

    // direct
    if (!this.hasRuntimeToken()) {
      return set({
        state: "not-configured",
        message: "Direct mode needs an access token. Paste one on the Settings page (kept in memory only). The client secret is never used in the browser.",
        checkedAt: new Date().toISOString(),
      });
    }
    const headers = { Authorization: this.getAuthorizationHeader()! };
    const data = await probe(`${config.dataApiUrl}/portfolios/`, "data", headers);
    const outreach = await probe(`${config.outreachApiUrl}/outreach-requests?limit=1`, "outreach", headers);
    return set({ ...summarise(data, outreach), checkedAt: new Date().toISOString(), tokenExpiresAt: this.runtimeTokenExpiresAt(), hosts: { data, outreach } });
  },
};

async function probe(url: string, host: HostKey, headers?: Record<string, string>): Promise<HostCheck> {
  try {
    await axios.get(url, { headers, timeout: config.requestTimeoutMs });
    recordSuccess(host);
    return { ok: true, message: "Request succeeded." };
  } catch (e) {
    if (axios.isAxiosError(e) && !e.response) {
      return { ok: false, message: "Blocked by the browser (CORS) or network error. This host does not allow direct browser calls from this origin." };
    }
    const status = axios.isAxiosError(e) ? e.response?.status : undefined;
    return { ok: false, message: status === 401 || status === 403 ? `HTTP ${status}: token missing, invalid or expired.` : `HTTP ${status ?? "error"}.` };
  }
}

function summarise(data: HostCheck, outreach: HostCheck): Pick<ConnectionStatus, "state" | "message"> {
  if (data.ok && outreach.ok) return { state: "connected", message: "Data API and Outreach API requests succeeded." };
  if (data.ok || outreach.ok) {
    return { state: "partial", message: `Data API: ${data.ok ? "OK" : data.message} Outreach API: ${outreach.ok ? "OK" : outreach.message}` };
  }
  return { state: "disconnected", message: `Data API: ${data.message} Outreach API: ${outreach.message}` };
}
