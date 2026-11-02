import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { Activity, Building2, CalendarRange, Clock, FolderKanban, Star } from "lucide-react";
import { useCompanies } from "@/hooks/useCompanies";
import { usePortfolios } from "@/hooks/usePortfolios";
import { connectionStore } from "@/services/rapidRatingsAuthService";
import { apiActivity } from "@/services/apiActivity";
import { syncStore } from "@/services/syncService";
import { getAuthMode } from "@/services/config";
import { useStore } from "@/utils/store";
import { CONNECTION_LABEL } from "@/components/common/ConnectionIndicator";
import { DemoBanner, PageHeader } from "@/components/common/PageHeader";
import { StatusBadge } from "@/components/common/StatusBadge";
import { InlineSpinner } from "@/components/common/LoadingState";
import { normaliseError } from "@/services/httpClient";
import { fmtDateTime, relativeTime } from "@/utils/format";
import type { ResourceKey } from "@/types/schema";

export function OverviewPage() {
  const status = useStore(connectionStore);
  const activity = useStore(apiActivity);
  const { runs } = useStore(syncStore);
  const live = status.state === "connected" || status.state === "partial" || status.state === "demo";
  const companies = useCompanies({ page: 1 }, live);
  const portfolios = usePortfolios(1);
  const demo = getAuthMode() === "mock";

  const lastRun = (r: ResourceKey) => runs.find((x) => x.resource === r && (x.status === "succeeded" || x.status === "partial") && x.mode === getAuthMode());
  const lastSuccess = [activity.data.lastSuccessAt, activity.outreach.lastSuccessAt].filter(Boolean).sort().pop();
  const [label, tone] = CONNECTION_LABEL[status.state];

  const countCard = (q: { data?: { total: number }; isLoading: boolean; error: unknown }) =>
    q.isLoading ? <InlineSpinner /> : q.error ? <span className="text-sm text-red-700">{normaliseError(q.error).message}</span> : q.data ? q.data.total.toLocaleString() : <NotRetrieved />;

  const syncCard = (r: ResourceKey) => {
    const run = lastRun(r);
    return run ? (
      <>
        {run.recordsRetrieved.toLocaleString()}
        <p className="mt-1 text-xs font-normal text-slate-500">
          {run.scope} · last sync {relativeTime(run.endedAt)}
          {run.status === "partial" ? " (partial)" : ""}
        </p>
      </>
    ) : (
      <NotRetrieved hint={<Link to="/sync-history" className="text-blue-700 hover:underline">Run a sync</Link>} />
    );
  };

  return (
    <>
      <PageHeader title="Overview" description="RapidRatings integration status. All figures come from real API responses in the current mode." />
      <DemoBanner />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        <Card icon={<Building2 />} title="Companies retrieved" note="`count` from GET /companies/" to="/companies">
          {countCard(companies)}
        </Card>
        <Card icon={<FolderKanban />} title="Portfolios retrieved" note="`count` from GET /portfolios/" to="/portfolios">
          {countCard(portfolios)}
        </Card>
        <Card icon={<CalendarRange />} title="Reporting periods retrieved" note="From the last manual sync of all companies" to="/periods">
          {syncCard("periods")}
        </Card>
        <Card icon={<Star />} title="Ratings retrieved" note="From the last manual sync of all companies" to="/ratings">
          {syncCard("ratings")}
        </Card>
        <Card icon={<Clock />} title="Last successful API request" note={demo ? "Demo mode: requests are simulated" : "Any real request in this browser session"}>
          {lastSuccess ? (
            <>
              {relativeTime(lastSuccess)}
              <p className="mt-1 text-xs font-normal text-slate-500">{fmtDateTime(lastSuccess)}</p>
            </>
          ) : (
            <NotRetrieved hint="No request has succeeded yet in this session." />
          )}
        </Card>
        <Card icon={<Activity />} title="API connection status" note={status.checkedAt ? `Checked ${relativeTime(status.checkedAt)}` : "Not checked"} to="/settings">
          <StatusBadge tone={tone}>{label}</StatusBadge>
          <p className="mt-2 text-xs font-normal text-slate-600">{status.message}</p>
        </Card>
      </div>

      {!live && status.state !== "checking" && (
        <p className="mt-4 text-sm text-slate-500">
          Counts load once the API connection succeeds. Configure it in <Link to="/settings" className="text-blue-700 hover:underline">Settings</Link>.
        </p>
      )}
    </>
  );
}

function Card({ icon, title, note, to, children }: { icon: ReactNode; title: string; note: string; to?: string; children: ReactNode }) {
  const body = (
    <div className="card h-full p-4 transition-colors hover:border-slate-300">
      <div className="mb-3 flex items-center gap-2 text-slate-500">
        <span className="h-4 w-4 [&>svg]:h-4 [&>svg]:w-4">{icon}</span>
        <span className="text-sm font-medium">{title}</span>
      </div>
      <div className="text-2xl font-semibold text-slate-900">{children}</div>
      <p className="mt-2 text-[11px] text-slate-400">{note}</p>
    </div>
  );
  return to ? (
    <Link to={to} className="block focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500">
      {body}
    </Link>
  ) : (
    body
  );
}

function NotRetrieved({ hint }: { hint?: ReactNode }) {
  return (
    <span className="text-base font-normal text-slate-400">
      Not retrieved yet
      {hint && <span className="mt-1 block text-xs">{hint}</span>}
    </span>
  );
}
