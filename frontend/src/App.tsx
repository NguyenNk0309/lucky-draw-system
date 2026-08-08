import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth';
import { Layout } from './components/Layout';
import { ProtectedRoute } from './components/ProtectedRoute';
import { AnalyticsPage } from './pages/AnalyticsPage';
import { CampaignsPage } from './pages/CampaignsPage';
import { LoginPage } from './pages/LoginPage';
import { LuckyDrawPage } from './pages/LuckyDrawPage';
import { ShopPage } from './pages/ShopPage';

function Home() {
  const { session } = useAuth();
  return (
    <Navigate
      to={session?.role === 'SELLER' ? '/campaigns' : '/shop'}
      replace
    />
  );
}

export function App() {
  return (
    <Routes>
      <Route path="login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<Layout />}>
          <Route index element={<Home />} />
          <Route element={<ProtectedRoute role="CUSTOMER" />}>
            <Route path="shop" element={<ShopPage />} />
          </Route>
          <Route path="lucky-draw" element={<LuckyDrawPage />} />
          <Route element={<ProtectedRoute role="SELLER" />}>
            <Route path="campaigns" element={<CampaignsPage />} />
            <Route path="analytics" element={<AnalyticsPage />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
