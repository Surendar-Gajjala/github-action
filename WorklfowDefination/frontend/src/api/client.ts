import type { ApiErrorBody, PageQuery } from './types';

const CREDENTIALS_KEY = 'scm.credentials';

export class ApiError extends Error {
  readonly status: number;
  readonly body?: ApiErrorBody;

  constructor(status: number, message: string, body?: ApiErrorBody) {
    super(message);
    this.status = status;
    this.body = body;
  }

  /** Field errors keyed by field name, for mapping onto forms. */
  get fieldErrors(): Record<string, string> {
    const out: Record<string, string> = {};
    for (const f of this.body?.fieldErrors ?? []) {
      out[f.field] = out[f.field] ? `${out[f.field]}; ${f.message}` : f.message;
    }
    return out;
  }
}

// Demo HTTP Basic credentials, kept for the browser session only (local development).
export function setCredentials(username: string, password: string) {
  sessionStorage.setItem(CREDENTIALS_KEY, btoa(`${username}:${password}`));
}

export function clearCredentials() {
  sessionStorage.removeItem(CREDENTIALS_KEY);
}

export function hasCredentials() {
  return sessionStorage.getItem(CREDENTIALS_KEY) !== null;
}

let unauthorizedHandler: (() => void) | undefined;
export function onUnauthorized(handler: () => void) {
  unauthorizedHandler = handler;
}

export async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  const token = sessionStorage.getItem(CREDENTIALS_KEY);
  if (token) headers.Authorization = `Basic ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let response: Response;
  try {
    response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError(0, 'The server is not reachable. Is the backend running on port 8085?');
  }

  if (response.status === 204) return undefined as T;
  const text = await response.text();
  const data = text ? safeJson(text) : undefined;
  if (!response.ok) {
    if (response.status === 401) unauthorizedHandler?.();
    const errorBody = data as ApiErrorBody | undefined;
    throw new ApiError(response.status, errorBody?.message ?? `${response.status} ${response.statusText}`, errorBody);
  }
  return data as T;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return { message: text };
  }
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body ?? {}),
  put: <T>(path: string, body: unknown) => request<T>('PUT', path, body),
  delete: <T>(path: string) => request<T>('DELETE', path),
};

export function query<T extends PageQuery>(params: T): string {
  const sp = new URLSearchParams();
  Object.entries(params as Record<string, unknown>).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') sp.set(k, String(v));
  });
  const s = sp.toString();
  return s ? `?${s}` : '';
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    const details = error.body?.details?.length ? `\n• ${error.body.details.join('\n• ')}` : '';
    return error.message + details;
  }
  return error instanceof Error ? error.message : String(error);
}
