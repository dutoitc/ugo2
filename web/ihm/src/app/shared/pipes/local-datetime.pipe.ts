import { Pipe, PipeTransform } from '@angular/core';

/** Convertit une date ISO ou 'YYYY-MM-DD HH:mm:ss' vers Europe/Zurich (fr-CH) */
@Pipe({ name: 'localDateTime', standalone: true, pure: true })
export class LocalDateTimePipe implements PipeTransform {
transform(dbUtc: string | Date | null | undefined): string {
    if (dbUtc == null) return '—';
    try {
      let iso: string | Date = dbUtc;
      if (typeof dbUtc === 'string') {
        if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/.test(dbUtc) && !dbUtc.includes('T')) {
          iso = dbUtc.replace(' ', 'T') + 'Z';
        } else {
          iso = dbUtc;
        }
      }
      const dt = new Date(iso);
      return dt.toLocaleString('fr-CH', { timeZone: 'Europe/Zurich' });
    } catch {
      return typeof dbUtc === 'string' ? dbUtc : '—';
    }
  }
}
