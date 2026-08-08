import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../auth';
import type { Role } from '../types';

export function ProtectedRoute({ role }: { role?: Role }) {
  const { session } = useAuth();
  if (!session) return <Navigate to="/login" replace />;
  if (role && session.role !== role) return <Navigate to="/" replace />;
  return <Outlet />;
}
