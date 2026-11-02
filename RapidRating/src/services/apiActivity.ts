import { createStore } from "@/utils/store";

export type HostKey = "data" | "outreach";

export interface HostActivity {
  lastSuccessAt?: string;
  lastErrorAt?: string;
  lastErrorMessage?: string;
}

/** Records real request outcomes. "Connected" in the UI is derived only from actual successes. */
export const apiActivity = createStore<Record<HostKey, HostActivity>>({ data: {}, outreach: {} });

export function recordSuccess(host: HostKey) {
  apiActivity.set((s) => ({ ...s, [host]: { ...s[host], lastSuccessAt: new Date().toISOString() } }));
}

export function recordError(host: HostKey, message: string) {
  apiActivity.set((s) => ({ ...s, [host]: { ...s[host], lastErrorAt: new Date().toISOString(), lastErrorMessage: message } }));
}

export function lastSuccessfulRequest(): string | undefined {
  const { data, outreach } = apiActivity.get();
  return [data.lastSuccessAt, outreach.lastSuccessAt].filter(Boolean).sort().pop();
}
