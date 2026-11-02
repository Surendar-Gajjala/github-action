import { Link, useLocation } from "react-router-dom";
import { ChevronRight } from "lucide-react";
import { NAV } from "./Sidebar";

export function Breadcrumbs() {
  const { pathname } = useLocation();
  const item = NAV.find((n) => "to" in n && n.to !== "/" && pathname.startsWith(n.to));
  return (
    <nav aria-label="Breadcrumb" className="flex min-w-0 items-center gap-1 text-sm text-slate-500">
      <Link to="/" className="hover:text-slate-800">
        Data Integrations
      </Link>
      <ChevronRight className="h-3.5 w-3.5 shrink-0" aria-hidden />
      <span className={item ? "hover:text-slate-800" : "font-medium text-slate-800"}>RapidRatings</span>
      {item && "label" in item && (
        <>
          <ChevronRight className="h-3.5 w-3.5 shrink-0" aria-hidden />
          <span className="truncate font-medium text-slate-800" aria-current="page">
            {item.label}
          </span>
        </>
      )}
    </nav>
  );
}
