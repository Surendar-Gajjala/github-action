import { useMemo, useState } from "react";
import { useFieldArray, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, KeyRound, PlugZap, Save } from "lucide-react";
import { authService, connectionStore } from "@/services/rapidRatingsAuthService";
import { config, getAuthMode, hasAuthModeOverride, setAuthModeOverride } from "@/services/config";
import { syncStore } from "@/services/syncService";
import { CONNECTION_LABEL } from "@/components/common/ConnectionIndicator";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusBadge } from "@/components/common/StatusBadge";
import { DetailList } from "@/components/common/Drawer";
import { useStore } from "@/utils/store";
import { fmtDateTime } from "@/utils/format";
import { loadSchedule, nextPlannedRun, saveSchedule, scheduleSchema, type ScheduleConfig } from "./schedule";
import type { AuthMode } from "@/types/api";

const MODE_HELP: Record<AuthMode, string> = {
  mock: "Synthetic demo data generated in the browser. No network calls.",
  proxy: "The Vite dev server obtains the OAuth token on your machine using RR_CLIENT_ID / RR_CLIENT_SECRET from .env (never bundled) and forwards requests. Development only (`npm run dev`).",
  direct: "The browser calls the provider directly with an access token you paste below (memory only). The rridata host does not allow browser CORS from this origin, so only Outreach will work.",
};

export function SettingsPage() {
  return (
    <>
      <PageHeader title="Settings" description="API connection and sync schedule configuration." />
      <div className="space-y-6">
        <ConnectionSettings />
        <ScheduleSettings />
      </div>
    </>
  );
}

function ConnectionSettings() {
  const qc = useQueryClient();
  const status = useStore(connectionStore);
  const [mode, setMode] = useState<AuthMode>(getAuthMode());
  const [token, setToken] = useState("");
  const [expires, setExpires] = useState("1800");
  const [, force] = useState(0);

  const applyMode = async (m: AuthMode) => {
    setMode(m);
    setAuthModeOverride(m === config.envAuthMode ? null : m);
    qc.clear(); // never mix data from different modes
    await authService.checkConnection();
  };

  const applyToken = async () => {
    authService.setRuntimeToken(token, Number(expires) || undefined);
    setToken(""); // don't keep the token in component state
    qc.clear();
    force((n) => n + 1);
    await authService.checkConnection();
  };

  const [label, tone] = CONNECTION_LABEL[status.state];

  return (
    <section className="card p-4">
      <h2 className="mb-3 flex items-center gap-2 text-base font-semibold text-slate-900">
        <PlugZap className="h-4 w-4" aria-hidden /> API connection
      </h2>

      <fieldset className="mb-4">
        <legend className="label">Authentication mode</legend>
        <div className="grid gap-2 md:grid-cols-3">
          {(["mock", "proxy", "direct"] as AuthMode[]).map((m) => (
            <label key={m} className={`cursor-pointer rounded-md border p-3 text-sm ${mode === m ? "border-blue-500 bg-blue-50" : "border-slate-200 hover:bg-slate-50"}`}>
              <div className="flex items-center gap-2">
                <input type="radio" name="auth-mode" value={m} checked={mode === m} onChange={() => applyMode(m)} />
                <span className="font-medium capitalize">{m}</span>
                {m === config.envAuthMode && <StatusBadge tone="slate">.env default</StatusBadge>}
              </div>
              <p className="mt-1 text-xs text-slate-500">{MODE_HELP[m]}</p>
            </label>
          ))}
        </div>
        {hasAuthModeOverride() && <p className="mt-1 text-xs text-slate-500">Overriding VITE_RAPIDRATINGS_AUTH_MODE={config.envAuthMode} for this browser.</p>}
      </fieldset>

      {mode === "direct" && (
        <div className="mb-4 rounded-md border border-slate-200 p-3">
          <h3 className="mb-1 flex items-center gap-2 text-sm font-semibold text-slate-800">
            <KeyRound className="h-4 w-4" aria-hidden /> Runtime access token
          </h3>
          <p className="mb-2 text-xs text-slate-500">
            Obtain a token out-of-band (see docs/RapidRatings_API_Reference.md §3). It's held in memory only (lost on reload) and never logged. Tokens last 1800 s and there is no refresh token. The client secret is never entered here.
          </p>
          <div className="flex flex-wrap items-end gap-2">
            <div className="w-96">
              <label htmlFor="rt-token" className="label">
                Access token
              </label>
              <input id="rt-token" type="password" autoComplete="off" className="input font-mono" value={token} onChange={(e) => setToken(e.target.value)} />
            </div>
            <div className="w-28">
              <label htmlFor="rt-exp" className="label">
                Expires in (s)
              </label>
              <input id="rt-exp" className="input" inputMode="numeric" value={expires} onChange={(e) => setExpires(e.target.value)} />
            </div>
            <button type="button" className="btn-primary" disabled={!token.trim()} onClick={applyToken}>
              Use token
            </button>
            {authService.hasRuntimeToken() && (
              <button type="button" className="btn-secondary" onClick={() => (authService.clearRuntimeToken(), qc.clear(), force((n) => n + 1), authService.checkConnection())}>
                Clear token
              </button>
            )}
          </div>
          <p className="mt-2 text-xs">{authService.hasRuntimeToken() ? `Token loaded${authService.runtimeTokenExpiresAt() ? `, expires ${fmtDateTime(authService.runtimeTokenExpiresAt())}` : ""}.` : "No token loaded."}</p>
        </div>
      )}

      <div className="flex flex-wrap items-start justify-between gap-4">
        <DetailList
          rows={[
            ["Status", <StatusBadge tone={tone}>{label}</StatusBadge>],
            ["Details", status.message],
            ["Last checked", fmtDateTime(status.checkedAt)],
            ["Token expires", fmtDateTime(status.tokenExpiresAt)],
            ["Data API", status.hosts ? `${status.hosts.data.ok ? "OK" : "Failed"}: ${status.hosts.data.message}` : "—"],
            ["Outreach API", status.hosts ? `${status.hosts.outreach.ok ? "OK" : "Failed"}: ${status.hosts.outreach.message}` : "—"],
            ["Data API URL", <code className="text-xs">{config.dataApiUrl || "not set"}</code>],
            ["Outreach API URL", <code className="text-xs">{config.outreachApiUrl || "not set"}</code>],
            ["Auth URL", <code className="text-xs">{config.authUrl || "not set"}</code>],
          ]}
        />
        <button type="button" className="btn-secondary" onClick={() => authService.checkConnection(true)} disabled={status.state === "checking"}>
          Test connection
        </button>
      </div>
    </section>
  );
}

