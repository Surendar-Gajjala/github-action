import { useEffect, useMemo, useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";

interface Props {
  /** Human summary, e.g. "Page 2 of 5 · 96 records" */
  summary: string;
  hasPrevious: boolean;
  hasNext: boolean;
  onPrevious: () => void;
  onNext: () => void;
  busy?: boolean;
}

/** Previous/next only: provider lists must be navigated via their own next/previous links or cursors. */
export function TablePagination({ summary, hasPrevious, hasNext, onPrevious, onNext, busy }: Props) {
  return (
    <div className="flex items-center justify-between gap-3 px-4 py-3 text-sm text-slate-700">
      <span aria-live="polite">{summary}</span>
      <div className="flex items-center gap-1">
        <button type="button" className="btn-ghost px-2 py-1.5 text-slate-500" disabled={!hasPrevious || busy} onClick={onPrevious} aria-label="Previous page">
          <ChevronLeft className="h-4 w-4" /> Prev
        </button>
        <button type="button" className="btn-ghost px-2 py-1.5 text-slate-500" disabled={!hasNext || busy} onClick={onNext} aria-label="Next page">
          Next <ChevronRight className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}

/** Client-side paging for lists already fully loaded in memory (periods, ratings, filtered results). */
export function useClientPagination<T>(items: T[], pageSize: number) {
  const [page, setPage] = useState(1);
  const pages = Math.max(1, Math.ceil(items.length / pageSize));
  useEffect(() => setPage((p) => Math.min(p, pages)), [pages]);
  const slice = useMemo(() => items.slice((page - 1) * pageSize, page * pageSize), [items, page, pageSize]);
  return {
    slice,
    page,
    pages,
    reset: () => setPage(1),
    props: {
      summary: `Page ${page} of ${pages} · ${items.length.toLocaleString()} record(s)`,
      hasPrevious: page > 1,
      hasNext: page < pages,
      onPrevious: () => setPage((p) => Math.max(1, p - 1)),
      onNext: () => setPage((p) => Math.min(pages, p + 1)),
    },
  };
}
