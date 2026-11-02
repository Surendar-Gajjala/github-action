import { QueryClient } from "@tanstack/react-query";
import { isApiError } from "@/services/httpClient";

/**
 * In-memory cache only (no persister): provider response data is never written to browser storage.
 * The HTTP client already retries 429/5xx with backoff, so React Query does not retry API errors.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60_000,
      gcTime: 10 * 60_000,
      refetchOnWindowFocus: false,
      retry: (count, error) => !isApiError(error) && count < 1,
    },
    mutations: { retry: false },
  },
});
