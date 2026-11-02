import { Box, Building2, Cpu, Factory, LayoutGrid, ListTree, Package } from 'lucide-react';
import { NavLink } from 'react-router-dom';

const NAV_ITEMS = [
  { to: '/dashboards', label: 'Dashboards', icon: LayoutGrid },
  { to: '/item-hierarchy', label: 'Item Hierarchy', icon: ListTree },
  { to: '/items', label: 'Items', icon: Package },
  { to: '/parts', label: 'Parts', icon: Cpu },
  { to: '/sites', label: 'Sites', icon: Factory },
  { to: '/companies', label: 'Companies', icon: Building2 },
];

export function Sidebar() {
  return (
    <aside className="flex w-[346px] shrink-0 flex-col border-r border-gray-200 bg-gray-50">
      <div className="flex h-[68px] items-center gap-3 border-b border-gray-200 px-5">
        <span className="flex size-[34px] items-center justify-center rounded-lg bg-gradient-to-br from-blue-500 to-indigo-600 text-white shadow-sm">
          <Box className="size-5" aria-hidden />
        </span>
        <span className="text-[18px] font-bold text-gray-900">Demo-Application</span>
      </div>

      <nav aria-label="Main" className="flex flex-col gap-1 p-2.5 pt-4">
        {NAV_ITEMS.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              `flex items-center gap-3.5 rounded-lg px-3.5 py-3 text-[18px] text-gray-800 transition-colors ${
                isActive ? 'bg-gray-200/60 font-semibold' : 'hover:bg-gray-100'
              }`
            }
          >
            <Icon className="size-[22px]" strokeWidth={1.75} aria-hidden />
            {label}
          </NavLink>
        ))}
      </nav>
    </aside>
  );
}
