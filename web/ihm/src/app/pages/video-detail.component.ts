import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../services/api.service';
import { VideoDetailResponse } from '../models';

declare global {
interface Window { echarts: any; }
}

@Component({
standalone: true,
selector: 'app-video-detail',
imports: [CommonModule, RouterLink],
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

// ECharts instances
private chartGlobal: any = null;
private chartsByPlatform: Record<string, any> = {};
private sparkInstances: Record<string, any> = {};

// Resize observers à détacher
private roList: Array<{ el: HTMLElement; ro: ResizeObserver }> = [];

private onResize = () => {
try {
this.chartGlobal?.resize?.();
      Object.values(this.chartsByPlatform).forEach(c => c?.resize?.());
      Object.values(this.sparkInstances).forEach(c => c?.resize?.());
    } catch {}
  };

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) return;
    this.videoId = Number(idParam);
    console.log('[video-detail] init for videoId=', this.videoId);

    this.api.getVideoById(this.videoId).subscribe({
      next: (res) => {
        console.log('[video-detail] getVideoById raw:', res);
        this.data.set(res);
        setTimeout(() => this.initCharts(), 0);
      },
      error: (err) => console.error('[video-detail] getVideoById failed', err)
    });

    this.fetchTimeseries();
    window.addEventListener('resize', this.onResize);
  }

  ngOnDestroy(): void {
    window.removeEventListener('resize', this.onResize);
    this.roList.forEach(({ ro, el }) => { try { ro.unobserve(el); ro.disconnect(); } catch {} });
    this.roList = [];
  }

  // ================= Helpers formatting =================

  private n(v: any): number {
    if (v == null || v === '') return 0;
    const n = Number(v);
    return Number.isFinite(n) ? n : 0;
  }

  fmtHMS(val: number | string | null | undefined): string {
    const s = this.n(val);
    if (s <= 0) return '—';
    let t = Math.floor(s);
    const d = Math.floor(t / 86400); t -= d * 86400;
    const h = Math.floor(t / 3600); t -= h * 3600;
    const m = Math.floor(t / 60); const sec = t - m * 60;
    const z = (x: number) => (x < 10 ? '0' + x : '' + x);
    const core = `${h}h${z(m)}'${z(sec)}''`;
    return d > 0 ? `${d}j ${core}` : core;
  }

  fmtInt(n: number | string | null | undefined): string {
    if (n == null || n === '') return '—';
    const num = Number(n);
    return Number.isFinite(num) ? String(Math.trunc(num)) : String(n);
  }

  fmtPct(n: number | string | null | undefined): string {
    if (n == null || n === '') return '—';
    const num = Number(n);
    return Number.isFinite(num) ? (num * 100).toFixed(2) + ' %' : String(n);
  }

  toLocal(iso: string | null | undefined): string {
    if (!iso || (typeof iso === 'string' && iso.startsWith('0000-'))) return '—';
    const s = String(iso).replace(' ', 'T');
    const d = new Date(s);
    return isNaN(d.getTime()) ? '—' : d.toLocaleString('fr-CH', { timeZone: 'Europe/Zurich' });
  }

  // ROLLUP
  rollViewsSum(): number { return this.n(this.data()?.rollup?.views_native_sum); }
  rollLikes(): number { return this.n(this.data()?.rollup?.likes_sum); }
  rollComments(): number { return this.n(this.data()?.rollup?.comments_sum); }
  rollShares(): number { return this.n(this.data()?.rollup?.shares_sum); }
  rollWatchSeconds(): number { return this.n(this.data()?.rollup?.total_watch_seconds_sum); }
  rollEngRate(): string { return this.fmtPct(this.data()?.rollup?.engagement_rate_sum); }

  // URL
  normalizeUrl(url: string | null | undefined, platform?: string | null): string | null {
    if (!url) return null;
    if (/^https?:\/\//i.test(url)) return url;
    const p = (platform || '').toUpperCase();
    if (p === 'FACEBOOK' || /^\/reel\//.test(url) || /^\/videos?\/.*/.test(url) || /^\/watch/.test(url)) {
      return 'https://www.facebook.com' + (url.startsWith('/') ? url : '/' + url);
    }
    return url;
  }

  // Watch eq
  rollWatchEqSeconds(): number {
    const srcs = this.data()?.sources ?? [];
    let sum = 0;
    for (const s of srcs) {
      const l: any = (s as any)?.latest;
      const we = l?.watch_equivalent;
      if (we != null && we !== '') {
        const n = Number(we);
        if (Number.isFinite(n)) sum += n;
      }
    }
    return sum > 0 ? sum : this.rollWatchSeconds();
  }

  // LATEST rows
  latestRows() {
    const srcs = this.data()?.sources ?? [];
    return srcs.map(s => ({ src: s, l: (s as any)?.latest })).filter(x => !!x.l);
  }

  // trackBy pour stabiliser le DOM des ngFor
  trackByPlatform = (_: number, r: any) => (r?.src?.platform || r?.src?.id || _);

  // Réactions
  reactionsRows() {
    return (this.latestRows() || []).filter((r: any) => {
      const l = r?.l || {};
      const vals = [l.reactions_like, l.reactions_love, l.reactions_wow, l.reactions_haha, l.reactions_sad, l.reactions_angry]
        .map((v: any) => Number(v || 0));
      return vals.some((v: number) => Number.isFinite(v) && v > 0);
    });
  }
  hasAnyReactions(): boolean { return this.reactionsRows().length > 0; }
  showIfPos(n: any): boolean { const v = Number(n || 0); return Number.isFinite(v) && v > 0; }

  engRateForRow(r: any): string {
    const provided = r?.l?.engagement_rate;
    if (provided != null && provided !== '') {
      const num = Number(provided);
      return Number.isFinite(num) ? (num * 100).toFixed(2) + ' %' : String(provided);
    }
    const likes = Number(r?.l?.likes || 0);
    const comments = Number(r?.l?.comments || 0);
    const shares = Number(r?.l?.shares || 0);
    const views = Number(r?.l?.views_native || 0);
    const denom = views > 0 ? views : 1;
    return ((likes + comments + shares) / denom * 100).toFixed(2) + ' %';
  }

  // ================= Graphiques =================

  private parseSeries(items: any): Array<[number, number]> {
    if (!Array.isArray(items)) return [];
    const parseTs = (x: any): number | null => {
      if (x == null) return null;
      if (typeof x === 'number') {
        const s = String(x); if (s.length === 10) return x * 1000; return x;
      }
      if (typeof x === 'string') {
        if (/^\d+$/.test(x)) return parseTs(Number(x));
        const d = new Date(x.includes('T') ? x : x.replace(' ', 'T'));
        return isNaN(d.getTime()) ? null : d.getTime();
      }
      return null;
    };
    const pickTimeKey = (o: any) => { for (const k of ['ts','t','time','timestamp','date']) if (k in o) return k; return null; };
    const pickValueKey = (o: any) => { for (const k of ['value','v','views','y','count','sum']) if (k in o) return k; return null; };
    const out: Array<[number, number]> = [];
    for (const it of items) {
      if (Array.isArray(it) && it.length >= 2) {
        const t = parseTs(it[0]); const v = Number(it[1] ?? 0);
        if (t != null && Number.isFinite(v)) out.push([t, v]);
      } else if (it && typeof it === 'object') {
        const tk = pickTimeKey(it); const vk = pickValueKey(it);
        const t = tk ? parseTs((it as any)[tk]) : null; const v = Number(vk ? (it as any)[vk] : 0);
        if (t != null && Number.isFinite(v)) out.push([t, v]);
      }
    }
    return out;
  }

  private observeAndResize(el: HTMLElement, inst: any, reinit?: () => void): void {
    const tryResize = () => { try { inst?.resize?.(); } catch {} };
    if (el.clientWidth > 0 && el.clientHeight > 0) tryResize();
    if ('ResizeObserver' in window) {
      const ro = new ResizeObserver(() => {
        if (el.clientWidth > 0 && el.clientHeight > 0) tryResize();
        // si l'instance n'est plus attachée au bon DOM (Angular a recréé le nœud), on réinit
        try {
          if (inst?.getDom && inst.getDom() !== el) { inst.dispose(); reinit?.(); }
        } catch {}
      });
      ro.observe(el);
      this.roList.push({ el, ro });
    }
    setTimeout(() => { tryResize(); reinit?.(); }, 150);
  }

  private initCharts(): void {
    try {
      const ec = (window as any).echarts;
      const elGlobal = document.getElementById('chart-global');
      if (ec && elGlobal) {
        this.chartGlobal = ec.init(elGlobal);
        this.refreshGlobalChart();
        this.observeAndResize(elGlobal, this.chartGlobal);
      }
      this.refreshPlatformCharts();
      this.initSparklines(); this.refreshSparklines();
      setTimeout(this.onResize, 0);
    } catch (e) {
      console.warn('ECharts init skipped', e);
    }
  }

  private refreshGlobalChart(): void {
    if (!this.chartGlobal || !this.ts) return;
    const series = this.parseSeries(this.ts?.views || this.ts?.timeseries?.views || []);
    this.chartGlobal.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'time' },
      yAxis: { type: 'value', min: 'dataMin', max: 'dataMax' },
      series: [{ type: 'line', showSymbol: false, data: series }]
    }, true);
    this.chartGlobal.resize();
  }

  private refreshPlatformCharts(): void {
    const ec = (window as any).echarts;
    if (!ec) return;

    (this.data()?.sources || []).forEach((s: any) => {
      const key = (s.platform || '').toUpperCase();
      const id = `chart-${key}`;
      let inst = this.chartsByPlatform[key];
      const el = document.getElementById(id) as HTMLElement | null;
      if (!el) return;

      const reinit = () => {
        try {
          inst?.dispose?.();
          inst = ec.init(el, undefined, { renderer: 'svg' });
          this.chartsByPlatform[key] = inst!;
          this.applyPlatformOption(key, inst!);
        } catch {}
      };

      // ⚠️ Si Angular a recréé le nœud, on rebind l’instance au nouveau DOM
      if (inst && inst.getDom && inst.getDom() !== el) {
        try { inst.dispose(); } catch {}
        inst = undefined as any;
      }

      if (!inst) {
        inst = ec.init(el, undefined, { renderer: 'svg' });
        this.chartsByPlatform[key] = inst;
        this.observeAndResize(el, inst, reinit);
      }

      this.applyPlatformOption(key, inst);
      inst.resize();
    });
  }

  private applyPlatformOption(key: string, inst: any): void {
    const plat = (this.ts && (this.ts[key] || this.ts?.by_platform?.[key])) || null;
    const raw = Array.isArray(plat?.views) ? plat.views
               : Array.isArray(plat) ? plat
               : (this.ts?.views || this.ts?.timeseries?.views || []);
    const series = this.parseSeries(raw);
    inst.setOption({
      animation: false,
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'time', axisLabel: { hideOverlap: true } },
      yAxis: { type: 'value', min: 'dataMin', max: 'dataMax' },
      series: [{ type: 'line', showSymbol: false, data: series }]
    }, true, false);
  }

  private initSparklines(): void {
    const ec = (window as any).echarts;
    if (!ec) return;
    (this.data()?.sources || []).forEach((s: any) => {
      const key = (s.platform || '').toUpperCase();
      const id = `spark-${key}`;
      let inst = this.sparkInstances[key];
      const el = document.getElementById(id) as HTMLElement | null;
      if (!el) return;

      const reinit = () => {
        try {
          inst?.dispose?.();
          inst = ec.init(el, undefined, { renderer: 'svg' });
          this.sparkInstances[key] = inst!;
          this.applySparkOption(key, inst!);
        } catch {}
      };

      if (inst && inst.getDom && inst.getDom() !== el) {
        try { inst.dispose(); } catch {}
        inst = undefined as any;
      }

      if (!inst) {
        inst = ec.init(el, undefined, { renderer: 'svg' });
        this.sparkInstances[key] = inst;
        this.observeAndResize(el, inst, reinit);
      }
    });
  }

  private refreshSparklines(): void {
    (this.data()?.sources || []).forEach((s: any) => {
      const key = (s.platform || '').toUpperCase();
      const inst = this.sparkInstances[key];
      const el = document.getElementById(`spark-${key}`) as HTMLElement | null;
      if (!inst || !el) return;
      this.applySparkOption(key, inst);
      inst.resize();
    });
  }

  private applySparkOption(key: string, inst: any): void {
    const plat = (this.ts && (this.ts[key] || this.ts?.by_platform?.[key])) || null;
    const raw = Array.isArray(plat?.views) ? plat.views
               : Array.isArray(plat) ? plat
               : (this.ts?.views || this.ts?.timeseries?.views || []);
    const series = this.parseSeries(raw);
    inst.setOption({
      animation: false,
      grid: { left: 0, right: 0, top: 0, bottom: 0 },
      xAxis: { type: 'time', show: false },
      yAxis: { type: 'value', show: false },
      series: [{ type: 'line', showSymbol: false, data: series }]
    }, true, false);
  }

  // ================= Timeseries fetch =================

  private fetchTimeseries(): void {
    const range = this.gran === 'hour' ? '7d' : '60d';
    console.log('[video-detail] fetchTimeseries gran=', this.gran, 'range=', range);
    this.api.getVideoTimeseries(this.videoId, { metric: 'views_native', interval: this.gran, range })
      .subscribe({
        next: (res) => {
          const r: any = res as any;
          const normalized = (r && typeof r === 'object' && 'timeseries' in r) ? r.timeseries : r || null;
          console.log('[video-detail] /timeseries normalized keys=', normalized ? Object.keys(normalized) : null);
          this.ts = normalized;
          if (this.chartGlobal) this.refreshGlobalChart(); else setTimeout(() => this.initCharts(), 0);
          this.refreshPlatformCharts();
          this.initSparklines(); this.refreshSparklines();
          setTimeout(this.onResize, 0);
        },
        error: (err) => console.error('[video-detail] getVideoTimeseries failed', err)
      });
  }

  // ================= Tableau répartition plateformes =================

  byPlatformRows() {
    const srcs = this.data()?.sources ?? [];
    const map: any = {};
    for (const s of srcs) {
      const p = (s.platform || '—').toUpperCase();
      if (!map[p]) map[p] = { views: 0, last: null as string | null };
      const latest: any = (s as any)?.latest;
      const v = this.n(latest?.views_native);
      map[p].views += v;
      const cand: string | null = latest?.snapshot_at ? String(latest.snapshot_at) : null;
      if (cand) {
        const candD = new Date(cand.replace(' ', 'T')).getTime();
        const lastD = map[p].last ? new Date(String(map[p].last).replace(' ', 'T')).getTime() : 0;
        if (candD > lastD) map[p].last = cand;
      }
    }
    return Object.keys(map).map(k => ({
      platform: k,
      views: map[k].views,
      last_snapshot_at: map[k].last
    }));
  }
}
