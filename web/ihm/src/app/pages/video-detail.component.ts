import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../services/api.service';
import { VideoDetailResponse } from '../models';

@Component({
  standalone: true,
  selector: 'app-video-detail',
  imports: [CommonModule, RouterLink],
  templateUrl: './video-detail.component.html',
  styleUrls: ['./video-detail.component.css'],
})
export class VideoDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private api = inject(ApiService);

  readonly data = signal<VideoDetailResponse | null>(null);

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) return;
    const id = Number(idParam);
    this.api.getVideoById(id).subscribe({
      next: (res) => this.data.set(res),
      error: (err) => {
        console.error('getVideoById failed', err);
        this.data.set(null);
      }
    });
  }

  private n(v: any): number {
    if (v == null || v === '') return 0;
    const n = Number(v);
    return Number.isFinite(n) ? n : 0;
  }

  /** 4h37'18''  (si >24h -> 2j 04h37'18'') */
  fmtHMS(val: number | string | null | undefined): string {
    const s = this.n(val);
    if (s <= 0) return '—';
    let t = Math.floor(s);
    const days = Math.floor(t / 86400); t -= days * 86400;
    const h = Math.floor(t / 3600); t -= h * 3600;
    const m = Math.floor(t / 60); const sec = t - m * 60;
    const pad = (x: number) => (x < 10 ? '0' : '') + x;
    const hh = days > 0 ? pad(h) : String(h);
    const core = `${hh}h${pad(m)}'${pad(sec)}''`;
    return days > 0 ? `${days}j ${core}` : core;
  }

  fmtInt(n: number | string | null | undefined): string {
    if (n == null || n === '') return '—';
    const num = Number(n);
    return Number.isFinite(num) ? String(Math.trunc(num)) : String(n);
  }

  fmtPct(n: number | string | null | undefined): string {
    if (n == null || n === '') return '—';
    const num = Number(n);
    if (!Number.isFinite(num)) return String(n);
    return (num * 100).toFixed(2) + ' %';
  }

  /** Pour usage template, sans appeler Number(...) dans l'HTML */
  engRateStr(val: any): string {
    if (val == null || val === '') return '—';
    const num = Number(val);
    if (!Number.isFinite(num)) return String(val);
    return (num * 100).toFixed(2) + ' %';
  }

  toLocal(iso: string | null | undefined): string {
    if (!iso || (typeof iso === 'string' && iso.startsWith('0000-'))) return '—';
    const d = new Date(iso);
    return isNaN(d.getTime()) ? '—' : d.toLocaleString('fr-CH', { timeZone: 'Europe/Zurich' });
  }

  /** ROLLUP (agrégats) */
  rollViewsSum(): number { return this.n(this.data()?.rollup?.views_native_sum); }
  rollLikes(): number { return this.n(this.data()?.rollup?.likes_sum); }
  rollComments(): number { return this.n(this.data()?.rollup?.comments_sum); }
  rollShares(): number { return this.n(this.data()?.rollup?.shares_sum); }
  rollWatchSeconds(): number { return this.n(this.data()?.rollup?.total_watch_seconds_sum); }
  rollEngRate(): string { return this.fmtPct(this.data()?.rollup?.engagement_rate_sum); }

  /** SOURCES / LATEST */
  latestRows() {
    const srcs = this.data()?.sources ?? [];
    return srcs
      .map(s => ({ src: s, l: s.latest }))
      .filter(x => !!x.l);
  }

  /** Eng. rate à afficher dans le tableau :
   *  - si la source fournit engagement_rate -> afficher tel quel
   *  - sinon, si plateforme FACEBOOK -> recalc (likes+comments+shares)/views_native
   *  - sinon, '—'
   */
  engRateForRow(r: any): string {
    const provided = r?.l?.engagement_rate;
    if (provided != null && provided !== '') {
      return this.engRateStr(provided);
    }
    const platform = String(r?.src?.platform || '').toUpperCase();
    if (platform === 'FACEBOOK') {
      const likes = this.n(r?.l?.likes);
      const comments = this.n(r?.l?.comments);
      const shares = this.n(r?.l?.shares);
      const views = this.n(r?.l?.views_native);
      if (views > 0) {
        const rate = (likes + comments + shares) / views;
        return (rate * 100).toFixed(2) + ' %';
      }
    }
    return '—';
  }
}
