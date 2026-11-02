import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { CalendarRange, RefreshCw, Search, Star } from "lucide-react";
import { useCompanies, useCompany } from "@/hooks/useCompanies";
import { DataTable, type Column } from "@/components/tables/DataTable";
import { TablePagination } from "@/components/tables/TablePagination";
import { DemoBanner, PageHeader } from "@/components/common/PageHeader";
import { LoadingState } from "@/components/common/LoadingState";
import { ErrorState } from "@/components/common/ErrorState";
import { EmptyState } from "@/components/common/EmptyState";
import { DetailList, Drawer } from "@/components/common/Drawer";
import { JsonViewer } from "@/components/common/JsonViewer";
import { fmt } from "@/utils/format";
import type { Company } from "@/types/company";

function useDebounced<T>(value: T, ms = 400) {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}

export function CompaniesPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState("");
  const [portfolio, setPortfolio] = useState("");
  const [selected, setSelected] = useState<string>();

  const term = useDebounced(search.trim());
  // Provider rule: `name` must be >= 3 characters (shorter returns 400), so shorter input is not sent
  const name = term.length >= 3 ? term : undefined;
  const portfolioId = /^\d+$/.test(portfolio) ? Number(portfolio) : undefined;
  useEffect(() => setPage(1), [name, portfolioId]);

  const query = useCompanies({ page, name, portfolio: portfolioId });
  const data = query.data;

  const goTo = (path: string, rrid: string) => navigate(`${path}?rrid=${encodeURIComponent(rrid)}`);

  const columns: Column<Company>[] = [
    { key: "name", header: "Name", render: (c) => <span className="link">{fmt(c.name)}</span> },
    { key: "id", header: "RRID", render: (c) => <span className="font-mono text-xs">{c.id}</span> },
    { key: "country", header: "Country" },
    { key: "ticker", header: "Ticker" },
    { key: "sector", header: "Sector" },
    { key: "latest", header: "Latest rating", render: (c) => fmt(c.latestRatings?.[0]?.id) },
    { key: "fhr", header: "FHR", align: "right", render: (c) => fmt(c.latestRatings?.[0]?.fhr) },
    { key: "chs", header: "CHS", align: "right", render: (c) => fmt(c.latestRatings?.[0]?.chs) },
    { key: "financialDate", header: "Financial date", render: (c) => fmt(c.latestRatings?.[0]?.financialDate) },
    {
      key: "actions",
      header: <span className="sr-only">Actions</span>,
      render: (c) => (
        <div className="flex gap-1" onClick={(e) => e.stopPropagation()}>
          <button type="button" className="btn-ghost px-2 py-1 text-xs" onClick={() => goTo("/periods", c.id)} title="View reporting periods">
            <CalendarRange className="h-3.5 w-3.5" aria-hidden /> Periods
          </button>
          <button type="button" className="btn-ghost px-2 py-1 text-xs" onClick={() => goTo("/ratings", c.id)} title="View ratings">
            <Star className="h-3.5 w-3.5" aria-hidden /> Ratings
          </button>
        </div>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Companies"
        description="GET /v2.0/companies/ · 20 per page (fixed by the provider) · FHR/CHS shown from latestRatings[0]"
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
            <label htmlFor="company-search" className="label">
              Search by name (server-side, min 3 characters)
            </label>
            <div className="relative">
              <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-slate-400" aria-hidden />
              <input id="company-search" className="input pl-9" placeholder="e.g. foods" value={search} onChange={(e) => setSearch(e.target.value)} />
            </div>
            {term.length > 0 && term.length < 3 && <p className="mt-1 text-xs text-amber-700">Enter at least 3 characters (provider rule).</p>}
          </div>
          <div className="w-40">
            <label htmlFor="company-portfolio" className="label">
              Portfolio ID
            </label>
            <input id="company-portfolio" className="input" inputMode="numeric" placeholder="e.g. 27070" value={portfolio} onChange={(e) => setPortfolio(e.target.value.trim())} />
          </div>
        </div>

        {query.isLoading ? (
          <LoadingState label="Loading companies…" />
        ) : query.error ? (
          <ErrorState error={query.error} onRetry={() => query.refetch()} />
        ) : !data || data.items.length === 0 ? (
          <EmptyState title="No companies found" description={name || portfolioId ? "No company matches the current filters." : "The API returned no companies for this account."} />
        ) : (
          <>
            <DataTable columns={columns} rows={data.items} rowKey={(c) => c.id} onRowClick={(c) => setSelected(c.id)} selectedKey={selected} />
            <TablePagination
              summary={`Page ${data.page} of ${Math.max(1, Math.ceil(data.total / data.pageSize))} · ${data.total.toLocaleString()} companies`}
              hasPrevious={data.hasPrevious}
              hasNext={data.hasNext}
              busy={query.isFetching}
              onPrevious={() => data.previousPage && setPage(data.previousPage)}
              onNext={() => data.nextPage && setPage(data.nextPage)}
            />
          </>
        )}
      </div>

      <CompanyDrawer rrid={selected} onClose={() => setSelected(undefined)} onNavigate={goTo} />
    </>
  );
}

function CompanyDrawer({ rrid, onClose, onNavigate }: { rrid?: string; onClose: () => void; onNavigate: (path: string, rrid: string) => void }) {
  const { data: c, isLoading, error, refetch } = useCompany(rrid);
  return (
    <Drawer
      open={!!rrid}
      onClose={onClose}
      title={c?.name ?? rrid ?? ""}
      subtitle={<>GET /v2.0/companies/{rrid}/</>}
      footer={
        rrid && (
          <>
            <button type="button" className="btn-primary" onClick={() => onNavigate("/periods", rrid)}>
              <CalendarRange className="h-4 w-4" aria-hidden /> View periods
            </button>
            <button type="button" className="btn-secondary" onClick={() => onNavigate("/ratings", rrid)}>
              <Star className="h-4 w-4" aria-hidden /> View ratings
            </button>
          </>
        )
      }
    >
      {isLoading ? (
        <LoadingState />
      ) : error ? (
        <ErrorState error={error} onRetry={() => refetch()} compact />
      ) : c ? (
        <div className="space-y-5">
          <DetailList
            rows={[
              ["RRID", <span className="font-mono">{c.id}</span>],
              ["Country", fmt(c.country)],
              ["Ticker / exchange", `${fmt(c.ticker)} / ${fmt(c.exchange)}`],
              ["Sector", fmt(c.sector)],
              ["Address", fmt(c.address)],
              ["Industry classification", `${fmt(c.industryClassificationSystem)} ${c.industryClassificationCode ?? ""}`],
              ["Currency / scale", `${fmt(c.currency)} / ${fmt(c.dataScale)}`],
              ["Data confidentiality", fmt(c.dataConfidential)],
              ["External source ID", fmt(c.externalSourceId)],
              ["Sector averages", c.sectorAverages ? `FHR ${fmt(c.sectorAverages.fhr)} · CHS ${fmt(c.sectorAverages.chs)}` : fmt(undefined)],
            ]}
          />
          <section>
            <h3 className="mb-2 text-sm font-semibold text-slate-800">Latest ratings (newest first)</h3>
            {c.latestRatings?.length ? (
              <DataTable
                columns={[
                  { key: "id", header: "Rating" },
                  { key: "financialDate", header: "Financial date" },
                  { key: "reportingPeriod", header: "Reporting period" },
                  { key: "fhr", header: "FHR", align: "right" },
                  { key: "chs", header: "CHS", align: "right" },
                  { key: "probabilityDefault", header: "PD %", align: "right" },
                ]}
                rows={c.latestRatings}
                rowKey={(r) => r.id}
              />
            ) : (
              <p className="text-sm text-slate-500">No ratings returned for this company.</p>
            )}
          </section>
          {c.reports?.length ? (
            <section>
              <h3 className="mb-2 text-sm font-semibold text-slate-800">Reports (download links returned by the API)</h3>
              <ul className="list-inside list-disc text-sm text-slate-600">
                {c.reports.map((r) => (
                  <li key={r.reportType}>
                    {r.reportType} <span className="text-xs text-slate-400">(requires API auth)</span>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}
          <section>
            <h3 className="mb-2 text-sm font-semibold text-slate-800">Raw response</h3>
            <JsonViewer data={c} maxHeight="20rem" />
          </section>
        </div>
      ) : null}
    </Drawer>
  );
}
