import { Component, computed, effect, signal, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HmsPipe } from '../shared/pipes/hms.pipe';
import { IntFrPipe } from '../shared/pipes/int-fr.pipe';
import { LocalDateTimePipe } from '../shared/pipes/local-datetime.pipe';
import { ApiService } from '../services/api.service';
import { VideoDetailResponse } from '../models';
import { TimeSeriesChartComponent } from '../components/time-series-chart.component';

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

// TrackBy pour *ngFor
trackByPlatform(index: number, item: any): string {
  return (item?.src?.platform ?? item?.platform ?? index)?.toString();
}

// Utilisé par le template (réactions)
showIfPos(v: any): boolean {
  const n = Number(v ?? 0);
  return Number.isFinite(n) && n > 0;
}

ngOnInit(): void {
  const id = Number(this.route.snapshot.paramMap.get('id') || 0);
  this.videoId = Number.isFinite(id) ? id : 0;
  console.log('[video-detail] ngOnInit videoId=', this.videoId);

  this.api.getVideoById(this.videoId).subscribe({
    next: (res: VideoDetailResponse) => {
      this.data.set(res);
      const sources = (res as any)?.sources || [];
      console.log('[video-detail] getVideoById ok, sources.len=', Array.isArray(sources) ? sources.length : -1,
                  'platforms=', Array.isArray(sources) ? sources.map((s:any)=>s.platform) : null);
    },
    error: (err: unknown) => console.error('[video-detail] getVideoById failed', err)
  });

  this.fetchTimeseries();
}

ngOnDestroy(): void {
  // plus d’instances impératives à nettoyer
}

private fetchTimeseries(): void {
  const range = this.gran === 'hour' ? '7d' : '60d';
  console.log('[video-detail] fetchTimeseries gran=', this.gran, 'range=', range);
  this.api.getVideoTimeseries(this.videoId, { metric: 'views_native', interval: this.gran, range })
  .subscribe({
    next: (res: unknown) => {
      const r: any = res as any;
      this.ts = (r && typeof r === 'object' && 'timeseries' in r) ? r.timeseries : r || null;
      const keys = this.ts ? Object.keys(this.ts) : null;
      console.log('[video-detail] /timeseries normalized keys=', keys);

      try {
        const dbg = (x:any) => x==null ? null : { type: typeof x, isArray: Array.isArray(x), keys: (x && typeof x==='object') ? Object.keys(x).slice(0,5) : null, sample: Array.isArray(x) ? x.slice(0,3) : null };
        console.log('[video-detail] raw.views =', dbg(this.ts?.views));
        console.log('[video-detail] raw.YOUTUBE =', dbg(this.ts?.YOUTUBE));
        console.log('[video-detail] raw.FACEBOOK =', dbg(this.ts?.FACEBOOK));
      } catch {}

      try { console.log('[video-detail] global len=', this.globalViewsSeries().length, 'first3=', this.globalViewsSeries().slice(0,3)); } catch(e){}
      try { console.log('[video-detail] YT len=', this.platformViewsSeries('YOUTUBE').length, 'first3=', this.platformViewsSeries('YOUTUBE').slice(0,3)); } catch(e){}
      try { console.log('[video-detail] FB len=', this.platformViewsSeries('FACEBOOK').length, 'first3=', this.platformViewsSeries('FACEBOOK').slice(0,3)); } catch(e){}
    },
    error: (err: unknown) => console.error('[video-detail] getVideoTimeseries failed', err)
  });

}

// ================= Helpers & KPI =================

private n(x: any): number { const v = Number(x || 0); return Number.isFinite(v) ? v : 0; }

toLocal(dt: string | null | undefined): string {
  if (!dt) return '—';
  try { return new Date(dt).toLocaleString('fr-CH'); } catch { return String(dt); }
}

