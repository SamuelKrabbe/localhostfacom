import { useEffect, useState } from 'react';

const QUERY = '(prefers-color-scheme: dark)';

export function useDarkMode(): boolean {
  const [isDark, setIsDark] = useState(() => window.matchMedia(QUERY).matches);

  useEffect(() => {
    const query = window.matchMedia(QUERY);
    const listener = (event: MediaQueryListEvent) => setIsDark(event.matches);
    query.addEventListener('change', listener);
    return () => query.removeEventListener('change', listener);
  }, []);

  return isDark;
}
