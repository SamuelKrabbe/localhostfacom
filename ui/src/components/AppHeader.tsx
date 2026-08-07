import { useEffect, useState } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { wordmarkSegments, wordmarkText } from '../lib/wordmark';
import styles from './AppHeader.module.css';

const TYPING_INTERVAL_MS = 70;

const SEGMENTS = wordmarkSegments();
const FULL_TEXT = wordmarkText();
// The prompt is printed by the shell, not typed by the user, so it is there from the start.
const PROMPT_LENGTH = SEGMENTS[0].text.length;
const COMMAND_LENGTH = FULL_TEXT.length - PROMPT_LENGTH;
// How many typed characters must land before each segment starts appearing. Negative for
// the prompt, which is already on screen at zero.
const SEGMENT_STARTS = SEGMENTS.map(
  (_, index) =>
    SEGMENTS.slice(0, index).reduce((total, segment) => total + segment.text.length, 0) -
    PROMPT_LENGTH,
);

export function AppHeader() {
  const [typed, setTyped] = useState(() =>
    window.matchMedia('(prefers-reduced-motion: reduce)').matches ? COMMAND_LENGTH : 0,
  );

  useEffect(() => {
    if (typed >= COMMAND_LENGTH) {
      return;
    }
    const timer = window.setTimeout(() => setTyped(typed + 1), TYPING_INTERVAL_MS);
    return () => window.clearTimeout(timer);
  }, [typed]);

  return (
    <header className={styles.header}>
      <div className={styles.inner}>
        {/* The visible text is mid-animation, so the accessible name is the whole string. */}
        <Link to="/" className={styles.wordmark} aria-label={FULL_TEXT}>
          <span aria-hidden="true">
            {SEGMENTS.map((segment, index) => (
              <span key={segment.kind} className={styles[segment.kind]}>
                {segment.text.slice(0, Math.max(0, typed - SEGMENT_STARTS[index]))}
              </span>
            ))}
            {/* A real terminal cursor stops blinking while keys are coming in. */}
            <span
              className={
                typed >= COMMAND_LENGTH ? `${styles.cursor} ${styles.blinking}` : styles.cursor
              }
            >
              █
            </span>
          </span>
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
