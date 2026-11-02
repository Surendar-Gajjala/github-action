import { useEffect } from "react";
import { Link } from "react-router-dom";
import { authService, connectionStore, type ConnectionState } from "@/services/rapidRatingsAuthService";
import { useStore } from "@/utils/store";
import { StatusBadge, type Tone } from "./StatusBadge";

export const CONNECTION_LABEL: Record<ConnectionState, [label: string, tone: Tone]> = {
  demo: ["Demo mode", "violet"],
  checking: ["Checking…", "slate"],
  connected: ["Connected", "green"],
  partial: ["Partially connected", "amber"],
  disconnected: ["Not connected", "red"],
  "not-configured": ["Not configured", "amber"],
  unknown: ["Not checked", "slate"],
};

export function ConnectionIndicator() {
  const status = useStore(connectionStore);
  useEffect(() => {
    void authService.checkConnection();
  }, []);
  const [label, tone] = CONNECTION_LABEL[status.state];
  return (
    <Link to="/settings" title={status.message} className="flex items-center gap-2">
      <span className="hidden text-xs text-slate-500 sm:inline">Mode: {status.mode}</span>
      <StatusBadge tone={tone}>
        <span className={`h-1.5 w-1.5 rounded-full ${tone === "green" ? "bg-emerald-500" : tone === "red" ? "bg-red-500" : tone === "amber" ? "bg-amber-500" : tone === "violet" ? "bg-violet-500" : "bg-slate-400"}`} />
        {label}
      </StatusBadge>
    </Link>
  );
}
