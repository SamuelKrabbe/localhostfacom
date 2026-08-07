import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AppHeader } from './components/AppHeader';
import { CartProvider } from './cart/CartProvider';
import { PublicDashboard } from './pages/PublicDashboard';

function App() {
  return (
    <CartProvider>
      <BrowserRouter>
        <AppHeader />
        <Routes>
          <Route path="/" element={<PublicDashboard />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </CartProvider>
  );
}

export default App;
