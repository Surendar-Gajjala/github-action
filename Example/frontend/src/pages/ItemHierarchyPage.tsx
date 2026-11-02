import { ChevronsDownUp, ChevronsUpDown } from 'lucide-react';
import { useDeferredValue, useMemo, useState } from 'react';

import { PageHeader } from '../components/PageHeader';
import { TreeTable } from '../components/hierarchy/TreeTable';
import { expandableKeys, searchTree } from '../components/hierarchy/treeRows';
import { LoadError } from '../components/table/LoadError';
import { PaginationFooter } from '../components/table/PaginationFooter';
import { SearchBox } from '../components/table/SearchBox';
import { useItemHierarchy } from '../hooks/useItemHierarchy';
import { usePagination } from '../hooks/usePagination';

const toolbarButton =
  'inline-flex items-center gap-2 rounded-lg border border-gray-200 bg-white px-3 py-2 text-[15px] font-semibold text-gray-700 hover:bg-gray-50 disabled:opacity-50';

export function ItemHierarchyPage() {
  const { page, pageSize, setPage, setPageSize } = usePagination();
  const { data, isLoading, isError, error, isFetching, isPlaceholderData } = useItemHierarchy(page - 1, pageSize);
  const roots = useMemo(() => data?.objects ?? [], [data]);

  const [expanded, setExpanded] = useState<Set<string>>(() => new Set());
  const [query, setQuery] = useState('');
  const deferredQuery = useDeferredValue(query);
  const search = useMemo(() => searchTree(roots, deferredQuery), [roots, deferredQuery]);
  const visibleExpanded = useMemo(
    () => (search.expand.size ? new Set([...expanded, ...search.expand]) : expanded),
    [expanded, search.expand],
  );

  const toggle = (key: string) =>
    setExpanded((current) => {
      const next = new Set(current);
      if (!next.delete(key)) next.add(key);
      return next;
    });

  const offset = (page - 1) * pageSize;
  const pageCount = data ? (data.totalPages ?? (data.hasMore ? page + 1 : page)) : 1;

  return (
    <div className="flex h-full flex-col">
      <PageHeader title="Item Hierarchy" subtitle="Browse products and expand their items and sourced parts." />

      <div className="flex min-h-0 flex-1 flex-col bg-gray-50">
        <div className="flex flex-wrap items-center justify-between gap-3 px-10 py-5">
          <SearchBox
            label="Search products"
            value={query}
            onChange={setQuery}
            placeholder="Search this page by item, part or manufacturer..."
          />
          <div className="flex gap-2">
            <button
              type="button"
              className={toolbarButton}
              onClick={() => setExpanded(expandableKeys(roots))}
              disabled={isLoading || roots.length === 0}
            >
              <ChevronsUpDown className="size-4" aria-hidden />
              Expand all
            </button>
            <button
              type="button"
              className={toolbarButton}
              onClick={() => setExpanded(new Set())}
              disabled={expanded.size === 0}
            >
              <ChevronsDownUp className="size-4" aria-hidden />
              Collapse all
            </button>
          </div>
        </div>

        {isError ? (
          <LoadError title="Item hierarchy" error={error} />
        ) : (
          <>
            <div className={`flex min-h-0 flex-1 flex-col transition-opacity ${isPlaceholderData ? 'opacity-60' : ''}`}>
              <TreeTable
                roots={search.roots}
                expanded={visibleExpanded}
                onToggle={toggle}
                isLoading={isLoading}
                emptyMessage={query ? `No products on this page match "${query}".` : 'No products available.'}
              />
            </div>
            <PaginationFooter
              noun="products"
              page={page}
              pageCount={pageCount}
              pageSize={pageSize}
              rangeStart={data && data.count > 0 ? offset + 1 : 0}
              rangeEnd={offset + (data?.count ?? 0)}
              total={data?.totalObjects ?? null}
              isLoading={isLoading}
              isFetching={isFetching}
              onPageChange={setPage}
              onPageSizeChange={setPageSize}
            />
          </>
        )}
      </div>
    </div>
  );
}
