import { useState } from "react";
import { ChevronDown, ChevronRight, Copy } from "lucide-react";

/** Collapsible JSON tree for inspecting nested objects/arrays. Renders data held in memory only. */
export function JsonViewer({ data, maxHeight = "28rem" }: { data: unknown; maxHeight?: string }) {
  return (
    <div className="relative rounded-md border border-slate-200 bg-slate-50">
      <button
        type="button"
        className="btn-ghost absolute right-1 top-1 px-2 py-1 text-xs"
        onClick={() => navigator.clipboard?.writeText(JSON.stringify(data, null, 2)).catch(() => undefined)}
      >
        <Copy className="h-3 w-3" aria-hidden /> Copy
      </button>
      <div className="overflow-auto p-3 font-mono text-xs leading-5" style={{ maxHeight }}>
        <Node value={data} depth={0} />
      </div>
    </div>
  );
}

function Node({ name, value, depth }: { name?: string; value: unknown; depth: number }) {
  const [open, setOpen] = useState(depth < 2);
  const isObj = value !== null && typeof value === "object";
  const label = name !== undefined ? <span className="text-violet-700">{JSON.stringify(name)}: </span> : null;

  if (!isObj) {
    const cls = value === null ? "text-slate-400" : typeof value === "string" ? "text-emerald-700" : typeof value === "number" ? "text-blue-700" : "text-amber-700";
    return (
      <div style={{ paddingLeft: depth ? 14 : 0 }}>
        {label}
        <span className={cls}>{JSON.stringify(value)}</span>
      </div>
    );
  }

  const entries = Array.isArray(value) ? value.map((v, i) => [String(i), v] as const) : Object.entries(value as object);
  const [o, c] = Array.isArray(value) ? ["[", "]"] : ["{", "}"];
  return (
    <div style={{ paddingLeft: depth ? 14 : 0 }}>
      <button type="button" className="inline-flex items-center text-left hover:bg-slate-200/60" onClick={() => setOpen(!open)} aria-expanded={open}>
        {open ? <ChevronDown className="h-3 w-3" aria-hidden /> : <ChevronRight className="h-3 w-3" aria-hidden />}
        {label}
        <span className="text-slate-500">
          {o}
          {!open && ` ${entries.length} ${Array.isArray(value) ? "items" : "keys"} ${c}`}
        </span>
      </button>
      {open && (
        <>
          {entries.map(([k, v]) => (
            <Node key={k} name={Array.isArray(value) ? undefined : k} value={v} depth={depth + 1} />
          ))}
          <div className="text-slate-500">{c}</div>
        </>
      )}
    </div>
  );
}
