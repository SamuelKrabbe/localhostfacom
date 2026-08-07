import { Link, NavLink } from 'react-router-dom';
import styles from './AppHeader.module.css';

export function AppHeader() {
  return (
    <header className={styles.header}>
      <div className={styles.inner}>
        <Link to="/" className={styles.wordmark}>
          <span className={styles.host}>localhost</span>
          <span className={styles.port}>:facom</span>
          <span className={styles.cursor}> █</span>
        </Link>
        <nav className={styles.nav}>
          <NavLink
            to="/"
            end
            className={({ isActive }) => (isActive ? styles.linkActive : styles.link)}
          >
            Transparência
          </NavLink>
          <NavLink
            to="/cardapio"
            className={({ isActive }) => (isActive ? styles.linkActive : styles.link)}
          >
            Cardápio
          </NavLink>
        </nav>
      </div>
    </header>
  );
}
