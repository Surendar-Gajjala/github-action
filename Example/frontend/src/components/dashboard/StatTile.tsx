import type { UseQueryResult } from '@tanstack/react-query';
import { ArrowRight, type LucideIcon } from 'lucide-react';
import { Link } from 'react-router-dom';

import { getErrorMessage } from '../../services/enterpriseQuery';
import type { PageResponse } from '../../types/page';

interface Props {
  /** Sentence-case label, e.g. "Items". */
  label: string;
  to: string;
  icon: LucideIcon;
  /** Count query: a one-row page whose `totalObjects` is the count. */
  query: UseQueryResult<PageResponse<unknown>>;
  /** Link text; defaults to "View <label>". */
  linkText?: string;
}

const exact = new Intl.NumberFormat();
const compact = new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 });

/** Below 10,000 the exact figure fits; above, compact (12.9K) with the exact figure underneath. */
const display = (count: number) => (count < 10_000 ? exact.format(count) : compact.format(count));

/** KPI tile: a total count from the hosted server, linking to the tab that lists it. */
export function StatTile({ label, to, icon: Icon, query, linkText = `View ${label.toLowerCase()}` }: Props) {
  const count = query.data?.totalObjects ?? query.data?.count ?? null;

  return (
    <Link
      to={to}
      className="group flex flex-col rounded-xl border border-gray-200 bg-white p-6 transition-colors hover:border-blue-300 hover:bg-blue-50/30 focus-visible:ring-2 focus-visible:ring-blue-500/40 focus-visible:outline-none"
    >
      <div className="flex items-center gap-2.5 text-[16px] font-semibold text-gray-600">
        <span className="flex size-8 items-center justify-center rounded-lg bg-gray-100 text-gray-700">
          <Icon className="size-[18px]" strokeWidth={1.75} aria-hidden />
        </span>
        {label}
      </div>

      <div className="mt-5 min-h-[72px]" aria-live="polite">
        {query.isLoading ? (
          <>
            <span className="sr-only">Loading {label.toLowerCase()} count</span>
            <span className="block h-10 w-28 animate-pulse rounded bg-gray-200" aria-hidden />
          </>
        ) : query.isError ? (
          <>
            <p className="text-[28px] font-semibold text-gray-400">Unavailable</p>
            <p className="mt-1 text-sm text-gray-500">{getErrorMessage(query.error)}</p>
          </>
        ) : (
          <>
            <p className="text-[40px] leading-none font-semibold text-gray-900 tabular-nums" data-testid={`${label}-count`}>
              {count === null ? '—' : display(count)}
            </p>
            <p className="mt-2 text-sm text-gray-500">
              {count === null ? 'Total not reported' : `${exact.format(count)} total`}
            </p>
          </>
        )}
      </div>

      <span className="mt-4 inline-flex items-center gap-1.5 text-[15px] font-semibold text-blue-600">
        {linkText}
        <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" aria-hidden />
      </span>
    </Link>
  );
}
