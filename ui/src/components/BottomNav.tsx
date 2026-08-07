import { LineChart, ShoppingBag } from 'lucide-react';
import { NavLink } from 'react-router-dom';
import styles from './BottomNav.module.css';

export function BottomNav() {
  return (
    <nav className={styles.nav}>
      <NavLink
        to="/"
        end
        className={({ isActive }) => (isActive ? styles.linkActive : styles.link)}
      >
        <LineChart size={20} />
        Transparência
      </NavLink>
      <NavLink
        to="/cardapio"
        className={({ isActive }) => (isActive ? styles.linkActive : styles.link)}
      >
        <ShoppingBag size={20} />
        Cardápio
      </NavLink>
    </nav>
  );
}
