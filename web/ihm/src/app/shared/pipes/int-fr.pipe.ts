import { Pipe, PipeTransform } from '@angular/core';

/** Affiche un entier au format fr-CH (séparateurs) */
@Pipe({ name: 'intFr', standalone: true, pure: true })
export class IntFrPipe implements PipeTransform {
private fmt = new Intl.NumberFormat('fr-CH', { maximumFractionDigits: 0 });
transform(value: number | null | undefined): string {
    if (value == null || !isFinite(Number(value))) return '—';
    return this.fmt.format(Math.round(Number(value)));
  }
}
