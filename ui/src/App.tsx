import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AppHeader } from './components/AppHeader';
import { CartProvider } from './cart/CartProvider';
import { Catalog } from './pages/Catalog';
import { OrderConfirmation } from './pages/OrderConfirmation';
import { PixPayment } from './pages/PixPayment';
import { PublicDashboard } from './pages/PublicDashboard';

function App() {
  return (
    <CartProvider>
      <BrowserRouter>
        <AppHeader />
        <Routes>
          <Route path="/" element={<PublicDashboard />} />
          <Route path="/cardapio" element={<Catalog />} />
          <Route path="/pagamento/:orderId" element={<PixPayment />} />
          <Route path="/confirmacao/:orderId" element={<OrderConfirmation />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </CartProvider>
  );
}

export default App;