function ScheduleSettings() {
  const { runs } = useStore(syncStore);
  const [saved, setSaved] = useState(false);
  const timezones = useMemo(() => {
    try {
      return (Intl as unknown as { supportedValuesOf: (k: string) => string[] }).supportedValuesOf("timeZone");
    } catch {
      return ["UTC"];
    }
  }, []);

  const {
    register,
    control,
    handleSubmit,
    watch,
    formState: { errors, isDirty },
    reset,
  } = useForm<ScheduleConfig>({ resolver: zodResolver(scheduleSchema), defaultValues: loadSchedule() });
  const { fields } = useFieldArray({ control, name: "entries" });
  const values = watch("entries");

  const onSubmit = handleSubmit((v) => {
    saveSchedule(v);
    reset(v);
    setSaved(true);
  });

  return (
    <section className="card p-4">
      <h2 className="mb-2 text-base font-semibold text-slate-900">Scheduled sync configuration</h2>
      <div className="mb-4 flex gap-2 rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900">
        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
        <span>
          <strong>Configuration only. Nothing runs on this schedule.</strong> Reliable background scheduling needs a backend scheduler or external job runner (e.g. cron, a cloud scheduler, or an integration platform). Browser timers stop when the tab closes, so they're
          deliberately not used. Settings are saved in this browser as non-sensitive demo configuration.
        </span>
      </div>

      <datalist id="tz-list">
        {timezones.map((tz) => (
          <option key={tz} value={tz} />
        ))}
      </datalist>

      <form onSubmit={onSubmit} noValidate>
        <div className="overflow-x-auto">
          <table className="min-w-full border-separate border-spacing-0">
            <thead>
              <tr>
                {["Resource", "Enabled", "Frequency", "Timezone", "Preferred time", "Day (weekly)", "Last run", "Next planned run"].map((h) => (
                  <th key={h} scope="col" className="th">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {fields.map((f, i) => {
                const v = values?.[i];
                const err = errors.entries?.[i];
                const last = runs.find((r) => r.resource === f.resource && r.status !== "running");
                const next = v && scheduleSchema.shape.entries.element.safeParse(v).success ? nextPlannedRun(v) : undefined;
                return (
                  <tr key={f.id}>
                    <td className="td font-medium capitalize">{f.resource}</td>
                    <td className="td">
                      <input type="checkbox" aria-label={`Enable ${f.resource} schedule`} {...register(`entries.${i}.enabled`)} />
                    </td>
                    <td className="td">
                      <select className="input py-1 text-xs" aria-label={`${f.resource} frequency`} {...register(`entries.${i}.frequency`)}>
                        <option value="hourly">Hourly</option>
                        <option value="daily">Daily</option>
                        <option value="weekly">Weekly</option>
                      </select>
                    </td>
                    <td className="td">
                      <input className="input w-48 py-1 text-xs" list="tz-list" aria-label={`${f.resource} timezone`} {...register(`entries.${i}.timezone`)} />
                      {err?.timezone && <p className="field-error">{err.timezone.message}</p>}
                    </td>
                    <td className="td">
                      <input type="time" className="input w-28 py-1 text-xs" aria-label={`${f.resource} preferred time`} {...register(`entries.${i}.time`)} />
                      {v?.frequency === "hourly" && <p className="text-[11px] text-slate-400">minute used</p>}
                      {err?.time && <p className="field-error">{err.time.message}</p>}
                    </td>
                    <td className="td">
                      <select className="input py-1 text-xs" aria-label={`${f.resource} day of week`} disabled={v?.frequency !== "weekly"} {...register(`entries.${i}.dayOfWeek`)}>
                        {["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"].map((d, n) => (
                          <option key={d} value={n}>
                            {d}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td className="td text-xs">{last ? `${fmtDateTime(last.endedAt)} (${last.status}, manual)` : "Never"}</td>
                    <td className="td text-xs">{next ? `${next.toLocaleString()} (planned, not executed)` : v?.enabled ? "—" : "Disabled"}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        <div className="mt-3 flex items-center gap-3">
          <button type="submit" className="btn-primary" disabled={!isDirty}>
            <Save className="h-4 w-4" aria-hidden /> Save schedule
          </button>
          {saved && !isDirty && <span className="text-sm text-emerald-700">Saved in this browser.</span>}
        </div>
      </form>
    </section>
  );
}
