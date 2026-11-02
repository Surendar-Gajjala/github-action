import type { ReactNode } from "react";
import { FlaskConical } from "lucide-react";
import { getAuthMode } from "@/services/config";

export function PageHeader({ title, description, actions }: { title: string; description?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="page-header -mx-4 -mt-6 mb-6 flex flex-wrap items-start justify-between gap-3 border-b border-slate-200 px-4 py-7 sm:-mx-8 sm:px-8">
      <div className="min-w-0">
        <h1 className="text-2xl font-normal tracking-tight text-slate-900">{title}</h1>
        {description && <p className="mt-2 text-sm text-slate-500">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

/** Shown on every data page in mock mode so demo data can never be mistaken for provider data. */
export function DemoBanner() {
  if (getAuthMode() !== "mock") return null;
  return (
    <div className="mb-4 flex items-start gap-2 rounded-md border border-violet-200 bg-violet-50 px-3 py-2 text-sm text-violet-800">
      <FlaskConical className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
      <span>
        <strong>Demo data.</strong> Fictional, generated records that follow the RapidRatings response structure. Not provider data. Switch the auth mode in Settings to use the real API.
      </span>
    </div>
  );
}
