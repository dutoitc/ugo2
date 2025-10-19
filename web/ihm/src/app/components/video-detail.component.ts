import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HmsPipe } from '../shared/pipes/hms.pipe';
import { IntFrPipe } from '../shared/pipes/int-fr.pipe';
import { LocalDateTimePipe } from '../shared/pipes/local-datetime.pipe';
import { ApiService } from '../services/api.service';
import { VideoDetailResponse } from '../services/api.models';
import { TimeSeriesChartComponent } from './time-series-chart.component';
import VideoDetailAdapter from '../adapters/video-detail.adapter';
import { TimeseriesPoint, LatestRowVm, ReactionsRowVm } from '../models/video-detail.vm';

@Component({
standalone: true,
selector: 'app-video-detail',
imports: [CommonModule, RouterLink, TimeSeriesChartComponent, HmsPipe, IntFrPipe, LocalDateTimePipe],
templateUrl: './video-detail.component.html',
styleUrls: ['./video-detail.component.css'],
})
export class VideoDetailComponent implements OnInit, OnDestroy {
private route = inject(ActivatedRoute);
private api = inject(ApiService);

readonly data = signal<VideoDetailResponse | null>(null);

private videoId = 0;
gran: 'hour' | 'day' = 'hour';
private ts: any | null = null;
private _seriesGlobal: TimeseriesPoint[] | null = null;
private _seriesByPf = new Map<string, TimeseriesPoint[]>();


// TrackBy pour *ngFor
trackByPlatform(index: number, item: any): string {
    const pf = item?.src?.platform ?? item?.platform ?? index;
    return String(pf);
  }

  // Utilisé par le template (réactions)
  showIfPos(v: any): boolean {
    const n = Number(v ?? 0);
    return Number.isFinite(n) && n > 0;
  }

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id') || 0);
    this.videoId = Number.isFinite(id) ? id : 0;

    this.api.getVideoById(this.videoId).subscribe({
      next: (res: VideoDetailResponse) => {
        this.data.set(res);
      },
      error: (err: unknown) => console.error('[video-detail] getVideoById failed', err),
    });

    this.fetchTimeseries();
  }

  ngOnDestroy(): void {
    // rien à nettoyer ici
  }

  private fetchTimeseries(): void {
    const range = this.gran === 'hour' ? '7d' : '60d';
    this.api
      .getVideoTimeseries(this.videoId, { metric: 'views_native', interval: this.gran, range })
      .subscribe({
        next: (res: unknown) => {
          const r: any = res as any;
          this.ts = (r && typeof r === 'object' && 'timeseries' in r) ? (r as any).timeseries : r || null;
        },
        error: (err: unknown) => console.error('[video-detail] getVideoTimeseries failed', err),
      });
  }

  // ================= Helpers & KPI =================

  private n(x: any): number {
    const v = Number(x || 0);
    return Number.isFinite(v) ? v : 0;
  }

  toLocal(dt: string | null | undefined): string {
    if (!dt) return '—';
    try {
      return new Date(dt).toLocaleString('fr-CH');
    } catch {
      return String(dt);
    }
  }

  fmtHMS(t: any): string {
    const n = this.n(t);
    if (!Number.isFinite(n) || n <= 0) return '0';
    const d = Math.floor(n / 86400),
      h = Math.floor((n - d * 86400) / 3600);
    const m = Math.floor((n - d * 86400 - h * 3600) / 60);
    const s = Math.floor(n - d * 86400 - h * 3600 - m * 60);
    const pad2 = (x: number) => (x < 10 ? '0' + x : '' + x);
    const dd = d > 0 ? `${d}j ` : '';
    return `${dd}${h}h${pad2(m)}'${pad2(s)}''`;
  }

  fmtInt(v: any): string {
    const n = this.n(v);
    return n === 0 ? '0' : n.toLocaleString('fr-CH');
  }

  fmtPct(v: any): string {
    if (v == null || v === '') return '—';
    const n = Number(v);
    if (!Number.isFinite(n)) return '—';
    return `${(n * 100).toFixed(2)} %`;
  }

  rollViewsSum(): number {
    const d = this.data();
    if (!d || !Array.isArray(d.sources)) return 0;
    return d.sources.reduce(
      (acc: number, s: any) =>
        acc + this.n((s.latest || {}).views_native || (s.latest || {}).views),
      0
    );
  }

  rollLikes(): number {
    const d = this.data();
    if (!d || !Array.isArray(d.sources)) return 0;
    return d.sources.reduce((acc: number, s: any) => acc + this.n((s.latest || {}).likes), 0);
  }

  rollComments(): number {
    const d = this.data();
    if (!d || !Array.isArray(d.sources)) return 0;
    return d.sources.reduce((acc: number, s: any) => acc + this.n((s.latest || {}).comments), 0);
  }

  rollShares(): number {
    const d = this.data();
    if (!d || !Array.isArray(d.sources)) return 0;
    return d.sources.reduce((acc: number, s: any) => acc + this.n((s.latest || {}).shares), 0);
  }

