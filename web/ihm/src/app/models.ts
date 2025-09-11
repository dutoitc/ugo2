export interface VideoListItem {
  id: number;
  slug?: string | null;
  title: string | null;
  description?: string | null;
  published_at: string | null;       // ISO-like "YYYY-MM-DD HH:mm:ss.SSS"
  duration_seconds?: number | null;
  is_locked: boolean;

  by_platform?: Record<string, number | string | null> | null; // { YOUTUBE: 123, FACEBOOK: 456, ... }
  last_snapshot_at?: string | null;  // "YYYY-MM-DD HH:mm:ss.SSS"

  // agrégats éventuels (souvent renvoyés en string)
  views_native_sum?: number | string | null;
  likes_sum?: number | string | null;
  comments_sum?: number | string | null;
  shares_sum?: number | string | null;
  engagement_rate_sum?: number | string | null;
  total_watch_seconds_sum?: number | string | null;
  avg_watch_ratio_est?: number | string | null;
  watch_equivalent_sum?: number | string | null;
}

export interface VideoListResponse {
  page: number;
  size: number;
  total: number;
  items: Array<VideoListItem>;
}

/** ========= Détail =========
 * S’aligne sur ton JSON réel (video, rollup, sources[].latest)
 */
export interface VideoDetailResponse {
  video: {
    id: number;
    slug?: string | null;
    title: string | null;
    description?: string | null;
    published_at: string | null;
    duration_seconds?: number | null;
    is_locked: boolean;
  };
  rollup?: {
    video_id: number;
    slug?: string | null;
    video_title?: string | null;
    video_published_at?: string | null;
    canonical_length_seconds?: number | null;

    views_native_sum?: number | string | null;
    likes_sum?: number | string | null;
    comments_sum?: number | string | null;
    shares_sum?: number | string | null;
    total_watch_seconds_sum?: number | string | null;

    views_yt?: number | string | null;
    views_fb?: number | string | null;
    views_ig?: number | string | null;
    views_tt?: number | string | null;

    avg_watch_ratio_est?: number | string | null;
    watch_equivalent_sum?: number | string | null;
    engagement_rate_sum?: number | string | null;
  } | null;

  sources: Array<{
    id: number;
    platform: string;                 // YOUTUBE | FACEBOOK | …
    platform_format?: string | null;  // VIDEO | REEL | …
    platform_video_id?: string | null;
    title?: string | null;
    url?: string | null;
    published_at?: string | null;
    duration_seconds?: number | null;
    is_active?: boolean;

    latest?: {
      id: number;
      source_video_id: number;
      video_id: number;
      platform: string;
      platform_format?: string | null;
      platform_video_id?: string | null;
      url?: string | null;

      source_title?: string | null;
      source_published_at?: string | null;
      video_title?: string | null;
      video_published_at?: string | null;

      snapshot_at?: string | null;
      views_native?: number | string | null;
      avg_watch_seconds?: number | string | null;
      total_watch_seconds?: number | string | null;
      length_seconds?: number | string | null;

      reach?: number | string | null;
      unique_viewers?: number | string | null;

      likes?: number | string | null;
      comments?: number | string | null;
      shares?: number | string | null;

      reactions_total?: number | string | null;
      reactions_like?: number | string | null;
      reactions_love?: number | string | null;
      reactions_wow?: number | string | null;
      reactions_haha?: number | string | null;
      reactions_sad?: number | string | null;
      reactions_angry?: number | string | null;

      avg_watch_ratio?: number | string | null;
      watch_equivalent?: number | string | null;
      engagement_rate?: number | string | null;
    } | null;

    timeseries?: unknown | null;
  }>;
}
