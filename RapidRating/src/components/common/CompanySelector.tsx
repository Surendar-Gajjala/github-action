import { useMemo, useState } from "react";
import { useAllCompanies } from "@/hooks/useCompanies";
import { InlineSpinner } from "./LoadingState";
import { normaliseError } from "@/services/httpClient";

/** Searchable company picker. The value is the provider's RRID (Company.id). */
export function CompanySelector({ value, onChange, id = "company-selector" }: { value?: string; onChange: (rrid: string | undefined) => void; id?: string }) {
  const { data, isLoading, error } = useAllCompanies();
  const [filter, setFilter] = useState("");
  const options = useMemo(() => {
    const f = filter.trim().toLowerCase();
    return (data ?? []).filter((c) => !f || c.name?.toLowerCase().includes(f) || c.id.toLowerCase().includes(f));
  }, [data, filter]);

  return (
    <div className="flex flex-wrap items-end gap-2">
      <div className="w-56">
        <label htmlFor={`${id}-filter`} className="label">
          Filter companies
        </label>
        <input id={`${id}-filter`} className="input" placeholder="Name or RRID" value={filter} onChange={(e) => setFilter(e.target.value)} />
      </div>
      <div className="w-80">
        <label htmlFor={id} className="label">
          Company {isLoading && <InlineSpinner />}
        </label>
        <select id={id} className="input" value={value ?? ""} onChange={(e) => onChange(e.target.value || undefined)} disabled={isLoading || !!error}>
          <option value="">{isLoading ? "Loading companies…" : "Select a company"}</option>
          {value && !options.some((o) => o.id === value) && <option value={value}>{value}</option>}
          {options.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name} ({c.id})
            </option>
          ))}
        </select>
      </div>
      <div className="w-40">
        <label htmlFor={`${id}-rrid`} className="label">
          RRID
        </label>
        <input id={`${id}-rrid`} className="input font-mono" value={value ?? ""} placeholder="e.g. XAZPzdJ" onChange={(e) => onChange(e.target.value.trim() || undefined)} />
      </div>
      {error && <p className="w-full text-xs text-red-600">Could not load the company list: {normaliseError(error).message}. You can still enter an RRID.</p>}
    </div>
  );
}
