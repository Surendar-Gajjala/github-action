/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_RAPIDRATINGS_DATA_API_URL?: string;
  readonly VITE_RAPIDRATINGS_OUTREACH_API_URL?: string;
  readonly VITE_RAPIDRATINGS_AUTH_URL?: string;
  readonly VITE_RAPIDRATINGS_AUTH_MODE?: "mock" | "proxy" | "direct";
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
