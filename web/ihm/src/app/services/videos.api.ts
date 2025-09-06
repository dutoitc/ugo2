import { Injectable } from '@angular/core';

export type Platform = 'YOUTUBE'|'FACEBOOK'|'INSTAGRAM'|'TIKTOK';
export type SortKey =
| 'views_desc' | 'published_desc' | 'published_asc'
| 'engagement_desc' | 'watch_eq_desc'
| 'title_asc' | 'title_desc';

export interface VideoListItem {
  id: number;
  slug: string | null;
  title: string;
  published_at: string;
  length_seconds: number | null;

  views_native_sum: number | null;
  likes_sum: number | null;
  comments_sum: number | null;
  shares_sum: number | null;

  total_watch_seconds_sum: number | null;
  avg_watch_ratio_est: number | null;
  watch_equivalent_sum: number | null;
  engagement_rate_sum: number | null;

  // ex:
  // "by_platform": { "YOUTUBE": 0, "FACEBOOK": 4541, "INSTAGRAM": 0, "TIKTOK": 0 }
  by_platform: Partial<Record<'YOUTUBE'|'FACEBOOK'|'INSTAGRAM'|'TIKTOK', number>> | Record<string, number> | null;
}


export interface VideoListResponse {
  total: number;
  page: number;
  size: number;
  items: VideoListItem[];
}


@Injectable({ providedIn: 'root' })
export class VideosApi {
private base = '/api/v1';

async list(params: {
    page?: number; size?: number; q?: string;
    platform?: Platform | undefined; sort?: SortKey;
    from?: string; to?: string;
  } = {}): Promise<VideoListResponse> {

    const usp = new URLSearchParams();
    const put = (k: string, v: unknown) => {
      if (v === undefined || v === null) return;
      const s = String(v).trim();
      if (s === '' || s === 'null' || s === 'undefined') return; // <-- clé !
      usp.set(k, s);
    };

    put('page', params.page ?? 1);
    put('size', params.size ?? 20);
    put('q', params.q);
    put('platform', params.platform);   // ne passera pas si vide/null
    put('sort', params.sort);
    put('from', params.from);
    put('to', params.to);

    const res = await fetch(`${this.base}/videos?${usp.toString()}`, {
      headers: { 'Accept': 'application/json' }
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return res.json();
  }
}
