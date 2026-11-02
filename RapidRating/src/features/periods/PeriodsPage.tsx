import { useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Building2, RefreshCw, Star } from "lucide-react";
import { usePeriods } from "@/hooks/usePeriods";
import { useCompany } from "@/hooks/useCompanies";
import { CompanySelector } from "@/components/common/CompanySelector";
import { DataTable } from "@/components/tables/DataTable";
import { TablePagination, useClientPagination } from "@/components/tables/TablePagination";
import { DemoBanner, PageHeader } from "@/components/common/PageHeader";
import { LoadingState } from "@/components/common/LoadingState";
import { ErrorState } from "@/components/common/ErrorState";
import { EmptyState } from "@/components/common/EmptyState";
import { DetailList, Drawer } from "@/components/common/Drawer";
import { JsonViewer } from "@/components/common/JsonViewer";
import { fmt } from "@/utils/format";
import type { Period } from "@/types/period";

const AMOUNT_COLUMNS: [keyof Period, string][] = [
  ["salesRevenue", "Revenue"],
  ["earningsBeforeInterestAndTax", "EBIT"],
  ["netProfitAfterTax", "Net profit"],
  ["totalAssets", "Total assets"],
  ["totalLiabilities", "Total liabilities"],
  ["totalShareholderEquity", "Equity"],
];

export function PeriodsPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const rrid = params.get("rrid") ?? undefined;
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<Period>();

  const company = useCompany(rrid);
  const query = usePeriods(rrid);

  const filtered = useMemo(() => {
    const s = search.trim().toLowerCase();
    return (query.data ?? []).filter((p) => !s || p.id.toLowerCase().includes(s) || p.financialDate?.includes(s));
  }, [query.data, search]);
  const pager = useClientPagination(filtered, 10);

  const setRrid = (v?: string) => {
    setSelected(undefined);
    setParams(v ? { rrid: v } : {});
  };
  const toRating = (id?: string) => navigate(`/ratings?rrid=${encodeURIComponent(rrid!)}${id ? `&rating=${encodeURIComponent(id)}` : ""}`);

  return (
    <>
      <PageHeader
        title="Periods"
        description="GET /v2.0/companies/{rrid}/periods/ · financial statement figures per reporting period"
        actions={
          rrid && (
            <>
              <button type="button" className="btn-secondary" onClick={() => toRating()}>
                <Star className="h-4 w-4" aria-hidden /> Ratings for this company
              </button>
              <button type="button" className="btn-secondary" onClick={() => query.refetch()} disabled={query.isFetching}>
                <RefreshCw className={`h-4 w-4 ${query.isFetching ? "animate-spin" : ""}`} aria-hidden /> Refresh
              </button>
            </>
          )
        }
      />
      <DemoBanner />
      <div className="card mb-4 p-3">
        <CompanySelector value={rrid} onChange={setRrid} />
        {rrid && company.data && (
          <p className="mt-2 text-xs text-slate-500">
            {company.data.name} · amounts in <strong>{fmt(company.data.currency)}</strong>, scale <strong>{fmt(company.data.dataScale)}</strong> (from the company record)
          </p>
        )}
      </div>

      <div className="card-flush">
        {!rrid ? (
          <EmptyState icon={<Building2 className="h-10 w-10" />} title="Select a company" description="Choose a company (or enter its RRID) to retrieve its reporting periods." />
        ) : query.isLoading ? (
          <LoadingState label="Loading periods (following provider pages)…" />
        ) : query.error ? (
          <ErrorState error={query.error} onRetry={() => query.refetch()} />
        ) : !query.data?.length ? (
          <EmptyState title="No periods returned" description="The API returned no reporting periods for this company." />
        ) : (
          <>
            <div className="border-b border-slate-200 p-3">
              <div className="w-64">
                <label htmlFor="period-search" className="label">
                  Filter periods
                </label>
                <input id="period-search" className="input" placeholder="e.g. 2019 or YE" value={search} onChange={(e) => (setSearch(e.target.value), pager.reset())} />
              </div>
            </div>
            <DataTable
              columns={[
                { key: "id", header: "Period", render: (p) => <span className="font-medium">{p.id}</span> },
                { key: "eqyYear", header: "Year", render: (p) => p.eqyYear },
                { key: "period", header: "Type" },
                { key: "financialDate", header: "Financial date" },
                { key: "currency", header: "Currency" },
                ...AMOUNT_COLUMNS.map(([k, h]) => ({ key: k, header: h, align: "right" as const })),
                {
                  key: "rating",
                  header: <span className="sr-only">Actions</span>,
                  render: (p) => (
                    <button type="button" className="btn-ghost px-2 py-1 text-xs" onClick={(e) => (e.stopPropagation(), toRating(p.id))}>
                      <Star className="h-3.5 w-3.5" aria-hidden /> Rating
                    </button>
                  ),
                },
              ]}
              rows={pager.slice}
              rowKey={(p) => p.id}
              onRowClick={setSelected}
              selectedKey={selected?.id}
            />
            <TablePagination {...pager.props} />
          </>
        )}
      </div>

      <Drawer
        open={!!selected}
        onClose={() => setSelected(undefined)}
        title={`Period ${selected?.id ?? ""}`}
        subtitle={`GET /v2.0/companies/${rrid}/periods/${selected?.id}/`}
        footer={
          selected && (
            <button type="button" className="btn-primary" onClick={() => toRating(selected.id)}>
              <Star className="h-4 w-4" aria-hidden /> View rating {selected.id}
            </button>
          )
        }
      >
        {selected && (
          <div className="space-y-5">
            <DetailList rows={Object.entries(selected).filter(([k]) => k !== "href").map(([k, v]) => [k, fmt(v)])} />
            <JsonViewer data={selected} maxHeight="16rem" />
          </div>
        )}
      </Drawer>
    </>
  );
}
