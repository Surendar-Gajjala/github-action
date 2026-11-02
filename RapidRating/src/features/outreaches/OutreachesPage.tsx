import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Plus, RefreshCw } from "lucide-react";
import { useOutreach, useOutreachCommunications, useOutreaches } from "@/hooks/useOutreaches";
import { DataTable } from "@/components/tables/DataTable";
import { TablePagination } from "@/components/tables/TablePagination";
import { DemoBanner, PageHeader } from "@/components/common/PageHeader";
import { LoadingState } from "@/components/common/LoadingState";
import { ErrorState } from "@/components/common/ErrorState";
import { EmptyState } from "@/components/common/EmptyState";
import { DetailList, Drawer } from "@/components/common/Drawer";
import { JsonViewer } from "@/components/common/JsonViewer";
import { StatusBadge } from "@/components/common/StatusBadge";
import { fmt, fmtDateTime } from "@/utils/format";
import { OutreachForm } from "./OutreachForm";
import { OUTREACH_SORT_FIELDS, type OutreachQuery } from "@/types/outreach";

export function OutreachesPage() {
  const [filters, setFilters] = useState<Omit<OutreachQuery, "cursor">>({ sort: "-id", limit: 20 });
  const [draft, setDraft] = useState({ companyName: "", referenceId: "", isRated: "" });
  // Cursor pagination: keep the stack of cursors used to reach the current page
  const [cursors, setCursors] = useState<(string | undefined)[]>([undefined]);
  const [selected, setSelected] = useState<string>();
  const [creating, setCreating] = useState(false);

  const cursor = cursors[cursors.length - 1];
  const query = useOutreaches({ ...filters, cursor });
  const data = query.data;
  useEffect(() => setCursors([undefined]), [filters]);

  const applyFilters = (e: React.FormEvent) => {
    e.preventDefault();
    setFilters((f) => ({
      ...f,
      companyName: draft.companyName.trim() || undefined,
      referenceId: draft.referenceId.trim() || undefined,
      isRated: (draft.isRated || undefined) as OutreachQuery["isRated"],
    }));
  };

  return (
    <>
      <PageHeader
        title="Outreaches"
        description="api.sandbox.rrilabs.co/outreach-requests · documented operations: list, detail, communications, create"
        actions={
          <>
            <button type="button" className="btn-primary" onClick={() => setCreating(true)}>
              <Plus className="h-4 w-4" aria-hidden /> New outreach request
            </button>
            <button type="button" className="btn-secondary" onClick={() => query.refetch()} disabled={query.isFetching}>
              <RefreshCw className={`h-4 w-4 ${query.isFetching ? "animate-spin" : ""}`} aria-hidden /> Refresh
            </button>
          </>
        }
      />
      <DemoBanner />

      <div className="card-flush">
        <form onSubmit={applyFilters} className="toolbar">
          <div className="w-56">
            <label htmlFor="o-name" className="label">
              Counterparty company name
            </label>
            <input id="o-name" className="input" value={draft.companyName} onChange={(e) => setDraft({ ...draft, companyName: e.target.value })} />
          </div>
          <div className="w-44">
            <label htmlFor="o-ref" className="label">
              Reference ID
            </label>
            <input id="o-ref" className="input" value={draft.referenceId} onChange={(e) => setDraft({ ...draft, referenceId: e.target.value })} />
          </div>
          <div className="w-32">
            <label htmlFor="o-rated" className="label">
              Rated
            </label>
            <select id="o-rated" className="input" value={draft.isRated} onChange={(e) => setDraft({ ...draft, isRated: e.target.value })}>
              <option value="">Any</option>
              <option value="true">Rated</option>
              <option value="false">Not rated</option>
            </select>
          </div>
          <div className="w-52">
            <label htmlFor="o-sort" className="label">
              Sort
            </label>
            <select id="o-sort" className="input" value={filters.sort} onChange={(e) => setFilters({ ...filters, sort: e.target.value })}>
              {OUTREACH_SORT_FIELDS.flatMap((f) => [`-${f}`, f]).map((s) => (
                <option key={s} value={s}>
                  {s.startsWith("-") ? `${s.slice(1)} (desc)` : `${s} (asc)`}
                </option>
              ))}
            </select>
          </div>
          <div className="w-24">
            <label htmlFor="o-limit" className="label">
              Page size
            </label>
            <select id="o-limit" className="input" value={filters.limit} onChange={(e) => setFilters({ ...filters, limit: Number(e.target.value) })}>
              {[10, 20, 50, 100].map((n) => (
                <option key={n}>{n}</option>
              ))}
            </select>
          </div>
          <button type="submit" className="btn-secondary">
            Apply
          </button>
          <p className="w-full text-xs text-slate-500">Filters use the documented `q` parameter: partial, case-insensitive, combined with AND.</p>
        </form>

        {query.isLoading ? (
          <LoadingState label="Loading outreach requests…" />
        ) : query.error ? (
          <ErrorState error={query.error} onRetry={() => query.refetch()} />
        ) : !data?.items.length ? (
          <EmptyState
            title="No outreach requests"
            description={filters.companyName || filters.referenceId || filters.isRated ? "No request matches the filters." : "This account has no outreach requests yet (the sandbox account currently returns total: 0)."}
          />
        ) : (
          <>
            <DataTable
              columns={[
                { key: "outreach_request_id", header: "ID", render: (o) => <span className="font-mono text-xs">{fmt(o.outreach_request_id)}</span> },
                { key: "company", header: "Counterparty", render: (o) => <span className="link">{fmt(o.counterparty?.company_name)}</span> },
                { key: "ref", header: "Reference", render: (o) => fmt(o.counterparty?.reference_id) },
                { key: "status", header: "Status", render: (o) => (o.status ? <StatusBadge>{o.status}</StatusBadge> : fmt(undefined)) },
                { key: "is_rated", header: "Rated", render: (o) => (o.is_rated === undefined ? fmt(undefined) : <StatusBadge tone={o.is_rated ? "green" : "slate"}>{o.is_rated ? "Rated" : "Not rated"}</StatusBadge>) },
                { key: "rrid", header: "RRID", render: (o) => <span className="font-mono text-xs">{fmt(o.rrid)}</span> },
                { key: "created_at", header: "Created", render: (o) => fmtDateTime(o.created_at) },
                { key: "modified_at", header: "Modified", render: (o) => fmtDateTime(o.modified_at) },
              ]}
              rows={data.items}
              rowKey={(o) => o.outreach_request_id ?? JSON.stringify(o.counterparty)}
              onRowClick={(o) => o.outreach_request_id && setSelected(o.outreach_request_id)}
              selectedKey={selected}
            />
            <TablePagination
              summary={`Page ${cursors.length} · ${data.total.toLocaleString()} request(s)`}
              hasPrevious={cursors.length > 1}
              hasNext={data.hasNext}
              busy={query.isFetching}
              onPrevious={() => setCursors((c) => c.slice(0, -1))}
              onNext={() => data.nextCursor && setCursors((c) => [...c, data.nextCursor!])}
            />
          </>
        )}
      </div>

      <OutreachDrawer id={selected} onClose={() => setSelected(undefined)} />
      <Drawer open={creating} onClose={() => setCreating(false)} title="New outreach request" subtitle="POST /outreach-requests">
        <OutreachForm
          onCancel={() => setCreating(false)}
          onCreated={(o) => {
            setCreating(false);
            if (o.outreach_request_id) setSelected(o.outreach_request_id);
          }}
        />
      </Drawer>
    </>
  );
}

