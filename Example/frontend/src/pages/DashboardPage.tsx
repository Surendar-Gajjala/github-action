import { Cpu, Factory, ListTree, Package } from 'lucide-react';

import { PageHeader } from '../components/PageHeader';
import { StatTile } from '../components/dashboard/StatTile';
import { useHierarchyProducts } from '../hooks/useItemHierarchy';
import { useItems } from '../hooks/useItems';
import { useParts } from '../hooks/useParts';
import { useSites } from '../hooks/useSites';

/**
 * Overview of the hosted data. Each count reuses a list endpoint with a one-row page
 * and reads its total, so each tile costs one small hosted call. Top-level BOMs uses
 * the anchors-only products endpoint, so it never runs the graph traversal.
 */
export function DashboardPage() {
  const items = useItems(0, 1);
  const parts = useParts(0, 1);
  const sites = useSites(0, 1);
  const boms = useHierarchyProducts(0, 1);

  return (
    <div className="flex h-full flex-col">
      <PageHeader title="Dashboards" subtitle="Totals from the hosted data, with links to each list." />

      <div className="flex-1 overflow-auto bg-gray-50 px-10 py-8">
        <section aria-label="Totals" className="grid max-w-6xl grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-4">
          <StatTile label="Top-level BOMs" to="/item-hierarchy" icon={ListTree} query={boms} linkText="View item hierarchy" />
          <StatTile label="Items" to="/items" icon={Package} query={items} />
          <StatTile label="Parts" to="/parts" icon={Cpu} query={parts} />
          <StatTile label="Sites" to="/sites" icon={Factory} query={sites} />
        </section>
      </div>
    </div>
  );
}
