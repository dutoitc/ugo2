import { Component, OnInit, inject, signal } from '@angular/core';
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
export class VideoDetailComponent implements OnInit {
private route = inject(ActivatedRoute);
private api = inject(ApiService);

readonly data = signal<VideoDetailResponse | null>(null);

private videoId = 0;
gran: 'hour' | 'day' = 'hour';             // 1h / 1d
private ts: any | null = null;             // payload de /video/{id}/timeseries

// ECharts instances
private chartGlobal: any = null;
private chartsByPlatform: Record<string, any> = {};
private sparkInstances: Record<string, any> = {};

ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) return;
    this.videoId = Number(idParam);

    // 1) Détails vidéo
    this.api.getVideoById(this.videoId).subscribe({
      next: (res) => {
        this.data.set(res);
        setTimeout(() => this.initCharts(), 0);
      },
      error: (err) => {
        console.error('getVideoById failed', err);
        this.data.set(null);
      }
    });

    // 2) Timeseries (par défaut: hour)
    this.fetchTimeseries();
  }

  // ================= Helpers formatting =================

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

  /** Eng. rate à afficher dans le tableau */
  engRateForRow(r: any): string {
    const provided = r?.l?.engagement_rate;
    if (provided != null && provided !== '') {
      const num = Number(provided);
      return Number.isFinite(num) ? (num * 100).toFixed(2) + ' %' : String(provided);
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

  // ================= Timeseries & Charts =================

  /** Récupère la timeseries depuis l'API en fonction de la granularité */
  private fetchTimeseries(): void {
    const range = this.gran === 'hour' ? '7d' : '60d'; // heuristique simple
    this.api.getVideoTimeseries(this.videoId, { metric: 'views_native', interval: this.gran, range })
      .subscribe({
        next: (res) => {
          this.ts = res || null;
          // (ré)initialiser / rafraîchir
          if (this.chartGlobal) this.refreshGlobalChart(); else setTimeout(() => this.initCharts(), 0);
          this.refreshPlatformCharts(); // crée/maj chaque graphe plateforme
          this.initSparklines(); this.refreshSparklines();
        },
        error: (err) => {
          console.error('getVideoTimeseries failed', err);
        }
      });
  }

  /** Change 1h/1d */
  setGran(g: 'hour' | 'day'): void {
    if (this.gran === g) return;
    this.gran = g;
    this.fetchTimeseries();
  }

  /** Initialise le graphe global + prépare les conteneurs plateformes */
  private initCharts(): void {
    const echarts = (window as any).echarts;
    if (!echarts) return;

    // Global
    const el = document.getElementById('chart');
    if (el && !this.chartGlobal) {
      try { this.chartGlobal = echarts.init(el); } catch (e) { console.error('echarts init (global)', e); }
    }
    this.refreshGlobalChart();

    // Plateformes (instances seront créées à la volée)
    this.refreshPlatformCharts();

    // Sparklines
    this.initSparklines(); this.refreshSparklines();
  }

  /** Série globale depuis ts */
  private globalPoints(): Array<{ t: string; v: number }> {
    const arr = this.ts?.timeseries?.views;
    if (!Array.isArray(arr)) return [];
    const out = arr.map((p: any) => ({
      t: String(p.ts ?? p.t ?? p.time ?? p.timestamp),
      v: this.n(p.value ?? p.v ?? p.views ?? 0),
    }));
    out.sort((a, b) => new Date(a.t).getTime() - new Date(b.t).getTime());
    return out;
  }

  /** Plateformes disponibles dans la ts (ou fallback latestRows) */
  platforms(): string[] {
    const t = this.ts?.timeseries;
    if (t) {
      return Object.keys(t).filter(k => k !== 'views');
    }
    const rows = this.latestRows();
    const set = new Set(rows.map((r: any) => String(r?.src?.platform || 'UNKNOWN').toUpperCase()));
    return Array.from(set);
  }

  /** Rafraîchit le graphe global */
  private refreshGlobalChart(): void {
    if (!this.chartGlobal) return;
    const pts = this.globalPoints();
    const cats = pts.map(p => p.t);
    const vals = pts.map(p => p.v);

    const xFmt = this.gran === 'hour'
      ? (c: any) => {
          try { return new Date(String(c)).toLocaleString('fr-CH', { timeZone: 'Europe/Zurich', hour: '2-digit', minute: '2-digit' }); }
          catch { return String(c); }
        }
      : (c: any) => {
          try { return new Date(String(c)).toLocaleDateString('fr-CH', { timeZone: 'Europe/Zurich' }); }
          catch { return String(c); }
        };

    const option = {
      tooltip: { trigger: 'axis' },
      grid: { left: 55, right: 10, top: 20, bottom: 30 },
      xAxis: { type: 'category', boundaryGap: false, data: cats.map(xFmt) },
      yAxis: {
        type: 'value',
        name: 'Vues',
        nameLocation: 'middle',
        nameGap: 45,
        axisLabel: { show: true }
      },
      series: [{ name: 'Vues', type: 'line', smooth: true, data: vals, areaStyle: {} }]
    };
    try { this.chartGlobal.setOption(option, true); } catch (e) { console.error('setOption global', e); }
  }

  /** Crée/MAJ un graphe par plateforme */
  private refreshPlatformCharts(): void {
    const echarts = (window as any).echarts;
    if (!echarts) return;

    const plats = this.platforms();
    for (const p of plats) {
      const el = document.getElementById('chart-platform-' + p);
      if (!el) continue;

      if (!this.chartsByPlatform[p]) {
        try { this.chartsByPlatform[p] = echarts.init(el); }
        catch (e) { console.error('echarts init (platform ' + p + ')', e); continue; }
      }

      const pts = this.platformPoints(p);
      const cats = pts.map(pt => pt.t);
      const vals = pts.map(pt => pt.v);

      const xFmt = this.gran === 'hour'
        ? (c: any) => {
            try { return new Date(String(c)).toLocaleString('fr-CH', { timeZone: 'Europe/Zurich', hour: '2-digit', minute: '2-digit' }); }
            catch { return String(c); }
          }
        : (c: any) => {
            try { return new Date(String(c)).toLocaleDateString('fr-CH', { timeZone: 'Europe/Zurich' }); }
            catch { return String(c); }
          };

      const opt = {
        tooltip: { trigger: 'axis' },
        grid: { left: 55, right: 10, top: 14, bottom: 28 },
        xAxis: { type: 'category', boundaryGap: false, data: cats.map(xFmt) },
        yAxis: {
          type: 'value',
          name: 'Vues',
          nameLocation: 'middle',
          nameGap: 45,
          axisLabel: { show: true }
        },
        series: [{ name: p, type: 'line', smooth: true, data: vals }]
      };
      try { this.chartsByPlatform[p].setOption(opt, true); } catch (e) { console.error('setOption platform ' + p, e); }
    }
  }

  /** Points d’une plateforme depuis ts */
  private platformPoints(platform: string): Array<{ t: string; v: number }> {
    const arr = this.ts?.timeseries?.[platform];
    if (Array.isArray(arr)) {
      const out = arr.map((p: any) => ({
        t: String(p.ts ?? p.t ?? p.time ?? p.timestamp),
        v: this.n(p.value ?? p.v ?? p.views ?? 0),
      }));
      out.sort((a, b) => new Date(a.t).getTime() - new Date(b.t).getTime());
      return out;
    }

    // Fallback minimal si pas de ts: regrouper latestRows par snapshot_at (peu utile mais évite un graphe vide)
    const out: Array<{ t: string; v: number }> = [];
    for (const r of this.latestRows().filter(rr => String(rr?.src?.platform || '').toUpperCase() === platform)) {
      const t = r.l?.snapshot_at || new Date().toISOString();
      const v = this.n(r?.l?.views_native);
      out.push({ t, v });
    }
    out.sort((a, b) => new Date(a.t).getTime() - new Date(b.t).getTime());
    return out;
  }

  // ================= Sparklines (inchangé) =================

  private initSparklines() {
    const echarts = (window as any).echarts;
    if (!echarts) return;
    for (const s of this.bySource()) {
      const sparkEl = document.getElementById('spark-' + s.platform);
      if (!sparkEl || this.sparkInstances[s.platform]) continue;
      try { this.sparkInstances[s.platform] = echarts.init(sparkEl); }
      catch (e) { console.error('sparkline init error', e); }
    }
  }

  private refreshSparklines() {
    const echarts = (window as any).echarts;
    if (!echarts) return;
    for (const s of this.bySource()) {
      const inst = this.sparkInstances[s.platform];
      if (!inst) continue;
      const series = this.buildSparkSeriesForPlatform(s.platform);
      const opt = {
        grid: { left: 0, right: 0, top: 4, bottom: 4 },
        xAxis: { type: 'category', show: false, data: series.map(p => p.t) },
        yAxis: { type: 'value', show: false },
        series: [{ type: 'line', smooth: true, symbol: 'none', data: series.map(p => p.v) }]
      };
      try { inst.setOption(opt, true); } catch (e) { console.error('sparkline setOption', e); }
    }
  }

  private buildSparkSeriesForPlatform(platform: string): Array<{ t: string; v: number }> {
    const plat = String(platform).toUpperCase();
    const arr = this.ts?.timeseries?.[plat];
    if (Array.isArray(arr) && arr.length) {
      const out = arr.map((p: any) => ({
        t: String(p.ts ?? p.t ?? p.time ?? p.timestamp),
        v: this.n(p.value ?? p.views ?? p.v ?? 0)
      }));
      out.sort((a, b) => new Date(a.t).getTime() - new Date(b.t).getTime());
      return out.length > 24 ? out.slice(-24) : out;
    }
    const resp: any = this.data();
    const out: Array<{ t: string; v: number }> = [];
    if (resp && (resp as any).timeseries && Array.isArray((resp as any).timeseries[plat])) {
      for (const p of (resp as any).timeseries[plat]) {
        const t = p.ts ?? p.t ?? p.time ?? p.timestamp;
        const v = p.value ?? p.views ?? p.v ?? 0;
        if (t != null) out.push({ t, v: this.n(v) });
      }
    } else {
      for (const r of this.latestRows().filter(rr => String(rr?.src?.platform || '').toUpperCase() === plat)) {
        const t = r.l?.snapshot_at || new Date().toISOString();
        const v = this.n(r?.l?.views_native);
        out.push({ t, v });
      }
    }
    out.sort((a, b) => new Date(String(a.t)).getTime() - new Date(String(b.t)).getTime());
    return out.length > 24 ? out.slice(-24) : out;
  }

  // ================= Agrégats pour cartes Source =================

  bySource(): Array<{ platform: string; views: number; last_snapshot_at?: string }> {
    const rows = this.latestRows();
    const map: Record<string, { views: number; last?: string }> = {};
    for (const r of rows) {
      const p = String(r?.src?.platform || 'UNKNOWN').toUpperCase();
      const v = this.n(r?.l?.views_native);
      if (!map[p]) map[p] = { views: 0, last: r?.l?.snapshot_at ?? undefined };
      map[p].views += v;
      const cand: string | undefined = r?.l?.snapshot_at ?? undefined;
      if (cand) {
        const candD = new Date(String(cand)).getTime();
        const lastD = map[p].last ? new Date(String(map[p].last)).getTime() : 0;
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
