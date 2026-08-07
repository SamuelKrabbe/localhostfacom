const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const date = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit' });
const dayMonthYear = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
});
const dateTime = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit',
  month: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

export function formatCurrency(value: number): string {
  return currency.format(value);
}

export function formatDate(iso: string): string {
  return date.format(new Date(iso));
}

/**
 * Formats a bare `YYYY-MM-DD`. `new Date('2026-08-07')` is parsed as UTC midnight, which
 * in Brazil renders as the day before, so the parts are read out and rebuilt locally.
 */
export function formatIsoDate(iso: string): string {
  const [year, month, day] = iso.split('-').map(Number);
  return dayMonthYear.format(new Date(year, month - 1, day));
}

export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso));
}

/** Renders a countdown as mm:ss, clamped at zero so it never shows a negative timer. */
export function formatCountdown(msRemaining: number): string {
  const totalSeconds = Math.max(0, Math.floor(msRemaining / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}
