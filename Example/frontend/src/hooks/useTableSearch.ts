import { useDeferredValue, useMemo, useState } from 'react';

/** Keys of T whose values are strings (or null), i.e. searchable as text. */
export type TextKey<T> = { [K in keyof T]: T[K] extends string | null ? K : never }[keyof T];

/** Client-side search over the rows already on screen; never triggers a request. */
export function useTableSearch<T>(rows: T[], fields: readonly TextKey<T>[]) {
  const [query, setQuery] = useState('');
  const deferredQuery = useDeferredValue(query);

  const filtered = useMemo(() => {
    const needle = deferredQuery.trim().toLowerCase();
    if (!needle) return rows;
    return rows.filter((row) =>
      fields.some((field) => (row[field] as string | null)?.toLowerCase().includes(needle)),
    );
  }, [rows, fields, deferredQuery]);

  return { query, setQuery, filtered };
}
