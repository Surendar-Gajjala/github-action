import { Navigate, Route, Routes } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { LoadingState } from './components/States';
import { useAuth } from './auth/AuthContext';
import { LoginPage } from './auth/LoginPage';
import { ItemsPage } from './features/items/ItemsPage';
import { PartsPage } from './features/parts/PartsPage';
import { WorkflowPage } from './features/workflow/WorkflowPage';

export default function App() {
  const { user, initializing } = useAuth();
  if (initializing) return <LoadingState label="Signing in…" />;
  if (!user) return <LoginPage />;
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/" element={<Navigate to="/items" replace />} />
        <Route path="/items/:itemId?" element={<ItemsPage />} />
        <Route path="/parts/:partId?" element={<PartsPage />} />
        <Route path="/workflow/:section?/:key?" element={<WorkflowPage />} />
        <Route path="*" element={<Navigate to="/items" replace />} />
      </Route>
    </Routes>
  );
}
