const dateTime = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'medium' });
const date = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeZone: 'UTC' });

export function formatDateTime(value?: string | null): string {
  return value ? dateTime.format(new Date(value)) : '—';
}

/** Formats an ISO calendar date (yyyy-mm-dd) without shifting it across time zones. */
export function formatDate(value?: string | null): string {
  return value ? date.format(new Date(`${value}T00:00:00Z`)) : '—';
}