function OutreachDrawer({ id, onClose }: { id?: string; onClose: () => void }) {
  const { data: o, isLoading, error, refetch } = useOutreach(id);
  const comms = useOutreachCommunications(id);
  return (
    <Drawer open={!!id} onClose={onClose} title={o?.counterparty?.company_name ?? `Request ${id ?? ""}`} subtitle={`GET /outreach-requests/${id}`}>
      {isLoading ? (
        <LoadingState />
      ) : error ? (
        <ErrorState error={error} onRetry={() => refetch()} compact />
      ) : o ? (
        <div className="space-y-5">
          <DetailList
            rows={[
              ["Request ID", <span className="font-mono">{fmt(o.outreach_request_id)}</span>],
              ["Status (display only)", fmt(o.status)],
              ["Rated", fmt(o.is_rated)],
              ["RRID", o.rrid ? <Link className="font-mono text-blue-700 hover:underline" to={`/ratings?rrid=${encodeURIComponent(o.rrid)}`}>{o.rrid}</Link> : fmt(undefined)],
              ["Reference ID", fmt(o.counterparty?.reference_id)],
              ["Contact", `${fmt(o.counterparty?.contact?.first_name)} ${o.counterparty?.contact?.last_name ?? ""} · ${fmt(o.counterparty?.contact?.email)}`],
              ["Country", fmt(o.counterparty?.contact?.country)],
              ["Relationship manager", `${fmt(o.relationship_manager?.name)} · ${fmt(o.relationship_manager?.email)}`],
              ["Created", fmtDateTime(o.created_at)],
              ["Modified", fmtDateTime(o.modified_at)],
              ["Financial submissions", o.financial_submissions?.length ? o.financial_submissions.map((f) => fmtDateTime(f.created_at)).join(", ") : fmt(undefined)],
            ]}
          />
          {o.status_history?.length ? (
            <section>
              <h3 className="mb-2 text-sm font-semibold text-slate-800">Status history</h3>
              <DataTable
                columns={[
                  { key: "status", header: "Status" },
                  { key: "status_detail", header: "Detail" },
                  { key: "status_updated_at", header: "Updated", render: (h) => fmtDateTime(h.status_updated_at) },
                ]}
                rows={o.status_history}
                rowKey={(h) => `${h.status}-${h.status_updated_at}`}
              />
            </section>
          ) : null}
          <section>
            <h3 className="mb-2 text-sm font-semibold text-slate-800">Communication attempts</h3>
            {comms.isLoading ? (
              <LoadingState />
            ) : comms.error ? (
              <ErrorState error={comms.error} compact />
            ) : comms.data?.communications.introductions.length ? (
              <ul className="text-sm text-slate-700">
                {comms.data.communications.introductions.map((c, i) => (
                  <li key={i}>
                    {c.channel} · {fmtDateTime(c.sent_at)}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-slate-500">No communication attempts recorded.</p>
            )}
          </section>
          <JsonViewer data={o} maxHeight="16rem" />
        </div>
      ) : null}
    </Drawer>
  );
}
