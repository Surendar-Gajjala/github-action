/** A failed request whose message is safe to show users: it never contains credentials or tokens. */
export class UserFacingError extends Error {
  constructor(
    message: string,
    readonly status: number | null = null,
  ) {
    super(message);
    this.name = 'UserFacingError';
  }
}

/** User-facing message for a failed request; never contains credentials. */
export function getErrorMessage(error: unknown): string {
  return error instanceof UserFacingError ? error.message : 'Something went wrong.';
}
