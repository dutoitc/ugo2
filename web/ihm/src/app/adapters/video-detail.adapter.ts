// Service pur, testable, sans dépendances Angular.

import { TimeseriesPoint, LatestRowVm, ReactionsRowVm } from "../models/video-detail.vm";

export class VideoDetailAdapter {
/**
* Normalise des timeseries hétérogènes en [[epoch_ms, value], ...], triées (asc) et dédupliquées.
* Accepte:
*  - Array<[t,v]> ou Array<{t|time|timestamp|date|x|ts, v|value|views|views_native|val|y|sum|count}>
*  - { [time]: number|{value...} }
*  - wrappers { views|series|data|points|values: Array<...> }
*/
static parseSeries(input: unknown): TimeseriesPoint[] {
    if (!input) return [];

    const out: TimeseriesPoint[] = [];

    const coerceTime = (t: unknown): number | null => {
      if (t == null) return null;
      if (typeof t === "number") return t < 1e12 ? Math.round(t * 1000) : Math.round(t);
      if (t instanceof Date) return t.getTime();
      if (typeof t === "string") {
        const asNum = Number(t);
        if (!Number.isNaN(asNum)) return asNum < 1e12 ? Math.round(asNum * 1000) : Math.round(asNum);
        const d = new Date(t);
        const ts = d.getTime();
        return Number.isNaN(ts) ? null : ts;
      }
      return null;
    };

    const coerceVal = (v: unknown): number | null => {
      if (v == null) return null;
      if (typeof v === "number") return Number.isFinite(v) ? v : null;
      if (typeof v === "string") {
        const n = Number(v);
        return Number.isFinite(n) ? n : null;
      }
      return null;
    };

    const pushIfValid = (t: unknown, v: unknown) => {
      const tt = coerceTime(t);
      const vv = coerceVal(v);
      if (tt != null && vv != null) out.push([tt, vv]);
    };

    const pickValueFromObj = (o: Record<string, unknown>): number | null => {
      const keys = [
        "views_native","views","value","v","val","y","sum","count"
      ] as const;
      for (const k of keys) {
        const raw = o[k as keyof typeof o];
        const n = typeof raw === "number" ? raw : typeof raw === "string" ? Number(raw) : NaN;
        if (Number.isFinite(n)) return n;
      }
      // fallback: premier champ numérisable
      for (const k of Object.keys(o)) {
        const raw = (o as any)[k];
        const n = typeof raw === "number" ? raw : typeof raw === "string" ? Number(raw) : NaN;
        if (Number.isFinite(n)) return n;
      }
      return null;
    };

    const pickTimeFromObj = (o: Record<string, unknown>): number | null => {
      const keys = ["time","timestamp","date","t","x","ts"] as const;
      for (const k of keys) {
        if (k in o) {
          const t = coerceTime(o[k]);
          if (t != null) return t;
        }
      }
      return null;
    };

    const unwrapIfWrapperArray = (obj: any): unknown[] | null => {
      if (!obj || typeof obj !== "object") return null;
      const candidates = ["series","data","points","values","views"];
      for (const k of candidates) {
        if (Array.isArray((obj as any)[k])) return (obj as any)[k];
      }
      return null;
    };

    // 1) Si c'est un tableau, ou si c'est un wrapper qui contient un tableau
    const arrayInput: unknown[] | null =
      Array.isArray(input) ? input :
      unwrapIfWrapperArray(input as any);

    if (arrayInput) {
      for (const item of arrayInput) {
        if (Array.isArray(item) && item.length >= 2) {
          pushIfValid(item[0], item[1]);
          continue;
        }
        if (item && typeof item === "object") {
          const o = item as Record<string, unknown>;
          const t = pickTimeFromObj(o);
          const v = pickValueFromObj(o);
          if (t != null && v != null) out.push([t, v]);
        }
      }
      out.sort((a, b) => a[0] - b[0]);
      // dédup
      const dedup: TimeseriesPoint[] = [];
      let prevT = Number.NaN;
      for (const p of out) {
        if (p[0] !== prevT) {
          dedup.push(p);
          prevT = p[0];
        } else {
          dedup[dedup.length - 1] = p; // garde la dernière valeur pour le même ts
        }
      }
      return dedup;
    }

    // 2) Dictionnaire { ts => v } ou { ts => {value:...} }
    if (input && typeof input === "object") {
      for (const [k, v] of Object.entries(input as Record<string, unknown>)) {
        const t = coerceTime(k);
        const vv =
          v && typeof v === "object" ? pickValueFromObj(v as Record<string, unknown>) :
          typeof v === "number" ? v :
          typeof v === "string" ? Number(v) : null;
        if (t != null && vv != null && Number.isFinite(vv)) out.push([t, vv]);
      }
      out.sort((a, b) => a[0] - b[0]);
      return out;
    }

    return out;
  }