/** "hh:mm:ss" ou "mm:ss" → secondes */
private parseHmsToSeconds(x: unknown): number {
  if (typeof x !== 'string') return 0;
  const parts = x.split(':').map(Number);
  if (parts.some(n => !Number.isFinite(n))) return 0;
  if (parts.length === 3) { const [h,m,s] = parts; return h*3600 + m*60 + s; }
  if (parts.length === 2) { const [m,s] = parts; return m*60 + s; }
  return 0;
}


  rollEngRate(): string {
    const views = this.rollViewsSum();
    const likes = this.rollLikes();
    const comments = this.rollComments();
    const shares = this.rollShares();
    const den = Math.max(1, views);
    return this.fmtPct((likes + comments + shares) / den);
  }

  // ================= Séries (via VideoDetailAdapter) =================

  private parseSeries(input: any): TimeseriesPoint[] {
    return VideoDetailAdapter.parseSeries(input);
  }

  globalViewsSeries(): TimeseriesPoint[] {
    if (!this._seriesGlobal) {
      const raw = this.ts?.views ?? this.ts?.timeseries?.views ?? this.ts;
      this._seriesGlobal = VideoDetailAdapter.toSeriesGlobal(raw);
    }
    return this._seriesGlobal;
  }

  platformViewsSeries(pf: string | null | undefined): TimeseriesPoint[] {
    const key = String(pf || '').toUpperCase();
    let s = this._seriesByPf.get(key);
    if (!s) {
      s = VideoDetailAdapter.toSeriesByPlatform(this.ts, key);
      this._seriesByPf.set(key, s);
    }
    return s;
  }


  // ================= Tables (shape legacy pour le template) =================

  latestRows(): Array<{ src: { platform: string }, l: any }> {
    const srcs = this.data()?.sources ?? [];
    // On pourrait passer par l’adapter.toLatestRows(...) pour cohérence,
    // mais on reconstruit explicitement le shape attendu par le HTML.
    return (srcs as any[])
      .map((s: any) => ({ src: { platform: s?.platform ?? 'unknown' }, l: s?.latest }))
      .filter(r => !!r.l);
  }

  reactionsRows(): Array<{ src: { platform: string }, l: any }> {
    const srcs = this.data()?.sources ?? [];
    return (srcs as any[])
      .map((s: any) => {
        const l = s?.latest ?? {};
        // Garantir les champs utilisés par le template, avec 0 par défaut
        const safe = {
          reactions_like: this.n(l.reactions_like),
          reactions_love: this.n(l.reactions_love),
          reactions_wow: this.n(l.reactions_wow),
          reactions_haha: this.n(l.reactions_haha),
          reactions_sad: this.n(l.reactions_sad),
          reactions_angry: this.n(l.reactions_angry),
        };
        return { src: { platform: s?.platform ?? 'unknown' }, l: { ...l, ...safe } };
      })
      // Afficher seulement si au moins une réaction > 0, comme avant
      .filter(r =>
        [r.l.reactions_like, r.l.reactions_love, r.l.reactions_wow, r.l.reactions_haha, r.l.reactions_sad, r.l.reactions_angry]
          .some((v: number) => Number.isFinite(v) && v > 0)
      );
  }

  hasAnyReactions(): boolean {
    return this.reactionsRows().length > 0;
  }

  private uniquePlatformRows<T extends { src?: { platform?: string } }>(rows: Array<T>): Array<T> {
    const seen = new Set<string>();
    return (rows ?? []).filter((r) => {
      const key = (r?.src?.platform ?? '').toLowerCase().trim();
      if (!key) return false;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  }

  get latestRowsDedup(): Array<{ src: { platform: string }, l: any }> {
    return this.uniquePlatformRows(this.latestRows());
  }



/** Durée vidéo par défaut si latest.length_seconds absent */
private videoLengthSecondsFallback(): number {
  const v: any = (this.data() as any)?.video ?? {};
  const n = Number(v.length_seconds ?? v.duration_seconds ?? v.duration);
  if (Number.isFinite(n) && n > 0) return Math.round(n);
  const lens = (this.data()?.sources ?? [])
    .map((s: any) => Number(s?.latest?.length_seconds))
    .filter((x: number | null): x is number => x!=null && Number.isFinite(x) && x > 0) as number[];
  if (lens.length) return Math.round(lens.reduce((a,b)=>a+b,0) / lens.length);
  return 300; // 5 min par défaut
}


/** Clamp plausibilité : max ≈ 1.1 × (views * length) avec fallback length */
private clampPlausibleSeconds(sec: number, latest: any): number {
  const views  = Number(latest?.views_native ?? latest?.views ?? 0) || 0;
  let length   = Number(latest?.length_seconds ?? 0) || 0;
  if (!length) length = this.videoLengthSecondsFallback();
  if (views > 0 && length > 0) {
    const max = views * length * 1.1; // serré
    return Math.min(Math.max(sec, 0), max);
  }
  return Math.max(0, sec);
}


/**
 * Durée équivalente de visionnage (secondes) depuis un `latest` hétérogène.
 * Règle: on prend le PLUS PETIT candidat explicite (sec/ms/min/hms/nested),
 * sinon un dérivé (min(avg*views, ratio*length*views)).
 * Clamp **strict** par source: max = views * length (pas de multiplicateur).
 */
private pickWatchSeconds(latest: any): number {
  if (!latest) return 0;
  const num = (v: unknown) => { const n = Number(v); return Number.isFinite(n) ? n : 0; };

  const views  = num(latest.views_native ?? latest.views);
  const length = num(latest.length_seconds) || this.videoLengthSecondsFallback();
  const cap    = (views > 0 && length > 0) ? views * length : Number.POSITIVE_INFINITY;

  // candidats explicites
  const cand: number[] = [];
  const push = (v: number) => { if (v > 0 && Number.isFinite(v)) cand.push(v); };

  // seconds
  push(num(latest.total_watch_seconds));
  push(num(latest.watch_time_seconds));
  push(num(latest.watch_seconds));

  // ms → s
  const ms = num(latest.watch_time_ms) || num(latest.watch_ms) || num(latest.watch_eq_ms);
  if (ms) push(Math.round(ms / 1000));

  // minutes → s
  const min = num(latest.estimated_minutes_watched) || num(latest.watch_minutes);
  if (min) push(min * 60);

  // hms
  if (typeof latest.watch_time_hms === 'string') push(this.parseHmsToSeconds(latest.watch_time_hms));

  // nested
  if (latest.metrics) push(this.pickWatchSeconds(latest.metrics));
  if (latest.totals)  push(this.pickWatchSeconds(latest.totals));

  // si on a au moins un explicite → prendre le plus petit
  if (cand.length) return Math.min(Math.min(...cand), cap);

  // sinon dérivés prudents
  const derived: number[] = [];
  const avgSec = num(latest.avg_watch_seconds);
  const ratio  = num(latest.avg_watch_ratio);
  if (avgSec && views) derived.push(avgSec * views);
  if (ratio && length && views) derived.push(ratio * length * views);

  if (derived.length) return Math.min(Math.min(...derived), cap);

  return 0;
}

/** Somme (secondes) sur toutes les sources + clamp global (sécurité) */
rollWatchEqSeconds(): number {
  const d = this.data();
  if (!d || !Array.isArray(d.sources)) return 0;

  let total = 0;
  let globalCap = 0;

  for (const s of d.sources as any[]) {
    const latest = s?.latest ?? {};
    const views  = Number(latest.views_native ?? latest.views) || 0;
    const length = Number(latest.length_seconds) || this.videoLengthSecondsFallback();
    if (views > 0 && length > 0) globalCap += views * length;
    total += this.pickWatchSeconds(latest);
  }

  // cap global: impossible de dépasser la somme views*length
  if (Number.isFinite(globalCap) && globalCap > 0) {
    total = Math.min(total, globalCap);
  }

  return Math.max(0, Math.round(total));
}

}
