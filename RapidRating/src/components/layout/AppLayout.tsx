import { useState } from "react";
import { Outlet } from "react-router-dom";
import { Sidebar } from "./Sidebar";
import { Header } from "./Header";

export function AppLayout() {
  const [navOpen, setNavOpen] = useState(false);
  return (
    <div className="min-h-screen">
      <Sidebar open={navOpen} onNavigate={() => setNavOpen(false)} />
      {navOpen && <div className="fixed inset-0 z-20 bg-slate-900/30 lg:hidden" onClick={() => setNavOpen(false)} aria-hidden />}
      <div className="lg:pl-64">
        <Header onMenu={() => setNavOpen(true)} />
        <main className="mx-auto max-w-[1600px] px-4 py-6 sm:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
