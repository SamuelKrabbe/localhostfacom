import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import { StateView } from '../../components/StateView';

export function RequireAuth() {
  const { admin, isResolving } = useAuth();
  const location = useLocation();

  if (isResolving) {
    return <StateView kind="loading" message="verificando sessão..." />;
  }

  if (!admin) {
    return <Navigate to="/admin/login" state={{ from: location.pathname }} replace />;
  }

  return <Outlet />;
}
