import { createBrowserRouter } from "react-router-dom";
import { AppLayout } from "@/components/layout/AppLayout";
import { OverviewPage } from "@/features/overview/OverviewPage";
import { CompaniesPage } from "@/features/companies/CompaniesPage";
import { PortfoliosPage } from "@/features/portfolios/PortfoliosPage";
import { PeriodsPage } from "@/features/periods/PeriodsPage";
import { RatingsPage } from "@/features/ratings/RatingsPage";
import { OutreachesPage } from "@/features/outreaches/OutreachesPage";
import { SchemaExplorerPage } from "@/features/schema-explorer/SchemaExplorerPage";
import { SchemaMappingPage } from "@/features/schema-mapping/SchemaMappingPage";
import { SyncHistoryPage } from "@/features/sync-history/SyncHistoryPage";
import { SettingsPage } from "@/features/settings/SettingsPage";
import { EmptyState } from "@/components/common/EmptyState";

export const router = createBrowserRouter([
  {
    element: <AppLayout />,
    children: [
      { index: true, element: <OverviewPage /> },
      { path: "companies", element: <CompaniesPage /> },
      { path: "portfolios", element: <PortfoliosPage /> },
      { path: "periods", element: <PeriodsPage /> },
      { path: "ratings", element: <RatingsPage /> },
      { path: "outreaches", element: <OutreachesPage /> },
      { path: "schema-explorer", element: <SchemaExplorerPage /> },
      { path: "schema-mapping", element: <SchemaMappingPage /> },
      { path: "sync-history", element: <SyncHistoryPage /> },
      { path: "settings", element: <SettingsPage /> },
      { path: "*", element: <EmptyState title="Page not found" description="Use the sidebar to navigate." /> },
    ],
  },
]);
