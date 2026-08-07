import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './auth/AuthProvider';
import { PublicLayout } from './components/PublicLayout';
import { CartProvider } from './cart/CartProvider';
import { Catalog } from './pages/Catalog';
import { OrderConfirmation } from './pages/OrderConfirmation';
import { PixPayment } from './pages/PixPayment';
import { PublicDashboard } from './pages/PublicDashboard';
import { AdminExpenses } from './pages/admin/AdminExpenses';
import { AdminLayout } from './pages/admin/AdminLayout';
import { AdminLogin } from './pages/admin/AdminLogin';
import { AdminOrders } from './pages/admin/AdminOrders';
import { AdminProducts } from './pages/admin/AdminProducts';
import { AdminUsers } from './pages/admin/AdminUsers';
import { RequireAuth } from './pages/admin/RequireAuth';

function App() {
  return (
    <AuthProvider>
      <CartProvider>
        <BrowserRouter>
          <Routes>
            <Route element={<PublicLayout />}>
              <Route path="/" element={<PublicDashboard />} />
              <Route path="/cardapio" element={<Catalog />} />
              <Route path="/pagamento/:orderId" element={<PixPayment />} />
              <Route path="/confirmacao/:orderId" element={<OrderConfirmation />} />
            </Route>

            <Route path="/admin/login" element={<AdminLogin />} />
            <Route element={<RequireAuth />}>
              <Route path="/admin" element={<AdminLayout />}>
                <Route index element={<Navigate to="/admin/pedidos" replace />} />
                <Route path="pedidos" element={<AdminOrders />} />
                <Route path="produtos" element={<AdminProducts />} />
                <Route path="despesas" element={<AdminExpenses />} />
                <Route path="usuarios" element={<AdminUsers />} />
              </Route>
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </CartProvider>
    </AuthProvider>
  );
}

export default App;
