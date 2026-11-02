import { NavLink } from "react-router-dom";
import { Box, Building2, CalendarRange, Database, FolderKanban, GitCompareArrows, History, LayoutDashboard, Send, Settings, Star } from "lucide-react";
import { cx } from "@/utils/format";

export const NAV = [
  { to: "/", label: "Overview", icon: LayoutDashboard, end: true },
  { section: "RapidRatings" },
  { to: "/companies", label: "Companies", icon: Building2 },
  { to: "/portfolios", label: "Portfolios", icon: FolderKanban },
  { to: "/periods", label: "Periods", icon: CalendarRange },
  { to: "/ratings", label: "Ratings", icon: Star },
  { to: "/outreaches", label: "Outreaches", icon: Send },
  { section: "Integration" },
  { to: "/schema-explorer", label: "Schema Explorer", icon: Database },
  { to: "/schema-mapping", label: "Schema Mapping", icon: GitCompareArrows },
  { to: "/sync-history", label: "Sync History", icon: History },
  { to: "/settings", label: "Settings", icon: Settings },
] as const;

export function Sidebar({ open, onNavigate }: { open: boolean; onNavigate: () => void }) {
  return (
    <aside
      className={cx(
        "fixed inset-y-0 left-0 z-30 flex w-64 flex-col border-r border-slate-200 bg-white transition-transform lg:translate-x-0",
        open ? "translate-x-0" : "-translate-x-full",
      )}
      aria-label="Main navigation"
    >
      <div className="flex h-14 items-center gap-2.5 border-b border-slate-200 px-4">
        <span className="flex h-7 w-7 items-center justify-center rounded-md bg-blue-600 text-white">
          <Box className="h-4 w-4" aria-hidden />
        </span>
        <div className="leading-tight">
          <div className="text-[15px] font-semibold text-slate-900">Data Integrations</div>
          <div className="text-[11px] text-slate-500">Provider: RapidRatings</div>
        </div>
      </div>
      <nav className="flex-1 space-y-0.5 overflow-y-auto px-2 py-4">
        {NAV.map((item, i) =>
          "section" in item ? (
            <div key={i} className="mb-1 mt-5 px-3 text-[11px] font-medium uppercase tracking-wider text-slate-400">
              {item.section}
            </div>
          ) : (
            <NavLink
              key={item.to}
              to={item.to}
              end={"end" in item ? item.end : false}
              onClick={onNavigate}
              className={({ isActive }) =>
                cx("flex items-center gap-3 rounded-md px-3 py-2.5 text-[15px] text-slate-700", isActive ? "bg-slate-100 font-medium text-slate-900" : "hover:bg-slate-50 hover:text-slate-900")
              }
            >
              <item.icon className="h-[18px] w-[18px] shrink-0" strokeWidth={1.75} aria-hidden />
              {item.label}
            </NavLink>
          ),
        )}
      </nav>
    </aside>
  );
}
