import { useIsFetching, useQueryClient } from "@tanstack/react-query";
import { Menu, RefreshCw } from "lucide-react";
import { Breadcrumbs } from "./Breadcrumbs";
import { ConnectionIndicator } from "@/components/common/ConnectionIndicator";

export function Header({ onMenu }: { onMenu: () => void }) {
  const qc = useQueryClient();
  const fetching = useIsFetching();
  return (
    <header className="sticky top-0 z-20 flex h-14 items-center gap-3 border-b border-slate-200 bg-white/95 px-4 backdrop-blur sm:px-8">
      <button type="button" className="btn-ghost p-1.5 lg:hidden" onClick={onMenu} aria-label="Open navigation">
        <Menu className="h-5 w-5" />
      </button>
      <div className="min-w-0 flex-1">
        <Breadcrumbs />
      </div>
      <button type="button" className="btn-ghost" onClick={() => qc.invalidateQueries()} title="Reload all data on this page">
        <RefreshCw className={`h-4 w-4 ${fetching ? "animate-spin" : ""}`} aria-hidden />
        <span className="hidden sm:inline">Reload</span>
      </button>
      <ConnectionIndicator />
    </header>
  );
}
