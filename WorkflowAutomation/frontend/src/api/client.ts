import type { ProblemDetail } from './types';

/**
 * Minimal fetch wrapper. Credentials are held in memory only (never in localStorage or VITE_* variables)
 * and sent as HTTP Basic auth. Every request carries a correlation ID for log tracing.
 */
let authorization: string | null = null;

export function setCredentials(username: string, password: string): void {
  authorization = 'Basic ' + btoa(`${username}:${password}`);
}

export function clearCredentials(): void {
  authorization = null;
}

export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail;

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${status}`);
    this.status = status;
    this.problem = problem;
  }
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  headers.set('X-Correlation-Id', crypto.randomUUID());
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (authorization) {
    headers.set('Authorization', authorization);
  }

  const response = await fetch(path, { ...init, headers });
  const text = await response.text();
  const data = text ? safeJson(text) : null;

  if (!response.ok) {
    throw new ApiError(response.status, (data as ProblemDetail) ?? { status: response.status });
  }
  return data as T;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return { detail: text };
  }
}
