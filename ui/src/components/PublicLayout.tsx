import { Outlet } from 'react-router-dom';
import { AppHeader } from './AppHeader';
import { BottomNav } from './BottomNav';
import styles from './PublicLayout.module.css';

export function PublicLayout() {
  return (
    <div className={styles.shell}>
      <AppHeader />
      <Outlet />
      <BottomNav />
    </div>
  );
}
