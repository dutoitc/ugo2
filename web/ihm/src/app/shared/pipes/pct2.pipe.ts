import { Pipe, PipeTransform } from '@angular/core';

/** Formate un ratio (0..1) en pourcentage avec 2 décimales: 0.0925 -> 9.25 % */
@Pipe({ name: 'pct2', standalone: true, pure: true })
export class Pct2Pipe implements PipeTransform {
private fmt = new Intl.NumberFormat('fr-CH', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
transform(ratio: number | null | undefined): string {
    if (ratio == null || !isFinite(Number(ratio))) return '—';
    const pct = Number(ratio) * 100;
    return `${this.fmt.format(pct)} %`;
  }
}
