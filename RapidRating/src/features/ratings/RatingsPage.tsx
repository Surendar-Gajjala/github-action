import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Building2, CalendarRange, RefreshCw } from "lucide-react";
import { useRatings } from "@/hooks/useRatings";
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
import { StatusBadge } from "@/components/common/StatusBadge";
import { fmt } from "@/utils/format";
import { getAuthMode } from "@/services/config";
import type { Rating } from "@/types/rating";

/** Provider-returned qualitative values, shown verbatim (no score-to-category mapping is applied). */
const Level = ({ v }: { v?: string | null }) => (v ? <StatusBadge tone="slate">{v}</StatusBadge> : <>{fmt(v)}</>);

export function RatingsPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const rrid = params.get("rrid") ?? undefined;
  const ratingParam = params.get("rating") ?? undefined;
  const [search, setSearch] = useState("");
  const [selectedId, setSelectedId] = useState<string | undefined>(ratingParam);

  const company = useCompany(rrid);
  const query = useRatings(rrid);
  useEffect(() => setSelectedId(ratingParam), [ratingParam]);

  const filtered = useMemo(() => {
    const s = search.trim().toLowerCase();
    return (query.data ?? []).filter((r) => !s || r.id.toLowerCase().includes(s) || r.financialDate?.includes(s) || r.reportingPeriod?.includes(s));
  }, [query.data, search]);
  const pager = useClientPagination(filtered, 10);
  const selected = query.data?.find((r) => r.id === selectedId);

  const setRrid = (v?: string) => setParams(v ? { rrid: v } : {});
  const origin = getAuthMode() === "mock" ? "Demo data" : "Provider data";

  return (
    <>
      <PageHeader
        title="Ratings"
        description="GET /v2.0/companies/{rrid}/ratings/ · full rating history, newest first"
        actions={
          rrid && (
            <>
              <button type="button" className="btn-secondary" onClick={() => navigate(`/periods?rrid=${encodeURIComponent(rrid)}`)}>
                <CalendarRange className="h-4 w-4" aria-hidden /> Periods for this company
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
        <CompanySelector value={rrid} onChange={setRrid} id="rating-company" />
        {rrid && company.data && (
          <p className="mt-2 text-xs text-slate-500">
            {company.data.name}
            {company.data.sectorAverages && (
              <>
                {" "}
                · sector averages returned by the API: FHR {fmt(company.data.sectorAverages.fhr)}, CHS {fmt(company.data.sectorAverages.chs)}
              </>
            )}
          </p>
        )}
      </div>

      <div className="card-flush">
        {!rrid ? (
          <EmptyState icon={<Building2 className="h-10 w-10" />} title="Select a company" description="Choose a company (or enter its RRID) to view its ratings." />
        ) : query.isLoading ? (
          <LoadingState label="Loading ratings (following provider pages)…" />
        ) : query.error ? (
          <ErrorState error={query.error} onRetry={() => query.refetch()} />
        ) : !query.data?.length ? (
          <EmptyState title="No ratings returned" description="The API returned no ratings for this company. No placeholder scores are shown." />
        ) : (
          <>
            <div className="flex flex-wrap items-end justify-between gap-3 border-b border-slate-200 p-3">
              <div className="w-64">
                <label htmlFor="rating-search" className="label">
                  Filter ratings
                </label>
                <input id="rating-search" className="input" placeholder="e.g. 2020 or Q3" value={search} onChange={(e) => (setSearch(e.target.value), pager.reset())} />
              </div>
              <StatusBadge tone={origin === "Demo data" ? "violet" : "green"}>{origin}</StatusBadge>
            </div>
            <DataTable
              columns={[
                { key: "id", header: "Rating", render: (r) => <span className="font-medium">{r.id}</span> },
                { key: "financialDate", header: "Financial date" },
                { key: "reportingPeriod", header: "Reporting period" },
                { key: "fhr", header: "FHR", align: "right" },
                { key: "chs", header: "CHS", align: "right" },
                { key: "probabilityDefault", header: "PD (1y, %)", align: "right" },
                { key: "delta", header: "Δ FHR", align: "right" },
                { key: "simulatedFhr", header: "Sim. FHR", align: "right" },
                { key: "leverage", header: "Leverage", render: (r) => <Level v={r.leverage} /> },
                { key: "liquidity", header: "Liquidity", render: (r) => <Level v={r.liquidity} /> },
                { key: "earningsPerformance", header: "Earnings", render: (r) => <Level v={r.earningsPerformance} /> },
              ]}
              rows={pager.slice}
              rowKey={(r) => r.id}
              onRowClick={(r) => setSelectedId(r.id)}
              selectedKey={selectedId}
            />
            <TablePagination {...pager.props} />
          </>
        )}
      </div>

      <RatingDrawer rating={selected} rrid={rrid} onClose={() => setSelectedId(undefined)} />
    </>
  );
}

function RatingDrawer({ rating, rrid, onClose }: { rating?: Rating; rrid?: string; onClose: () => void }) {
  return (
    <Drawer open={!!rating} onClose={onClose} title={`Rating ${rating?.id ?? ""}`} subtitle={`GET /v2.0/companies/${rrid}/ratings/${rating?.id}/`}>
      {rating && (
        <div className="space-y-5">
          <DetailList
            rows={Object.entries(rating)
              .filter(([k]) => k !== "termPd" && k !== "href")
              .map(([k, v]) => [k, fmt(v)])}
          />
          {rating.termPd && (
            <section>
              <h3 className="mb-2 text-sm font-semibold text-slate-800">termPd: cumulative probability of default (%)</h3>
              <DataTable columns={Object.keys(rating.termPd).map((k) => ({ key: k, header: k, align: "right" as const }))} rows={[rating.termPd]} rowKey={() => "termPd"} />
            </section>
          )}
          <JsonViewer data={rating} maxHeight="16rem" />
        </div>
      )}
    </Drawer>
  );
}
