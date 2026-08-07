import { useState } from 'react';
import { LogOut, Menu, Package, ShoppingCart, Users, Wallet, X } from 'lucide-react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import { wordmarkSegments } from '../../lib/wordmark';
import styles from './AdminLayout.module.css';

const NAVIGATION = [
  { to: '/admin/pedidos', label: 'Pedidos', icon: ShoppingCart },
  { to: '/admin/produtos', label: 'Produtos', icon: Package },
  { to: '/admin/despesas', label: 'Despesas & meta', icon: Wallet },
  { to: '/admin/usuarios', label: 'Admins', icon: Users },
];

function Wordmark() {
  return (
    <NavLink to="/" className={styles.wordmark}>
      {wordmarkSegments('/admin').map((segment) => (
        <span
          key={segment.kind}
          className={[
            segment.kind === 'port' || segment.kind === 'path'
              ? styles.wordmarkAccent
              : styles.wordmarkMuted,
            segment.kind === 'prompt' ? styles.promptLine : '',
          ].join(' ')}
        >
          {segment.text}
        </span>
      ))}
    </NavLink>
  );
}

function Nav({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <nav className={styles.nav}>
      {NAVIGATION.map(({ to, label, icon: Icon }) => (
        <NavLink
          key={to}
          to={to}
          onClick={onNavigate}
          className={({ isActive }) =>
            isActive ? `${styles.link} ${styles.linkActive}` : styles.link
          }
        >
          <Icon size={18} />
          {label}
        </NavLink>
      ))}
    </nav>
  );
}

function Account() {
  const { admin, signOut } = useAuth();

  return (
    <div className={styles.account}>
      <p className={styles.accountLabel}>Logado como</p>
      <p className={styles.accountEmail}>{admin?.email}</p>
      <button type="button" className={styles.signOut} onClick={signOut}>
        <LogOut size={18} />
        Sair
      </button>
    </div>
  );
}

export function AdminLayout() {
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const location = useLocation();

  return (
    <div className={styles.shell}>
      <div className={styles.topbar}>
        <Wordmark />
        <button
          type="button"
          className={styles.toggle}
          aria-expanded={isMenuOpen}
          aria-label={isMenuOpen ? 'Fechar menu' : 'Abrir menu'}
          onClick={() => setIsMenuOpen(!isMenuOpen)}
        >
          {isMenuOpen ? <X size={20} /> : <Menu size={20} />}
        </button>
      </div>

      {isMenuOpen ? (
        <div className={styles.drawer}>
          <Nav onNavigate={() => setIsMenuOpen(false)} />
          <Account />
        </div>
      ) : null}

      <aside className={styles.sidebar}>
        <Wordmark />
        <Nav />
        <Account />
      </aside>

      <main className={styles.content}>
        {/* Remounts each screen on navigation, so no stale form state carries over. */}
        <Outlet key={location.pathname} />
      </main>
    </div>
  );
}
