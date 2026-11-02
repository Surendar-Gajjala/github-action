import type { AuthMode } from "@/types/api";
import { storage } from "@/utils/storage";

const MODES: AuthMode[] = ["mock", "proxy", "direct"];
const MODE_KEY = "rr.authModeOverride";

const env = import.meta.env;

/** Public, non-secret configuration. Only VITE_ variables reach the bundle. */
export const config = {
  dataApiUrl: (env.VITE_RAPIDRATINGS_DATA_API_URL as string | undefined)?.replace(/\/$/, "") ?? "",
  outreachApiUrl: (env.VITE_RAPIDRATINGS_OUTREACH_API_URL as string | undefined)?.replace(/\/$/, "") ?? "",
  authUrl: (env.VITE_RAPIDRATINGS_AUTH_URL as string | undefined) ?? "",
  envAuthMode: parseMode(env.VITE_RAPIDRATINGS_AUTH_MODE),
  isDevServer: env.DEV,
  requestTimeoutMs: 30_000,
  /** Fixed page sizes observed on the provider (not configurable via the API). */
  pageSize: { companies: 20, portfolios: 20, periods: 10, ratings: 10 },
  outreachPageSize: 20,
};

function parseMode(v: unknown): AuthMode {
  return MODES.includes(v as AuthMode) ? (v as AuthMode) : "mock";
}

/** Effective auth mode: a runtime override (Settings page, non-sensitive) wins over the env default. */
export function getAuthMode(): AuthMode {
  const override = storage.get<AuthMode | null>(MODE_KEY, null);
  return override && MODES.includes(override) ? override : config.envAuthMode;
}

export function setAuthModeOverride(mode: AuthMode | null) {
  if (mode === null) storage.remove(MODE_KEY);
  else storage.set(MODE_KEY, mode);
}

export function hasAuthModeOverride() {
  return storage.get<AuthMode | null>(MODE_KEY, null) !== null;
}

export function configProblems(mode: AuthMode = getAuthMode()): string[] {
  const problems: string[] = [];
  if (mode === "mock") return problems;
  if (!config.dataApiUrl) problems.push("VITE_RAPIDRATINGS_DATA_API_URL is not set.");
  if (!config.outreachApiUrl) problems.push("VITE_RAPIDRATINGS_OUTREACH_API_URL is not set.");
  if (mode === "proxy" && !config.isDevServer) {
    problems.push("Proxy mode only works under `npm run dev`. This build has no server to obtain tokens; use a backend/API gateway in production.");
  }
  return problems;
}
