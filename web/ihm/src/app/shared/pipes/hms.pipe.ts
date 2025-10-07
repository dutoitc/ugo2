import { Pipe, PipeTransform } from '@angular/core';

/** Transforme un nombre de secondes en format lisible: 3j 10h02'58'' */
@Pipe({ name: 'hms', standalone: true, pure: true })
export class HmsPipe implements PipeTransform {
transform(totalSeconds: number | null | undefined): string {
    if (totalSeconds == null || isNaN(Number(totalSeconds))) return '—';
    const s = Math.max(0, Math.floor(Number(totalSeconds)));
    const days = Math.floor(s / 86400);
    const hours = Math.floor((s % 86400) / 3600);
    const minutes = Math.floor((s % 3600) / 60);
    const secs = s % 60;
    const mm = minutes.toString().padStart(2, '0');
    const ss = secs.toString().padStart(2, '0');
    const dPart = days > 0 ? `${days}j ` : '';
    const hPart = `${hours}h`;
    return `${dPart}${hPart}${mm}'${ss}''`;
  }
}