fmtHMS(t: any): string {
  const n = this.n(t);
  if (!Number.isFinite(n) || n <= 0) return '0';
  const d = Math.floor(n / 86400), h = Math.floor((n - d * 86400) / 3600);
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

latestRows(): any[] {
  const srcs = this.data()?.sources ?? [];
  return srcs.map((s: any) => ({ src: s, l: (s as any)?.latest })).filter(x => !!x.l);
}

reactionsRows(): Array<any> {
  return (this.latestRows() || []).filter((r: any) => {
    const vals = [
      r?.l?.reactions_like, r?.l?.reactions_love, r?.l?.reactions_wow,
      r?.l?.reactions_haha, r?.l?.reactions_sad, r?.l?.reactions_angry
    ].map((v: any) => Number(v || 0));
    return vals.some((v: number) => Number.isFinite(v) && v > 0);
  });
}

hasAnyReactions(): boolean { return this.reactionsRows().length > 0; }

rollViewsSum(): number {
  const d = this.data();
  if (!d || !Array.isArray(d.sources)) return 0;
  return d.sources.reduce((acc: number, s: any) => acc + this.n((s.latest || {}).views_native || (s.latest || {}).views), 0);
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

rollWatchEqSeconds(): number {
  const d = this.data();
  if (!d || !Array.isArray(d.sources)) return 0;
  return d.sources.reduce((acc: number, s: any) => acc + this.n((s.latest || {}).watch_eq_seconds), 0);
}

rollEngRate(): string {
  const views = this.rollViewsSum();
  const likes = this.rollLikes();
  const comments = this.rollComments();
  const shares = this.rollShares();
  const den = Math.max(1, views);
  return this.fmtPct((likes + comments + shares) / den);
}

// ================= Adaptateurs pour ngx-echarts =================

private parseSeries(input: any): Array<[number, number]> {
  const out: Array<[number, number]> = [];
  if (!input) return out;

  const parseTs = (x: any): number | null => {
    if (x == null) return null;
    if (typeof x === 'string') {
      const d = new Date(x); const t = d.getTime();
      return Number.isFinite(t) ? t : null;
    }
    const n = Number(x);
    if (!Number.isFinite(n)) return null;
    return n < 10_000_000_000 ? n * 1000 : n; // sec→ms auto
  };

  const pickValueFromObj = (o: any): number | null => {
    if (!o || typeof o !== 'object') return null;
    const keys = ['views_native','views','value','v','val','y','sum','count'];
    for (const k of keys) {
      const n = Number((o as any)[k]);
      if (Number.isFinite(n)) return n;
    }
    // fallback: premier champ numérisable
    for (const k of Object.keys(o)) {
      const n = Number((o as any)[k]);
      if (Number.isFinite(n)) return n;
    }
    return null;
  };

  const pickTimeFromObj = (o: any): number | null => {
    if (!o || typeof o !== 'object') return null;
    const keys = ['time','timestamp','date','t','x','ts'];
    for (const k of keys) {
      if (k in o) {
        const t = parseTs((o as any)[k]);
        if (t != null) return t;
      }
    }
    return null;
  };

  // 1) Si c'est (ou contient) un tableau
  const arr = Array.isArray(input) ? input :
              (input && typeof input === 'object' && Array.isArray(input.series)) ? input.series :
              (input && typeof input === 'object' && Array.isArray(input.data)) ? input.data :
              (input && typeof input === 'object' && Array.isArray(input.points)) ? input.points :
              (input && typeof input === 'object' && Array.isArray(input.values)) ? input.values : null;

  if (arr) {
    if (arr.length) {
      try {
        console.log('[video-detail] parseSeries sample0=', JSON.stringify(arr[0]));
      } catch {}
    }
    for (const it of arr) {
      if (Array.isArray(it) && it.length >= 2) {
        const tCandidate = it[0];
        const vCandidate = it[1];
        const t = (typeof tCandidate === 'object') ? pickTimeFromObj(tCandidate) : parseTs(tCandidate);
        let v: number | null = null;
        if (typeof vCandidate === 'object') v = pickValueFromObj(vCandidate);
        else {
          const nv = Number(vCandidate);
          v = Number.isFinite(nv) ? nv : null;
        }
        if (t != null && v != null) out.push([t, v]);
        continue;
      }
      if (it && typeof it === 'object') {
        const t = pickTimeFromObj(it);
        const v = pickValueFromObj(it);
        if (t != null && v != null) out.push([t, v]);
      }
    }
    out.sort((a,b)=>a[0]-b[0]);
    return out;
  }

  // 2) Dictionnaire { ts => v } ou { ts => {value:...} }
  if (input && typeof input === 'object') {
    try { console.log('[video-detail] parseSeries dict keys.sample=', Object.keys(input).slice(0,5)); } catch {}
    for (const k of Object.keys(input)) {
      const t = parseTs(k);
      const raw = (input as any)[k];
      const v = (typeof raw === 'object') ? pickValueFromObj(raw) : (Number.isFinite(Number(raw)) ? Number(raw) : null);
      if (t != null && v != null) out.push([t, v]);
    }
    out.sort((a,b)=>a[0]-b[0]);
    return out;
  }

  return out;
}


globalViewsSeries(): Array<[number, number]> {
  const raw = (this.ts?.views ?? this.ts?.timeseries?.views ?? null);
  try {
    console.log('[video-detail] globalViewsSeries raw type=', typeof raw, 'isArray=', Array.isArray(raw),
      'keys=', raw && typeof raw==='object' ? Object.keys(raw).slice(0,5) : null);
  } catch {}
  const series = this.parseSeries(raw);
  try { console.log('[video-detail] globalViewsSeries len=', series.length, 'first3=', series.slice(0,3)); } catch {}
  return series;
}

platformViewsSeries(pf: string | null | undefined): Array<[number, number]> {
  const key = String(pf || '').toUpperCase();
  const plat: any = (this.ts && (this.ts[key] || this.ts?.by_platform?.[key])) || null;
  const raw: any =
    (plat && Array.isArray(plat?.views)) ? plat.views :
    (Array.isArray(plat)) ? plat :
    this.ts?.views ?? this.ts?.timeseries?.views ?? null;

  try {
    console.log('[video-detail] platformViewsSeries key=', key, 'raw type=', typeof raw, 'isArray=', Array.isArray(raw),
      'keys=', raw && typeof raw==='object' ? Object.keys(raw).slice(0,5) : null);
  } catch {}

  const series = this.parseSeries(raw);
  try { console.log('[video-detail] platformViewsSeries key=', key, 'len=', series.length, 'first3=', series.slice(0,3)); } catch {}
  return series;
}


/** Déduplique des rows par plateforme (facebook, youtube, …) – garde la première occurrence */
private uniquePlatformRows(rows: Array<{ src?: { platform?: string } }>): Array<any> {
  const seen = new Set<string>();
  return (rows ?? []).filter((r) => {
    const key = (r?.src?.platform ?? '').toLowerCase().trim();
    if (!key) return false;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

/** Version dédupliquée, prête pour l’affichage des graphes par plateforme */
get latestRowsDedup(): any[] {
  return this.uniquePlatformRows(this.latestRows());
}

}