  static toSeriesGlobal(ts: unknown): TimeseriesPoint[] {
    return this.parseSeries(ts);
  }

  /**
   * Série filtrée par plateforme.
   * - ts[pf] est soit Array, soit { views|series|data:[...] }
   * - supporte également ts.by_platform?.[pf]
   * - sinon, on tente un parse direct de ts (fallback)
   */
  static toSeriesByPlatform(ts: unknown, pf: string): TimeseriesPoint[] {
    if (!ts || typeof ts !== "object" || Array.isArray(ts)) return this.parseSeries(ts);
    const obj = ts as Record<string, unknown>;

    const candidate =
      (obj[pf] as any) ??
      (obj['by_platform'] && (obj as any)['by_platform']?.[pf]);

    if (candidate != null) {
      if (Array.isArray(candidate)) return this.parseSeries(candidate);
      if (typeof candidate === "object") {
        const arr =
          Array.isArray((candidate as any)['views']) ? (candidate as any)['views'] :
          Array.isArray((candidate as any)['series']) ? (candidate as any)['series'] :
          Array.isArray((candidate as any)['data']) ? (candidate as any)['data'] :
          Array.isArray((candidate as any)['points']) ? (candidate as any)['points'] :
          Array.isArray((candidate as any)['values']) ? (candidate as any)['values'] :
          null;
        if (arr) return this.parseSeries(arr);
        return this.parseSeries(candidate);
      }
    }

    return this.parseSeries(ts);
  }

  // ---------- Tables (non utilisées par ton template actuel, mais ok si besoin) ----------

  static toLatestRows(sources: unknown[]): LatestRowVm[] {
    if (!Array.isArray(sources)) return [];
    const pickNum = (...c: unknown[]): number => {
      for (const x of c) {
        const n = typeof x === "number" ? x : typeof x === "string" ? Number(x) : NaN;
        if (Number.isFinite(n)) return n;
      }
      return 0;
    };
    const pickStr = (...c: unknown[]): string => {
      for (const x of c) {
        if (typeof x === "string" && x.trim()) return x;
        if (x instanceof Date) return x.toISOString();
      }
      return "";
    };

    return sources.map((s: any) => {
      const platform = pickStr(s?.platform, s?.pf, s?.source, s?.name) || "unknown";
      const views = pickNum(
        s?.views, s?.views_total, s?.totals?.views, s?.stats?.views, s?.metrics?.views,
        s?.latest?.views_native, s?.latest?.views
      );
      const lastSnapshot = pickStr(
        s?.last_snapshot_at, s?.lastSnapshotAt, s?.updated_at, s?.last_update, s?.snapshot_at, s?.latest?.snapshot_at
      );
      return { platform, views, last_snapshot_at: lastSnapshot };
    });
  }

  static toReactionsRows(sources: unknown[]): ReactionsRowVm[] {
    if (!Array.isArray(sources)) return [];
    const pickNum = (...c: unknown[]): number => {
      for (const x of c) {
        const n = typeof x === "number" ? x : typeof x === "string" ? Number(x) : NaN;
        if (Number.isFinite(n)) return n;
      }
      return 0;
    };
    const pickStr = (...c: unknown[]): string => {
      for (const x of c) if (typeof x === "string" && x.trim()) return x;
      return "unknown";
    };

    return sources.map((s: any) => {
      const platform = pickStr(s?.platform, s?.pf, s?.source, s?.name);
      const likes = pickNum(s?.likes, s?.reactions?.likes, s?.totals?.likes, s?.metrics?.likes, s?.latest?.likes);
      const comments = pickNum(s?.comments, s?.reactions?.comments, s?.totals?.comments, s?.metrics?.comments, s?.latest?.comments);
      const shares = pickNum(s?.shares, s?.reactions?.shares, s?.totals?.shares, s?.metrics?.shares, s?.latest?.shares);
      return { platform, likes, comments, shares };
    });
  }
}

export default VideoDetailAdapter;
