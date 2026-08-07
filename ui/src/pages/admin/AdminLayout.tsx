import { useState } from 'react';
import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom';
import { 
  Package, 
  ShoppingCart, 
  Wallet, 
  Users, 
  LogOut, 
  Menu, 
  X, 
  BookOpen
} from 'lucide-react';

export function AdminLayout() {
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();

  // Em um cenário real, você buscaria o email do admin do seu Contexto de Autenticação
  const adminEmail = "admin@saladeestudos.com"; 

  const handleLogout = () => {
    // Aqui você limparia o token JWT/estado de autenticação
    // localStorage.removeItem('token');
    navigate('/admin/login');
  };

  const navigation = [
    { name: 'Produtos', path: '/admin/produtos', icon: Package },
    { name: 'Pedidos', path: '/admin/pedidos', icon: ShoppingCart },
    { name: 'Despesas & Meta', path: '/admin/configuracoes', icon: Wallet },
    { name: 'Admins', path: '/admin/usuarios', icon: Users },
  ];

  const NavLinks = ({ onClick = () => {} }) => (
    <ul className="space-y-2">
      {navigation.map((item) => {
        const isActive = location.pathname.includes(item.path);
        const Icon = item.icon;
        
        return (
          <li key={item.name}>
            <Link
              to={item.path}
              onClick={onClick}
              className={`flex items-center space-x-3 px-4 py-3 rounded-lg transition-colors ${
                isActive 
                  ? 'bg-blue-50 text-blue-700 font-medium' 
                  : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
              }`}
            >
              <Icon size={20} className={isActive ? 'text-blue-700' : 'text-gray-500'} />
              <span>{item.name}</span>
            </Link>
          </li>
        );
      })}
    </ul>
  );

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col md:flex-row">
      
      {/* ============================== */}
      {/* HEADER MOBILE                  */}
      {/* ============================== */}
      <div className="md:hidden bg-white border-b border-gray-200 px-4 py-3 flex items-center justify-between sticky top-0 z-20">
        <div className="flex items-center space-x-2 text-gray-900 font-semibold">
          <BookOpen size={24} className="text-blue-600" />
          <span>Admin | Sala de Estudos</span>
        </div>
        <button 
          onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
          className="p-2 text-gray-600 hover:bg-gray-100 rounded-lg"
        >
          {isMobileMenuOpen ? <X size={24} /> : <Menu size={24} />}
        </button>
      </div>

      {/* ============================== */}
      {/* MENU MOBILE (OVERLAY)          */}
      {/* ============================== */}
      {isMobileMenuOpen && (
        <div className="md:hidden fixed inset-0 z-10 bg-white pt-16 flex flex-col">
          <div className="flex-1 px-4 py-6 overflow-y-auto">
            <NavLinks onClick={() => setIsMobileMenuOpen(false)} />
          </div>
          <div className="p-4 border-t border-gray-200 bg-gray-50">
            <p className="text-sm text-gray-500 truncate mb-4 px-2">{adminEmail}</p>
            <button 
              onClick={handleLogout}
              className="w-full flex items-center justify-center space-x-2 text-red-600 bg-red-50 hover:bg-red-100 py-3 rounded-lg font-medium transition-colors"
            >
              <LogOut size={20} />
              <span>Sair do sistema</span>
            </button>
          </div>
        </div>
      )}

      {/* ============================== */}
      {/* SIDEBAR DESKTOP                */}
      {/* ============================== */}
      <aside className="hidden md:flex flex-col w-64 bg-white border-r border-gray-200 sticky top-0 h-screen">
        <div className="p-6 flex items-center space-x-3 text-gray-900 border-b border-gray-100">
          <BookOpen size={28} className="text-blue-600" />
          <span className="font-bold text-lg leading-tight">Admin<br/><span className="text-sm font-normal text-gray-500">Sala de Estudos</span></span>
        </div>
        
        <nav className="flex-1 px-4 py-6 overflow-y-auto">
          <NavLinks />
        </nav>

        <div className="p-4 border-t border-gray-200">
          <div className="px-2 mb-4">
            <p className="text-xs font-medium text-gray-500 uppercase tracking-wider mb-1">Logado como</p>
            <p className="text-sm text-gray-900 truncate font-medium">{adminEmail}</p>
          </div>
          <button 
            onClick={handleLogout}
            className="w-full flex items-center space-x-3 px-4 py-2 text-gray-600 hover:bg-red-50 hover:text-red-600 rounded-lg transition-colors"
          >
            <LogOut size={20} />
            <span>Sair</span>
          </button>
        </div>
      </aside>

      {/* ============================== */}
      {/* ÁREA DE CONTEÚDO (MAIN)        */}
      {/* ============================== */}
      <main className="flex-1 w-full max-w-7xl mx-auto p-4 md:p-8">
        {/* O <Outlet /> renderiza as telas filhas (Produtos, Pedidos, etc) baseadas na URL */}
        <Outlet />
      </main>
      
    </div>
  );
}
