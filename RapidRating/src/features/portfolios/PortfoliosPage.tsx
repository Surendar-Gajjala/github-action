import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { RefreshCw, Search } from "lucide-react";
import { usePortfolio, usePortfolios } from "@/hooks/usePortfolios";
import { useCompanies } from "@/hooks/useCompanies";
import { DataTable } from "@/components/tables/DataTable";
import { TablePagination } from "@/components/tables/TablePagination";
import { DemoBanner, PageHeader } from "@/components/common/PageHeader";
import { LoadingState } from "@/components/common/LoadingState";
import { ErrorState } from "@/components/common/ErrorState";
import { EmptyState } from "@/components/common/EmptyState";
import { DetailList, Drawer } from "@/components/common/Drawer";
import { JsonViewer } from "@/components/common/JsonViewer";
import { fmt } from "@/utils/format";

export function PortfoliosPage() {
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<number>();
  const query = usePortfolios(page);
  const data = query.data;

  // The provider accepts no search parameter on /portfolios/, so search filters the loaded page
  const rows = useMemo(() => {
    const s = search.trim().toLowerCase();
    return (data?.items ?? []).filter((p) => !s || p.name?.toLowerCase().includes(s) || String(p.id).includes(s));
  }, [data, search]);

  return (
    <>
      <PageHeader
        title="Portfolios"
        description="GET /v2.0/portfolios/ · read-only"
        actions={
          <button type="button" className="btn-secondary" onClick={() => query.refetch()} disabled={query.isFetching}>
            <RefreshCw className={`h-4 w-4 ${query.isFetching ? "animate-spin" : ""}`} aria-hidden /> Refresh
          </button>
        }
      />
      <DemoBanner />
      <div className="card-flush">
        <div className="toolbar">
          <div className="w-72">
            <label htmlFor="portfolio-search" className="label">
              Search this page (the API has no portfolio search)
            </label>
            <div className="relative">
              <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-slate-400" aria-hidden />
              <input id="portfolio-search" className="input pl-9" placeholder="Name or ID" value={search} onChange={(e) => setSearch(e.target.value)} />
            </div>
          </div>
        </div>
        {query.isLoading ? (
          <LoadingState label="Loading portfolios…" />
        ) : query.error ? (
          <ErrorState error={query.error} onRetry={() => query.refetch()} />
        ) : !rows.length ? (
          <EmptyState title="No portfolios" description={search ? "No portfolio on this page matches the search." : "The API returned no portfolios."} />
        ) : (
          <>
            <DataTable
              columns={[
                { key: "id", header: "Portfolio ID", render: (p) => <span className="font-mono text-xs">{p.id}</span> },
                { key: "name", header: "Name", render: (p) => <span className="link">{fmt(p.name)}</span> },
                { key: "companies", header: "Companies link", render: (p) => (p.companies ? <span className="text-xs text-slate-500">/companies/?portfolio={p.id}</span> : fmt(undefined)) },
              ]}
              rows={rows}
              rowKey={(p) => String(p.id)}
              onRowClick={(p) => setSelected(p.id)}
              selectedKey={selected !== undefined ? String(selected) : undefined}
            />
            {data && (
              <TablePagination
                summary={`Page ${data.page} · ${data.total.toLocaleString()} portfolio(s)`}
                hasPrevious={data.hasPrevious}
                hasNext={data.hasNext}
                busy={query.isFetching}
                onPrevious={() => data.previousPage && setPage(data.previousPage)}
                onNext={() => data.nextPage && setPage(data.nextPage)}
              />
            )}
          </>
        )}
      </div>
      <PortfolioDrawer id={selected} onClose={() => setSelected(undefined)} />
    </>
  );
}

function PortfolioDrawer({ id, onClose }: { id?: number; onClose: () => void }) {
  const { data: p, isLoading, error, refetch } = usePortfolio(id);
  // The only documented relationship: the `companies` link = /companies/?portfolio={id}. Fetch its first page for the count.
  const enabled = id !== undefined;
  const companies = useCompanies({ portfolio: id, page: 1 }, enabled);

  return (
    <Drawer open={enabled} onClose={onClose} title={p?.name ?? String(id ?? "")} subtitle={<>GET /v2.0/portfolios/{id}/</>}>
      {isLoading ? (
        <LoadingState />
      ) : error ? (
        <ErrorState error={error} onRetry={() => refetch()} compact />
      ) : p ? (
        <div className="space-y-5">
          <DetailList rows={[["Portfolio ID", <span className="font-mono">{p.id}</span>], ["Name", fmt(p.name)], ["Companies link", fmt(p.companies)]]} />
          <section>
            <h3 className="mb-2 text-sm font-semibold text-slate-800">Companies in this portfolio</h3>
            <p className="mb-2 text-xs text-slate-500">From the portfolio's documented companies link (GET /companies/?portfolio={p.id}).</p>
            {companies.isLoading ? (
              <LoadingState />
            ) : companies.error ? (
              <ErrorState error={companies.error} compact />
            ) : companies.data ? (
              <>
                <p className="mb-2 text-sm">
                  <strong>{companies.data.total.toLocaleString()}</strong> companies. First {companies.data.items.length} shown.
                </p>
                <ul className="divide-y divide-slate-100 rounded border border-slate-200 text-sm">
                  {companies.data.items.map((c) => (
                    <li key={c.id} className="flex justify-between px-3 py-1.5">
                      <span>{c.name}</span>
                      <span className="font-mono text-xs text-slate-500">{c.id}</span>
                    </li>
                  ))}
                </ul>
                <Link to="/companies" className="mt-2 inline-block text-sm text-blue-700 hover:underline">
                  Open Companies (filter by portfolio ID {p.id})
                </Link>
              </>
            ) : null}
          </section>
          <section>
            <h3 className="mb-2 text-sm font-semibold text-slate-800">Raw response</h3>
            <JsonViewer data={p} />
          </section>
        </div>
      ) : null}
    </Drawer>
  );
}
