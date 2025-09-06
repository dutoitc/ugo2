export function toZurich(dbUtc: string | null | undefined): string {
  if (!dbUtc) return '—';
  try {
    let iso = dbUtc;
    if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/.test(dbUtc) && !dbUtc.includes('T')) {
      iso = dbUtc.replace(' ', 'T') + 'Z';
    }
    const dt = new Date(iso);
    return dt.toLocaleString('fr-CH', { timeZone: 'Europe/Zurich' });
  } catch { return dbUtc ?? '—'; }
}

/** Date seule (jj.mm.aaaa) Europe/Zurich */
export function toZurichDate(dbUtc: string | null | undefined): string {
  if (!dbUtc) return '—';
  try {
    let iso = dbUtc;
    if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/.test(dbUtc) && !dbUtc.includes('T')) {
      iso = dbUtc.replace(' ', 'T') + 'Z';
    }
    const dt = new Date(iso);
    return dt.toLocaleDateString('fr-CH', { timeZone: 'Europe/Zurich' });
  } catch { return dbUtc ?? '—'; }
}

/** Nombre entier “brut” (ex: 4500) */
export function n(x: number | null | undefined): string {
  if (x == null) return '—';
  return String(Math.round(x));
}

// (garde k() si tu l’utilises ailleurs)
export function k(n: number | null | undefined): string {
  if (n == null) return '—';
  if (n >= 1_000_000) return (n/1_000_000).toFixed(1).replace('.0','') + 'M';
  if (n >= 1_000) return (n/1_000).toFixed(1).replace('.0','') + 'k';
  return String(n);
}
