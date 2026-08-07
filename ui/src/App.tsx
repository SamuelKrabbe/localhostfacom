import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';

// Importação das telas públicas (ajuste os caminhos conforme sua estrutura de pastas)
// import { CatalogScreen } from './pages/CatalogScreen';
// import { PixPayment } from './pages/PixPayment';
// import { OrderConfirmation } from './pages/OrderConfirmation';
import { PublicDashboard } from './pages/PublicDashboard';

// Importação das telas admin (serão criadas nos próximos passos)
// import { AdminLogin } from './pages/admin/AdminLogin';
// import { AdminLayout } from './pages/admin/AdminLayout';
// import { AdminProducts } from './pages/admin/AdminProducts';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* ROTAS PÚBLICAS (CLIENTE) */}
        
        {/* Tela 1: Catálogo e Carrinho */}
        {/* <Route path="/" element={<CatalogScreen />} /> */}
        
        {/* Tela 2: Pagamento PIX */}
        {/* <Route path="/pagamento/:orderId" element={<PixPayment />} /> */}
        
        {/* Tela 3: Confirmação */}
        {/* <Route path="/confirmacao" element={<OrderConfirmation />} /> */}
        
        {/* Tela 4: Dashboard de Transparência */}
        <Route path="/transparencia" element={<PublicDashboard />} />


        {/* ROTAS ADMINISTRATIVAS */}
        
        {/* Tela 5: Login Admin */}
        {/* <Route path="/admin/login" element={<AdminLogin />} /> */}

        {/* Telas Protegidas do Admin (Usando um Layout aninhado) */}
        {/* <Route path="/admin" element={<AdminLayout />}>
          <Route index element={<Navigate to="/admin/produtos" replace />} />
          <Route path="produtos" element={<AdminProducts />} />
          <Route path="pedidos" element={<AdminOrders />} />
          <Route path="configuracoes" element={<AdminSettings />} />
          <Route path="usuarios" element={<AdminUsers />} />
        </Route> */}

        {/* Fallback para rotas não encontradas */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
