import type { ReactNode } from "react";
import { cx } from "@/utils/format";

export type Tone = "green" | "amber" | "red" | "blue" | "slate" | "violet";

const TONES: Record<Tone, string> = {
  green: "bg-emerald-50 text-emerald-700 ring-emerald-300",
  amber: "bg-amber-50 text-amber-700 ring-amber-300",
  red: "bg-red-50 text-red-700 ring-red-300",
  blue: "bg-blue-50 text-blue-700 ring-blue-300",
  slate: "bg-white text-slate-600 ring-slate-300",
  violet: "bg-violet-50 text-violet-700 ring-violet-300",
};

export function StatusBadge({ tone = "slate", children, title }: { tone?: Tone; children: ReactNode; title?: string }) {
  return (
    <span title={title} className={cx("inline-flex items-center gap-1 whitespace-nowrap rounded-sm px-2 py-0.5 text-[11px] font-medium ring-1 ring-inset", TONES[tone])}>
      {children}
    </span>
  );
}
