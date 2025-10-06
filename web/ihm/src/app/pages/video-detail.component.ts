import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../services/api.service';
import { VideoDetailResponse } from '../models';
import { TimeSeriesChartComponent } from '../components/time-series-chart.component';

declare global {
interface Window { echarts: any; }
}

@Component({
standalone: true,
selector: 'app-video-detail',
imports: [CommonModule, RouterLink, TimeSeriesChartComponent],
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

// ECharts instances (sparklines uniquement)
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
    const id = Number(this.route.snapshot.paramMap.get('id') || 0);
    this.videoId = Number.isFinite(id) ? id : 0;

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
    const pad2 = (x: number) => (x < 10 ? '0' + x : '' + x);
    const dd = d > 0 ? `${d}j ` : '';
    return `${dd}${h}h${pad2(m)}'${pad2(sec)}''`;
  }

  fmtInt(v: any): string {
    const n = this.n(v);
    return n === 0 ? '0' : n.toLocaleString('fr-CH');
  }

  fmtPct(v: any): string {
    if (v == null || v === '') return '—';
    const n = Number(v);
    if (!Number.isFinite(n)) return String(v);
    return (n * 100).toFixed(2) + ' %';
  }

  trackByPlatform = (_: number, row: any) => (row?.src?.platform || row?.platform || '');

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

  // Latest has reactions?
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
    const num = views > 0 ? (likes + comments + shares) / views : 0;
    return (num * 100).toFixed(2) + ' %';
  }

  // ================= ECharts (laissé en place, inoffensif si pas de conteneur) =================

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
    const rows = this.latestRows();
    rows.forEach((r: any) => {
      const key = (r?.src?.platform || '').toUpperCase();
      const id = `chart-${key}`;
      let inst = this.chartsByPlatform[key];
      const el = document.getElementById(id) as HTMLElement | null;
      if (!el) return;

      const reinit = () => {
        try {
          inst?.dispose?.();
          inst = ec.init(el, undefined, { renderer: 'svg' });
          this.chartsByPlatform[key] = inst!;
          this.applySparkOption(key, inst!);
        } catch {}
      };

      if (inst && inst.getDom && inst.getDom() !== el) {
        try { inst.dispose(); } catch {}
        inst = undefined as any;
      }

      if (!inst) {
        inst = ec.init(el, undefined, { renderer: 'svg' });
        this.chartsByPlatform[key] = inst;
        this.observeAndResize(el, inst, reinit);
      }
      this.applySparkOption(key, inst);
    });
  }

  private parseSeries(input: any): Array<[number, number]> {
    const out: Array<[number, number]> = [];
    if (!input) return out;

    const pickTimeKey = (obj: any): string | null => {
      for (const k of ['t','time','ts','timestamp','date']) if (k in (obj || {})) return k;
      for (const k of Object.keys(obj || {})) if (/time|date/i.test(k)) return k;
      return null;
    };
    const pickValueKey = (obj: any): string | null => {
      for (const k of ['v','value','y','count','views']) if (k in (obj || {})) return k;
      for (const k of Object.keys(obj || {})) if (/value|count|views|y/i.test(k)) return k;
      return null;
    };
    const parseTs = (v: any): number | null => {
      if (v == null) return null;
      if (typeof v === 'number') return v > 100000000000 ? v : v * 1000;
      const s = String(v).replace(' ', 'T');
      const d = new Date(s);
      const t = d.getTime();
      return Number.isFinite(t) ? t : null;
    };

    const items: any[] = Array.isArray(input) ? input : [];
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

  private applySparkOption(key: string, inst: any): void {
    if (!inst) return;
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

  private refreshSparklines(): void {
    (this.data()?.sources || []).forEach((s: any) => {
      const key = (s.platform || '').toUpperCase();
      const inst = this.sparkInstances[key];
      if (inst) this.applySparkOption(key, inst);
    });
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

  // ================= Table “sources latest” =================

  latestTableRows(): Array<any> {
    const rows = this.latestRows();
    const map: Record<string, { views: number; last: string | null }> = {};
    for (const r of rows) {
      const p = String(r?.src?.platform || '').toUpperCase();
      const latest: any = r?.l || {};
      const v = Number(latest?.views_native || latest?.views || 0);
      if (!map[p]) map[p] = { views: 0, last: null };
      if (Number.isFinite(v)) map[p].views += v;
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

  // === Adapter pour TimeSeriesChartComponent ===
  globalViewsSeries(): Array<[number, number]> {
    return this.parseSeries(this.ts?.views || this.ts?.timeseries?.views || []);
  }

  platformViewsSeries(pf: string | null | undefined): Array<[number, number]> {
    const key = String(pf || '').toUpperCase();
    const plat: any = (this.ts && (this.ts[key] || this.ts?.by_platform?.[key])) || null;
    const raw: any = Array.isArray(plat?.views) ? plat.views
               : Array.isArray(plat) ? plat
               : (this.ts?.views || this.ts?.timeseries?.views || []);
    return this.parseSeries(raw);
  }
}
